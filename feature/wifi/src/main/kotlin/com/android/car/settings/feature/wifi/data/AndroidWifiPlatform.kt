@file:Suppress("DEPRECATION", "LargeClass", "TooManyFunctions")
@file:SuppressLint("MissingPermission", "InlinedApi", "NewApi")

package com.android.car.settings.feature.wifi.data

import android.annotation.SuppressLint
import android.app.usage.NetworkStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NetworkPolicyManager
import android.net.TetheringManager
import android.net.wifi.ScanResult
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiEnterpriseConfig
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.core.common.rethrowIfCancellation
import com.android.car.settings.feature.wifi.domain.HotspotBand
import com.android.car.settings.feature.wifi.domain.HotspotConfiguration
import com.android.car.settings.feature.wifi.domain.HotspotSecurity
import com.android.car.settings.feature.wifi.domain.HotspotStatus
import com.android.car.settings.feature.wifi.domain.MeteredOverride
import com.android.car.settings.feature.wifi.domain.MobileNetworkState
import com.android.car.settings.feature.wifi.domain.MobileSubscription
import com.android.car.settings.feature.wifi.domain.WifiConnectionDetails
import com.android.car.settings.feature.wifi.domain.WifiCredentials
import com.android.car.settings.feature.wifi.domain.WifiEapMethod
import com.android.car.settings.feature.wifi.domain.WifiNetwork
import com.android.car.settings.feature.wifi.domain.WifiPreferences
import com.android.car.settings.feature.wifi.domain.WifiRadioState
import com.android.car.settings.feature.wifi.domain.WifiSecurity
import com.android.car.settings.feature.wifi.domain.WifiState
import com.android.car.settings.feature.wifi.domain.wifiQrPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.net.Inet4Address
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AndroidWifiPlatform
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : WifiPlatform {
        private val wifiManager = context.getSystemService(WifiManager::class.java)
        private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        private val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
        private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        private val userManager = context.getSystemService(UserManager::class.java)
        private val networkStatsManager = context.getSystemService(NetworkStatsManager::class.java)
        private val networkPolicyManager = runCatching { NetworkPolicyManager.from(context) }.getOrNull()
        private val tetheringManager =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.getSystemService(TetheringManager::class.java)
            } else {
                null
            }
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val executor = Executor { command -> scope.launch { command.run() } }
        private val mutableState = MutableStateFlow(WifiState())

        override val state: StateFlow<WifiState> = mutableState.asStateFlow()

        private val wifiReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    scope.launch {
                        if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                            mutableState.value = mutableState.value.copy(isScanning = false)
                        }
                        refresh()
                    }
                }
            }

        private val softApCallback =
            object : WifiHiddenApiBridge.HotspotListener {
                override fun onStateChanged(
                    state: Int,
                    failureReason: Int,
                ) {
                    val current = mutableState.value.hotspot
                    mutableState.value =
                        mutableState.value.copy(
                            hotspot =
                                current.copy(
                                    isEnabled = state == WIFI_AP_STATE_ENABLED,
                                    isTransitioning =
                                        state == WIFI_AP_STATE_ENABLING ||
                                            state == WIFI_AP_STATE_DISABLING,
                                    failureReason =
                                        if (state == WIFI_AP_STATE_FAILED) {
                                            "Soft AP failure code $failureReason"
                                        } else {
                                            null
                                        },
                                ),
                        )
                }

                override fun onClientsChanged(count: Int) {
                    val current = mutableState.value.hotspot
                    mutableState.value =
                        mutableState.value.copy(
                            hotspot = current.copy(clients = count),
                        )
                }
            }

        init {
            val filter =
                IntentFilter().apply {
                    addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                    addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                    addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                    addAction(WifiManager.RSSI_CHANGED_ACTION)
                    addAction(CONFIGURED_NETWORKS_CHANGED_ACTION)
                    addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(wifiReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(wifiReceiver, filter)
            }
            wifiManager?.let {
                runCatching {
                    WifiHiddenApiBridge.registerHotspotCallback(it, executor, softApCallback)
                }
            }
            scope.launch { refresh() }
        }

        override suspend fun refresh(): ActionResult =
            execute {
                val manager = requireNotNull(wifiManager) { "Wi-Fi is not supported" }
                val configurations = manager.configuredNetworks.orEmpty()
                val scans = manager.scanResults.orEmpty()
                val info = manager.connectionInfo
                val connectedNetworkId =
                    info?.networkId?.takeIf { it != INVALID_NETWORK_ID }

                val scannedNetworks =
                    scans
                        .groupBy { scanKey(it.SSID, securityFromCapabilities(it.capabilities)) }
                        .mapNotNull { (_, candidates) -> candidates.maxByOrNull(ScanResult::level) }
                        .map { scan ->
                            val security = securityFromCapabilities(scan.capabilities)
                            val configured =
                                configurations.firstOrNull {
                                    unquote(it.SSID) == scan.SSID &&
                                        securityCompatible(securityFromConfiguration(it), security)
                                }
                            scan.toDomain(
                                networkId = configured?.networkId,
                                isSaved = configured != null,
                                isConnected = configured?.networkId == connectedNetworkId,
                            )
                        }

                val savedNetworks =
                    configurations.map { configuration ->
                        val scan =
                            scans
                                .filter { it.SSID == unquote(configuration.SSID) }
                                .maxByOrNull(ScanResult::level)
                        configuration.toDomain(
                            scan = scan,
                            isConnected = configuration.networkId == connectedNetworkId,
                        )
                    }

                val connected =
                    connectedNetworkId?.let { id ->
                        (scannedNetworks + savedNetworks).firstOrNull { it.networkId == id }
                            ?: info?.toDomain(configurations.firstOrNull { it.networkId == id })
                    }

                val details =
                    connected?.let {
                        buildConnectionDetails(
                            network = it,
                            info = info,
                            linkProperties =
                                connectivityManager?.activeNetwork?.let(
                                    connectivityManager::getLinkProperties,
                                ),
                            configuration =
                                configurations.firstOrNull { config ->
                                    config.networkId == it.networkId
                                },
                        )
                    }

                mutableState.value =
                    mutableState.value.copy(
                        radioState = manager.wifiState.toDomain(),
                        connectedNetwork = connected,
                        availableNetworks =
                            scannedNetworks
                                .filterNot(WifiNetwork::isConnected)
                                .sortedWith(
                                    compareByDescending<WifiNetwork>(WifiNetwork::isSaved)
                                        .thenByDescending(WifiNetwork::signalLevel)
                                        .thenBy(WifiNetwork::ssid),
                                ),
                        savedNetworks =
                            savedNetworks
                                .filterNot(WifiNetwork::isConnected)
                                .sortedBy(WifiNetwork::ssid),
                        connectionDetails = details,
                        hotspot = readHotspotStatus(),
                        preferences = readPreferences(),
                        mobileNetwork = readMobileNetworkState(),
                        lastError = null,
                    )
            }

        override suspend fun setWifiEnabled(enabled: Boolean): ActionResult =
            execute {
                val accepted =
                    requireNotNull(wifiManager) { "Wi-Fi is not supported" }
                        .setWifiEnabled(enabled)
                check(accepted) { "The platform rejected the Wi-Fi state change" }
                mutableState.value =
                    mutableState.value.copy(
                        radioState =
                            if (enabled) {
                                WifiRadioState.ENABLING
                            } else {
                                WifiRadioState.DISABLING
                            },
                    )
            }

        override suspend fun startScan(): ActionResult =
            execute {
                val accepted =
                    requireNotNull(wifiManager) { "Wi-Fi is not supported" }
                        .startScan()
                check(accepted) { "The platform rejected the Wi-Fi scan" }
                mutableState.value = mutableState.value.copy(isScanning = true)
            }

        override suspend fun connect(
            network: WifiNetwork,
            credentials: WifiCredentials,
        ): ActionResult =
            execute {
                val manager = requireNotNull(wifiManager) { "Wi-Fi is not supported" }
                val networkId =
                    network.networkId ?: addOrUpdateConfiguration(
                        ssid = network.ssid,
                        security = network.security,
                        credentials = credentials,
                        hidden = network.isHidden,
                    )
                check(manager.enableNetwork(networkId, true)) {
                    "Unable to enable ${network.ssid}"
                }
                manager.reconnect()
            }

        override suspend fun addNetwork(
            ssid: String,
            security: WifiSecurity,
            credentials: WifiCredentials,
            hidden: Boolean,
        ): ActionResult =
            execute {
                val networkId = addOrUpdateConfiguration(ssid, security, credentials, hidden)
                val manager = requireNotNull(wifiManager)
                check(manager.enableNetwork(networkId, true)) { "Unable to enable $ssid" }
                manager.reconnect()
            }

        override suspend fun disconnect(): ActionResult =
            execute {
                check(requireNotNull(wifiManager).disconnect()) {
                    "The platform rejected the disconnect request"
                }
            }

        override suspend fun forget(networkId: Int): ActionResult =
            execute {
                val manager = requireNotNull(wifiManager)
                check(manager.removeNetwork(networkId)) { "Unable to forget network $networkId" }
                manager.saveConfiguration()
                refresh()
            }

        override suspend fun setAutoJoin(
            networkId: Int,
            enabled: Boolean,
        ): ActionResult =
            execute {
                val manager = requireNotNull(wifiManager)
                val method =
                    WifiManager::class.java.getMethod(
                        "allowAutojoin",
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                    )
                method.invoke(manager, networkId, enabled)
                refresh()
            }

        override suspend fun setMeteredOverride(
            networkId: Int,
            override: MeteredOverride,
        ): ActionResult =
            execute {
                val manager = requireNotNull(wifiManager)
                val platformOverride =
                    when (override) {
                        MeteredOverride.AUTOMATIC -> METERED_OVERRIDE_NONE
                        MeteredOverride.METERED -> METERED_OVERRIDE_METERED
                        MeteredOverride.NOT_METERED -> METERED_OVERRIDE_NOT_METERED
                    }
                check(
                    WifiHiddenApiBridge.setMeteredOverride(
                        manager,
                        networkId,
                        platformOverride,
                    ),
                ) {
                    "Unable to update metered setting"
                }
                manager.saveConfiguration()
                refresh()
            }

        override suspend fun setHotspotEnabled(enabled: Boolean): ActionResult =
            if (enabled) {
                startTethering()
            } else {
                execute {
                    WifiHiddenApiBridge.stopTethering(
                        requireNotNull(tetheringManager) { "Tethering is not supported" },
                    )
                }
            }

        override suspend fun updateHotspotConfiguration(configuration: HotspotConfiguration): ActionResult =
            execute {
                val securityType =
                    when (configuration.security) {
                        HotspotSecurity.OPEN -> SoftApConfiguration.SECURITY_TYPE_OPEN
                        HotspotSecurity.WPA2_PERSONAL ->
                            SoftApConfiguration.SECURITY_TYPE_WPA2_PSK

                        HotspotSecurity.WPA3_PERSONAL ->
                            SoftApConfiguration.SECURITY_TYPE_WPA3_SAE

                        HotspotSecurity.WPA2_WPA3_PERSONAL ->
                            SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION
                    }
                val band =
                    when (configuration.band) {
                        HotspotBand.AUTO,
                        HotspotBand.DUAL,
                        -> 0

                        HotspotBand.GHZ_2_4 -> SoftApConfiguration.BAND_2GHZ
                        HotspotBand.GHZ_5 -> SoftApConfiguration.BAND_5GHZ
                    }
                check(
                    WifiHiddenApiBridge.setHotspotConfiguration(
                        requireNotNull(wifiManager),
                        configuration.ssid,
                        configuration.password,
                        securityType,
                        band,
                        configuration.band == HotspotBand.DUAL,
                        configuration.autoShutdownEnabled,
                        configuration.maxClients,
                    ),
                ) {
                    "The platform rejected the hotspot configuration"
                }
                refresh()
            }

        override suspend fun setScanAlwaysAvailable(enabled: Boolean): ActionResult =
            execute {
                val manager = requireNotNull(wifiManager)
                val method =
                    WifiManager::class.java.getMethod(
                        "setScanAlwaysAvailable",
                        Boolean::class.javaPrimitiveType,
                    )
                method.invoke(manager, enabled)
                refresh()
            }

        override suspend fun setWakeupEnabled(enabled: Boolean): ActionResult = setGlobalSetting(WIFI_WAKEUP_ENABLED, enabled)

        override suspend fun setOpenNetworkNotificationEnabled(enabled: Boolean): ActionResult =
            setGlobalSetting(Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON, enabled)

        override suspend fun setCellularFallbackEnabled(enabled: Boolean): ActionResult = setGlobalSetting(WIFI_CELLULAR_FALLBACK, enabled)

        override suspend fun setPersistentTetheringEnabled(enabled: Boolean): ActionResult =
            execute {
                check(readPreferences().persistentTetheringSupported) {
                    "Persistent hotspot tethering is unavailable"
                }
                Settings.Global.putString(
                    context.contentResolver,
                    ENABLE_PERSISTENT_TETHERING,
                    if (enabled) "true" else "false",
                )
                refresh()
            }

        override suspend fun setMobileDataEnabled(enabled: Boolean): ActionResult =
            execute {
                check(readMobileNetworkState().mobileDataChangeAllowed) {
                    "Mobile data is restricted by the current user or administrator"
                }
                val subscriptionId = readMobileNetworkState().defaultSubscriptionId
                val manager = requireNotNull(telephonyManager) { "Mobile network is not supported" }
                val target = subscriptionId?.let(manager::createForSubscriptionId) ?: manager
                val method = target.javaClass.getMethod("setDataEnabled", Boolean::class.javaPrimitiveType)
                method.invoke(target, enabled)
                mutableState.value = mutableState.value.copy(mobileNetwork = readMobileNetworkState())
            }

        override suspend fun setDataRoamingEnabled(enabled: Boolean): ActionResult =
            execute {
                check(readMobileNetworkState().roamingChangeAllowed) {
                    "Data roaming is restricted by the current user or administrator"
                }
                val subscriptionId = readMobileNetworkState().defaultSubscriptionId
                val manager = requireNotNull(telephonyManager) { "Mobile network is not supported" }
                val target = subscriptionId?.let(manager::createForSubscriptionId) ?: manager
                val method = target.javaClass.getMethod("setDataRoamingEnabled", Boolean::class.javaPrimitiveType)
                method.invoke(target, enabled)
                mutableState.value = mutableState.value.copy(mobileNetwork = readMobileNetworkState())
            }

        override suspend fun setDefaultDataSubscription(subscriptionId: Int): ActionResult =
            execute {
                check(readMobileNetworkState().defaultDataChangeAllowed) {
                    "Changing the default data SIM is restricted by the current user or administrator"
                }
                val manager = requireNotNull(subscriptionManager) { "Subscription management is not supported" }
                val method =
                    manager.javaClass.methods.firstOrNull {
                        it.name == "setDefaultDataSubId" && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
                    } ?: error("Changing the default data SIM is unavailable")
                method.invoke(manager, subscriptionId)
                mutableState.value = mutableState.value.copy(mobileNetwork = readMobileNetworkState())
            }

        private suspend fun setGlobalSetting(
            key: String,
            enabled: Boolean,
        ): ActionResult =
            execute {
                check(
                    Settings.Global.putInt(
                        context.contentResolver,
                        key,
                        if (enabled) 1 else 0,
                    ),
                ) { "Unable to update $key" }
                refresh()
            }

        private suspend fun startTethering(): ActionResult =
            try {
                val manager = requireNotNull(tetheringManager) { "Tethering is not supported" }
                val result = CompletableDeferred<ActionResult>()
                WifiHiddenApiBridge.startTethering(
                    manager,
                    executor,
                    object : WifiHiddenApiBridge.TetheringCallback {
                        override fun onStarted() {
                            result.complete(ActionResult.Success)
                        }

                        override fun onFailed(error: Int) {
                            result.complete(
                                ActionResult.Failure("Unable to start hotspot: $error"),
                            )
                        }
                    },
                )
                withTimeout(TETHERING_CALLBACK_TIMEOUT_MS) { result.await() }
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()
                fail(throwable)
            }

        private fun addOrUpdateConfiguration(
            ssid: String,
            security: WifiSecurity,
            credentials: WifiCredentials,
            hidden: Boolean,
        ): Int {
            val manager = requireNotNull(wifiManager)
            val existing =
                manager.configuredNetworks.orEmpty().firstOrNull {
                    unquote(it.SSID) == ssid &&
                        securityCompatible(securityFromConfiguration(it), security)
                }
            val configuration =
                (existing ?: WifiConfiguration()).apply {
                    SSID = quote(ssid)
                    hiddenSSID = hidden
                    configureSecurity(this, security, credentials)
                }
            val networkId =
                if (existing == null) {
                    manager.addNetwork(configuration)
                } else {
                    manager.updateNetwork(configuration)
                }
            check(networkId >= 0) { "Unable to save $ssid" }
            manager.saveConfiguration()
            return networkId
        }

        private fun configureSecurity(
            configuration: WifiConfiguration,
            security: WifiSecurity,
            credentials: WifiCredentials,
        ) {
            configuration.allowedKeyManagement.clear()
            configuration.allowedAuthAlgorithms.clear()
            configuration.allowedProtocols.clear()
            configuration.allowedPairwiseCiphers.clear()
            configuration.allowedGroupCiphers.clear()

            when (security) {
                WifiSecurity.OPEN ->
                    configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)

                WifiSecurity.OWE ->
                    configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.OWE)

                WifiSecurity.WEP -> {
                    configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    configuration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                    configuration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                    configuration.wepKeys[0] =
                        credentials.password.takeIf(::isHexWepKey) ?: quote(credentials.password)
                    configuration.wepTxKeyIndex = 0
                }

                WifiSecurity.WPA2_PERSONAL,
                WifiSecurity.WPA2_WPA3_PERSONAL,
                -> {
                    configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    configuration.preSharedKey =
                        credentials.password.takeIf(::isPskHex) ?: quote(credentials.password)
                }

                WifiSecurity.WPA3_PERSONAL -> {
                    configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.SAE)
                    configuration.preSharedKey = quote(credentials.password)
                }

                WifiSecurity.EAP -> {
                    configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP)
                    configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X)
                    configuration.enterpriseConfig =
                        WifiEnterpriseConfig().apply {
                            identity = credentials.identity
                            anonymousIdentity = credentials.anonymousIdentity
                            password = credentials.password
                            eapMethod =
                                when (credentials.eapMethod) {
                                    WifiEapMethod.PEAP -> WifiEnterpriseConfig.Eap.PEAP
                                    WifiEapMethod.TLS -> WifiEnterpriseConfig.Eap.TLS
                                    WifiEapMethod.TTLS -> WifiEnterpriseConfig.Eap.TTLS
                                    WifiEapMethod.PWD -> WifiEnterpriseConfig.Eap.PWD
                                }
                            if (credentials.eapMethod == WifiEapMethod.PEAP ||
                                credentials.eapMethod == WifiEapMethod.TTLS
                            ) {
                                phase2Method = WifiEnterpriseConfig.Phase2.MSCHAPV2
                            }
                        }
                }

                WifiSecurity.UNKNOWN -> error("Unsupported Wi-Fi security")
            }
        }

        private fun readPreferences(): WifiPreferences =
            WifiPreferences(
                scanAlwaysAvailable = wifiManager?.isScanAlwaysAvailable == true,
                wakeupEnabled =
                    Settings.Global.getInt(
                        context.contentResolver,
                        WIFI_WAKEUP_ENABLED,
                        0,
                    ) == 1,
                openNetworkNotificationEnabled =
                    Settings.Global.getInt(
                        context.contentResolver,
                        Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                        0,
                    ) == 1,
                cellularFallbackEnabled =
                    Settings.Global.getInt(
                        context.contentResolver,
                        WIFI_CELLULAR_FALLBACK,
                        0,
                    ) == 1,
                persistentTetheringSupported = canControlPersistentTethering(),
                persistentTetheringEnabled =
                    Settings.Global
                        .getString(
                            context.contentResolver,
                            ENABLE_PERSISTENT_TETHERING,
                        ).equals("true", ignoreCase = true),
            )

        private fun canControlPersistentTethering(): Boolean =
            runCatching {
                val manager = context.getSystemService("car_wifi") ?: return@runCatching false
                manager.javaClass
                    .getMethod("canControlPersistTetheringSettings")
                    .invoke(manager) as? Boolean ?: false
            }.getOrDefault(false)

        private fun readMobileNetworkState(): MobileNetworkState {
            val supported = context.packageManager.hasSystemFeature(TELEPHONY_FEATURE)
            if (!supported || subscriptionManager == null || telephonyManager == null) {
                return MobileNetworkState(isSupported = false)
            }
            val subscriptions =
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    subscriptionManager.javaClass
                        .getMethod("getActiveSubscriptionInfoList")
                        .invoke(subscriptionManager) as? List<Any>
                }.getOrNull().orEmpty()
            val defaultId = readDefaultSubscriptionId()
            val target = defaultId?.let(telephonyManager::createForSubscriptionId) ?: telephonyManager
            val usage =
                runCatching {
                    val now = System.currentTimeMillis()
                    val bucket =
                        networkStatsManager?.querySummaryForDevice(
                            ConnectivityManager.TYPE_MOBILE,
                            null,
                            now - DATA_USAGE_WINDOW_MILLIS,
                            now,
                        )
                    (bucket?.rxBytes ?: 0L) + (bucket?.txBytes ?: 0L)
                }.getOrDefault(0L)
            val policy = readMobilePolicy()
            return MobileNetworkState(
                isSupported = true,
                subscriptions =
                    subscriptions
                        .mapNotNull { info ->
                            val id =
                                runCatching { info.javaClass.getMethod("getSubscriptionId").invoke(info) as Int }.getOrNull()
                                    ?: return@mapNotNull null
                            val display =
                                runCatching {
                                    info.javaClass
                                        .getMethod("getDisplayName")
                                        .invoke(info)
                                        ?.toString()
                                }.getOrNull()
                                    .orEmpty()
                                    .ifBlank { "SIM ${id + 1}" }
                            val carrier =
                                runCatching {
                                    info.javaClass
                                        .getMethod("getCarrierName")
                                        .invoke(info)
                                        ?.toString()
                                }.getOrNull().orEmpty()
                            MobileSubscription(id, display, carrier, id == defaultId)
                        }.sortedBy(MobileSubscription::displayName),
                defaultSubscriptionId = defaultId,
                mobileDataEnabled = invokeBoolean(target, "isDataEnabled"),
                roamingEnabled = invokeBoolean(target, "isDataRoamingEnabled"),
                dataUsageBytes = usage,
                warningBytes = policy?.first,
                limitBytes = policy?.second,
                mobileDataChangeAllowed = !hasUserRestriction(DISALLOW_CONFIG_MOBILE_NETWORKS),
                roamingChangeAllowed = !hasUserRestriction(DISALLOW_DATA_ROAMING),
                defaultDataChangeAllowed = !hasUserRestriction(DISALLOW_CONFIG_MOBILE_NETWORKS),
            )
        }

        private fun hasUserRestriction(restriction: String): Boolean = userManager?.hasUserRestriction(restriction) == true

        private fun readDefaultSubscriptionId(): Int? =
            runCatching {
                SubscriptionManager::class.java.getMethod("getDefaultDataSubscriptionId").invoke(null) as Int
            }.getOrNull()?.takeIf { it >= 0 }

        private fun invokeBoolean(
            target: Any,
            methodName: String,
        ): Boolean = runCatching { target.javaClass.getMethod(methodName).invoke(target) as? Boolean ?: false }.getOrDefault(false)

        private fun readMobilePolicy(): Pair<Long?, Long?>? =
            runCatching {
                val policies =
                    networkPolicyManager?.javaClass?.getMethod("getNetworkPolicies")?.invoke(networkPolicyManager)
                        as? Array<*> ?: return@runCatching null
                val policy = policies.firstOrNull() ?: return@runCatching null
                val warning =
                    policy.javaClass
                        .getField("warningBytes")
                        .getLong(policy)
                        .takeIf { it >= 0L }
                val limit =
                    policy.javaClass
                        .getField("limitBytes")
                        .getLong(policy)
                        .takeIf { it >= 0L }
                warning to limit
            }.getOrNull()

        private fun readHotspotStatus(): HotspotStatus {
            val configuration =
                wifiManager?.let(WifiHiddenApiBridge::getHotspotConfiguration)
            val base = mutableState.value.hotspot
            return base.copy(
                configuration = configuration?.toDomain() ?: HotspotConfiguration(),
                supports5Ghz = wifiManager?.is5GHzBandSupported == true,
                supportsDualBand =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            wifiManager?.isBridgedApConcurrencySupported == true
                        }.getOrDefault(false)
                    } else {
                        false
                    },
            )
        }

        private fun buildConnectionDetails(
            network: WifiNetwork,
            info: WifiInfo?,
            linkProperties: LinkProperties?,
            configuration: WifiConfiguration?,
        ): WifiConnectionDetails {
            val ipv4 =
                linkProperties
                    ?.linkAddresses
                    ?.firstOrNull { it.address is Inet4Address }
            val routes = linkProperties?.routes.orEmpty()
            val gateway =
                routes
                    .firstOrNull {
                        it.isDefaultRoute && it.gateway is Inet4Address
                    }?.gateway
            return WifiConnectionDetails(
                network = network,
                macAddress = info?.macAddress?.takeUnless { it == DEFAULT_MAC_ADDRESS },
                ipv4Address = ipv4?.address?.hostAddress,
                ipv6Addresses =
                    linkProperties
                        ?.linkAddresses
                        .orEmpty()
                        .filterNot { it.address is Inet4Address }
                        .mapNotNull(LinkAddress::getAddress)
                        .mapNotNull { it.hostAddress },
                gateway = gateway?.hostAddress,
                dnsServers =
                    linkProperties
                        ?.dnsServers
                        .orEmpty()
                        .mapNotNull { it.hostAddress },
                subnetMask = ipv4?.let(::prefixLengthToSubnetMask),
                linkSpeedMbps = info?.linkSpeed?.takeIf { it >= 0 },
                frequencyMhz = info?.frequency?.takeIf { it > 0 },
                securityLabel = network.security.label(),
                isAutoJoinEnabled =
                    configuration?.let(WifiHiddenApiBridge::getAutoJoin) ?: true,
                meteredOverride =
                    when (configuration?.let(WifiHiddenApiBridge::getMeteredOverride)) {
                        METERED_OVERRIDE_METERED -> MeteredOverride.METERED
                        METERED_OVERRIDE_NOT_METERED ->
                            MeteredOverride.NOT_METERED

                        else -> MeteredOverride.AUTOMATIC
                    },
                qrPayload =
                    configuration?.let { config ->
                        wifiQrPayload(
                            ssid = network.ssid,
                            security = network.security,
                            password = config.preSharedKey?.trim('"'),
                        )
                    },
            )
        }

        private suspend fun execute(block: suspend () -> Unit): ActionResult =
            withContext(dispatcher) {
                try {
                    block()
                    ActionResult.Success
                } catch (throwable: Throwable) {
                    throwable.rethrowIfCancellation()
                    fail(throwable)
                }
            }

        private fun fail(throwable: Throwable): ActionResult.Failure {
            Timber.w(throwable, "Wi-Fi platform action failed")
            val message = throwable.cause?.message ?: throwable.message ?: "Unknown Wi-Fi error"
            mutableState.value = mutableState.value.copy(lastError = message)
            return ActionResult.Failure(message, throwable)
        }
    }

private fun Int.toDomain(): WifiRadioState =
    when (this) {
        WifiManager.WIFI_STATE_DISABLED -> WifiRadioState.DISABLED
        WifiManager.WIFI_STATE_DISABLING -> WifiRadioState.DISABLING
        WifiManager.WIFI_STATE_ENABLING -> WifiRadioState.ENABLING
        WifiManager.WIFI_STATE_ENABLED -> WifiRadioState.ENABLED
        else -> WifiRadioState.UNKNOWN
    }

private fun ScanResult.toDomain(
    networkId: Int?,
    isSaved: Boolean,
    isConnected: Boolean,
): WifiNetwork {
    val security = securityFromCapabilities(capabilities)
    return WifiNetwork(
        key = scanKey(SSID, security),
        networkId = networkId,
        ssid = SSID.ifBlank { "<hidden network>" },
        bssid = BSSID,
        security = security,
        signalLevel = WifiManager.calculateSignalLevel(level, 5),
        rssi = level,
        isSaved = isSaved,
        isConnected = isConnected,
        isHidden = SSID.isBlank(),
    )
}

private fun WifiConfiguration.toDomain(
    scan: ScanResult?,
    isConnected: Boolean,
): WifiNetwork {
    val security = securityFromConfiguration(this)
    return WifiNetwork(
        key = scanKey(unquote(SSID), security),
        networkId = networkId,
        ssid = unquote(SSID).ifBlank { "<hidden network>" },
        bssid = scan?.BSSID,
        security = security,
        signalLevel = scan?.let { WifiManager.calculateSignalLevel(it.level, 5) } ?: 0,
        rssi = scan?.level ?: Int.MIN_VALUE,
        isSaved = true,
        isConnected = isConnected,
        isHidden = hiddenSSID,
    )
}

private fun WifiInfo.toDomain(configuration: WifiConfiguration?): WifiNetwork {
    val security = configuration?.let(::securityFromConfiguration) ?: WifiSecurity.UNKNOWN
    return WifiNetwork(
        key = scanKey(unquote(ssid), security),
        networkId = networkId,
        ssid = unquote(ssid).ifBlank { "<unknown network>" },
        bssid = bssid,
        security = security,
        signalLevel = WifiManager.calculateSignalLevel(rssi, 5),
        rssi = rssi,
        isSaved = configuration != null,
        isConnected = true,
        isHidden = configuration?.hiddenSSID == true,
    )
}

private fun WifiHiddenApiBridge.HotspotConfig.toDomain(): HotspotConfiguration =
    HotspotConfiguration(
        ssid = ssid ?: "",
        password = passphrase ?: "",
        security =
            when (securityType) {
                SoftApConfiguration.SECURITY_TYPE_OPEN -> HotspotSecurity.OPEN
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE ->
                    HotspotSecurity.WPA3_PERSONAL

                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION ->
                    HotspotSecurity.WPA2_WPA3_PERSONAL

                else -> HotspotSecurity.WPA2_PERSONAL
            },
        band =
            when {
                bands.size > 1 -> HotspotBand.DUAL
                band and SoftApConfiguration.BAND_5GHZ != 0 -> HotspotBand.GHZ_5
                band and SoftApConfiguration.BAND_2GHZ != 0 -> HotspotBand.GHZ_2_4
                else -> HotspotBand.AUTO
            },
        autoShutdownEnabled = autoShutdownEnabled,
        maxClients = maxClients,
    )

private const val INVALID_NETWORK_ID = -1
private const val DEFAULT_MAC_ADDRESS = "02:00:00:00:00:00"
private const val CONFIGURED_NETWORKS_CHANGED_ACTION =
    "android.net.wifi.CONFIGURED_NETWORKS_CHANGE"
private const val WIFI_WAKEUP_ENABLED = "wifi_wakeup_enabled"
private const val WIFI_CELLULAR_FALLBACK = "wifi_cellular_fallback"
private const val ENABLE_PERSISTENT_TETHERING = "enable_persistent_tethering"
private const val WIFI_AP_STATE_DISABLING = 10
private const val WIFI_AP_STATE_ENABLING = 12
private const val WIFI_AP_STATE_ENABLED = 13
private const val WIFI_AP_STATE_FAILED = 14
private const val METERED_OVERRIDE_NONE = 0
private const val METERED_OVERRIDE_METERED = 1
private const val METERED_OVERRIDE_NOT_METERED = 2
private const val TETHERING_CALLBACK_TIMEOUT_MS = 20_000L
private const val TELEPHONY_FEATURE = "android.hardware.telephony"
private const val DATA_USAGE_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1_000L
private const val DISALLOW_CONFIG_MOBILE_NETWORKS = "no_config_mobile_networks"
private const val DISALLOW_DATA_ROAMING = "no_config_data_roaming"

internal fun securityFromCapabilities(capabilities: String): WifiSecurity =
    when {
        capabilities.contains("SAE") && capabilities.contains("PSK") ->
            WifiSecurity.WPA2_WPA3_PERSONAL

        capabilities.contains("SAE") -> WifiSecurity.WPA3_PERSONAL
        capabilities.contains("OWE") -> WifiSecurity.OWE
        capabilities.contains("PSK") -> WifiSecurity.WPA2_PERSONAL
        capabilities.contains("EAP") -> WifiSecurity.EAP
        capabilities.contains("WEP") -> WifiSecurity.WEP
        capabilities.contains("ESS") || capabilities.isBlank() -> WifiSecurity.OPEN
        else -> WifiSecurity.UNKNOWN
    }

private fun securityFromConfiguration(configuration: WifiConfiguration): WifiSecurity =
    when {
        configuration.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.SAE) ->
            WifiSecurity.WPA3_PERSONAL

        configuration.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.OWE) -> WifiSecurity.OWE
        configuration.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK) ->
            WifiSecurity.WPA2_PERSONAL

        configuration.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP) ||
            configuration.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X) ->
            WifiSecurity.EAP

        configuration.wepKeys.firstOrNull() != null -> WifiSecurity.WEP
        else -> WifiSecurity.OPEN
    }

internal fun securityCompatible(
    configured: WifiSecurity,
    scanned: WifiSecurity,
): Boolean =
    configured == scanned ||
        (
            configured == WifiSecurity.WPA2_PERSONAL &&
                scanned == WifiSecurity.WPA2_WPA3_PERSONAL
        ) ||
        (
            configured == WifiSecurity.WPA3_PERSONAL &&
                scanned == WifiSecurity.WPA2_WPA3_PERSONAL
        )

private fun WifiSecurity.label(): String =
    when (this) {
        WifiSecurity.OPEN -> "None"
        WifiSecurity.OWE -> "Enhanced Open (OWE)"
        WifiSecurity.WEP -> "WEP"
        WifiSecurity.WPA2_PERSONAL -> "WPA2-Personal"
        WifiSecurity.WPA3_PERSONAL -> "WPA3-Personal"
        WifiSecurity.WPA2_WPA3_PERSONAL -> "WPA2/WPA3-Personal"
        WifiSecurity.EAP -> "Enterprise (802.1X)"
        WifiSecurity.UNKNOWN -> "Unknown"
    }

private fun scanKey(
    ssid: String,
    security: WifiSecurity,
): String = "${ssid.ifBlank { "<hidden>" }}|${security.name}"

private fun quote(value: String): String = "\"${value.replace("\"", "\\\"")}\""

private fun unquote(value: String?): String =
    value
        ?.removePrefix("\"")
        ?.removeSuffix("\"")
        .orEmpty()

private fun isHexWepKey(value: String): Boolean = value.length in setOf(10, 26, 58) && value.all(Char::isHexDigit)

private fun isPskHex(value: String): Boolean = value.length == 64 && value.all(Char::isHexDigit)

private fun Char.isHexDigit(): Boolean = isDigit() || lowercaseChar() in 'a'..'f'

private fun prefixLengthToSubnetMask(linkAddress: LinkAddress): String {
    val prefixLength = linkAddress.prefixLength
    val mask = if (prefixLength == 0) 0 else -1 shl (32 - prefixLength)
    return listOf(24, 16, 8, 0).joinToString(".") { shift ->
        ((mask ushr shift) and 0xff).toString()
    }
}
