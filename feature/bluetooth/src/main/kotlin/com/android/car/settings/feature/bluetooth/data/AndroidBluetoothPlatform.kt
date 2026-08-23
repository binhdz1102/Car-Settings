@file:Suppress("DEPRECATION")
@file:SuppressLint("MissingPermission", "NewApi")

package com.android.car.settings.feature.bluetooth.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothHidHost
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothMapClient
import android.bluetooth.BluetoothPan
import android.bluetooth.BluetoothPbapClient
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.feature.bluetooth.domain.BluetoothAdapterInfo
import com.android.car.settings.feature.bluetooth.domain.BluetoothBondState
import com.android.car.settings.feature.bluetooth.domain.BluetoothConnectionState
import com.android.car.settings.feature.bluetooth.domain.BluetoothDeviceModel
import com.android.car.settings.feature.bluetooth.domain.BluetoothDeviceType
import com.android.car.settings.feature.bluetooth.domain.BluetoothProfilePolicy
import com.android.car.settings.feature.bluetooth.domain.BluetoothProfileState
import com.android.car.settings.feature.bluetooth.domain.BluetoothProfileType
import com.android.car.settings.feature.bluetooth.domain.BluetoothRadioState
import com.android.car.settings.feature.bluetooth.domain.BluetoothState
import com.android.car.settings.feature.bluetooth.domain.UwbState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
internal class AndroidBluetoothPlatform
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : BluetoothPlatform {
        private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

        /** UWB is provided by a modular APEX and is not present in the platform compile stubs. */
        private val uwbManager: Any? = runCatching { context.getSystemService(UWB_SERVICE_NAME) }.getOrNull()
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val discoveredDevices = ConcurrentHashMap<String, DiscoveredDevice>()
        private val mutableState = MutableStateFlow(BluetoothState())
        private val profileProxyManager =
            ProfileProxyLeaseManager(
                connector = AndroidProfileProxyConnector(context, adapter),
                timeoutMillis = PROFILE_PROXY_TIMEOUT_MS,
            )
        private var selectedAddress: String? = null

        override val state: StateFlow<BluetoothState> = mutableState.asStateFlow()

        private val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device =
                                intent.getParcelableExtra<BluetoothDevice>(
                                    BluetoothDevice.EXTRA_DEVICE,
                                )
                            val rssi =
                                intent.getShortExtra(
                                    BluetoothDevice.EXTRA_RSSI,
                                    Short.MIN_VALUE,
                                )
                            device?.let {
                                discoveredDevices[it.address] =
                                    DiscoveredDevice(
                                        device = it,
                                        rssi =
                                            rssi
                                                .takeUnless { value -> value == Short.MIN_VALUE }
                                                ?.toInt(),
                                    )
                            }
                        }

                        BluetoothAdapter.ACTION_DISCOVERY_STARTED -> Unit
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> Unit
                        BluetoothAdapter.ACTION_STATE_CHANGED -> {
                            if (adapter?.state == BluetoothAdapter.STATE_OFF) {
                                discoveredDevices.clear()
                            }
                        }
                    }
                    scope.launch { refresh() }
                }
            }

        init {
            val filter =
                IntentFilter().apply {
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
                    addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothDevice.ACTION_NAME_CHANGED)
                    addAction(BluetoothDevice.ACTION_ALIAS_CHANGED)
                    addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                    addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    addAction(BluetoothDevice.ACTION_UUID)
                    addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                    addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                    addAction(BluetoothHidHost.ACTION_CONNECTION_STATE_CHANGED)
                    addAction(BluetoothPan.ACTION_CONNECTION_STATE_CHANGED)
                    addAction(BluetoothPbapClient.ACTION_CONNECTION_STATE_CHANGED)
                    addAction(BluetoothMapClient.ACTION_CONNECTION_STATE_CHANGED)
                    addAction(ACTION_BATTERY_LEVEL_CHANGED)
                }
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            scope.launch { refresh() }
        }

        override suspend fun refresh(): ActionResult =
            execute {
                val localAdapter = requireNotNull(adapter) { "Bluetooth is not supported" }
                val bondedDevices =
                    runCatching { localAdapter.bondedDevices.orEmpty() }
                        .getOrDefault(emptySet())
                        .toList()
                val profileStates = readProfileStates(bondedDevices)
                val bonded =
                    bondedDevices
                        .map { device ->
                            device.toDomain(profileStates = profileStates[device.address].orEmpty())
                        }.sortedBy { it.alias.lowercase() }
                val available =
                    discoveredDevices.values
                        .filterNot { it.device.address in bonded.map(BluetoothDeviceModel::address) }
                        .map { it.device.toDomain(it.rssi) }
                        .sortedWith(
                            compareByDescending<BluetoothDeviceModel> { it.rssi ?: Int.MIN_VALUE }
                                .thenBy { it.alias.lowercase() },
                        )
                val selected =
                    (bonded + available).firstOrNull { it.address == selectedAddress }
                mutableState.value =
                    BluetoothState(
                        adapter =
                            BluetoothAdapterInfo(
                                name = runCatching { localAdapter.name }.getOrNull().orEmpty(),
                                address = runCatching { localAdapter.address }.getOrNull().orEmpty(),
                                radioState = localAdapter.state.toDomain(),
                                isDiscovering = localAdapter.isDiscovering,
                                isDiscoverable = localAdapter.isDiscoverable(),
                                discoverableTimeoutSeconds =
                                    localAdapter.currentDiscoverableTimeoutSeconds(),
                            ),
                        pairedDevices = bonded,
                        availableDevices = available,
                        selectedDevice = selected,
                        uwb = readUwbState(),
                    )
            }

        override suspend fun setEnabled(enabled: Boolean): ActionResult =
            execute {
                val localAdapter = requireNotNull(adapter) { "Bluetooth is not supported" }
                val accepted = if (enabled) localAdapter.enable() else localAdapter.disable()
                check(accepted) { "The platform rejected the Bluetooth state change" }
            }

        override suspend fun setUwbEnabled(enabled: Boolean): ActionResult =
            execute {
                val state = readUwbState()
                check(state.isSupported) { "Ultra-Wideband is not supported on this vehicle" }
                val manager = requireNotNull(uwbManager) { "Ultra-Wideband service is unavailable" }
                mutableState.value = mutableState.value.copy(uwb = state.copy(isLoading = true, error = null))
                try {
                    val method =
                        manager.javaClass.methods.firstOrNull {
                            it.name == "setEnabled" &&
                                it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType))
                        } ?: error("Ultra-Wideband control is unavailable on this image")
                    method.invoke(manager, enabled)
                } finally {
                    mutableState.value = mutableState.value.copy(uwb = readUwbState())
                }
            }

        override suspend fun startDiscovery(): ActionResult =
            execute {
                val localAdapter = requireNotNull(adapter)
                discoveredDevices.clear()
                if (localAdapter.isDiscovering) localAdapter.cancelDiscovery()
                check(localAdapter.startDiscovery()) {
                    "The platform rejected Bluetooth discovery"
                }
                refresh()
            }

        override suspend fun stopDiscovery(): ActionResult =
            execute {
                val localAdapter = requireNotNull(adapter)
                if (localAdapter.isDiscovering) {
                    check(localAdapter.cancelDiscovery()) { "Unable to stop discovery" }
                }
                refresh()
            }

        override suspend fun pair(address: String): ActionResult =
            execute {
                adapter?.takeIf(BluetoothAdapter::isDiscovering)?.cancelDiscovery()
                val device = getDevice(address)
                check(device.createBond()) { "Unable to start pairing with ${device.safeName()}" }
                selectedAddress = address
                refresh()
            }

        override suspend fun cancelPairing(address: String): ActionResult =
            invokeDeviceBoolean(address, "cancelBondProcess", "Unable to cancel pairing")

        override suspend fun unpair(address: String): ActionResult =
            invokeDeviceBoolean(address, "removeBond", "Unable to forget Bluetooth device")

        override suspend fun connect(address: String): ActionResult =
            invokeDeviceBoolean(address, "connect", "Unable to connect Bluetooth device")

        override suspend fun disconnect(address: String): ActionResult =
            invokeDeviceBoolean(address, "disconnect", "Unable to disconnect Bluetooth device")

        override suspend fun selectDevice(address: String): ActionResult =
            execute {
                getDevice(address)
                selectedAddress = address
                refresh()
            }

        override suspend fun renameDevice(
            address: String,
            alias: String,
        ): ActionResult =
            execute {
                require(alias.isNotBlank()) { "Device name cannot be empty" }
                val device = getDevice(address)
                val result =
                    BluetoothDevice::class.java
                        .getMethod("setAlias", String::class.java)
                        .invoke(device, alias.trim())
                checkReflectionResult(result) { "Unable to rename Bluetooth device" }
                refresh()
            }

        override suspend fun renameAdapter(name: String): ActionResult =
            execute {
                require(name.isNotBlank()) { "Bluetooth name cannot be empty" }
                check(requireNotNull(adapter).setName(name.trim())) {
                    "Unable to rename this device"
                }
                refresh()
            }

        override suspend fun setDiscoverable(
            enabled: Boolean,
            durationSeconds: Int,
        ): ActionResult =
            execute {
                val localAdapter = requireNotNull(adapter)
                if (enabled) {
                    val duration = durationSeconds.coerceIn(1, MAX_DISCOVERABLE_SECONDS)
                    checkBluetoothStatus(
                        BluetoothHiddenApiBridge.setDiscoverableTimeout(
                            localAdapter,
                            Duration.ofSeconds(duration.toLong()),
                        ),
                    ) { "Unable to set discoverable timeout" }
                    checkBluetoothStatus(
                        BluetoothHiddenApiBridge.setScanMode(
                            localAdapter,
                            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE,
                        ),
                    ) { "Unable to enable discoverable mode" }
                } else {
                    checkBluetoothStatus(
                        BluetoothHiddenApiBridge.setScanMode(
                            localAdapter,
                            BluetoothAdapter.SCAN_MODE_CONNECTABLE,
                        ),
                    ) { "Unable to disable discoverable mode" }
                }
                refresh()
            }

        override suspend fun setProfileEnabled(
            address: String,
            profile: BluetoothProfileType,
            enabled: Boolean,
        ): ActionResult =
            execute {
                val device = getDevice(address)
                profileProxyManager.withProxy(profile.platformId) { proxy ->
                    val policy =
                        if (enabled) {
                            BluetoothHiddenApiBridge.connectionPolicyAllowed()
                        } else {
                            BluetoothHiddenApiBridge.connectionPolicyForbidden()
                        }
                    val policyMethod =
                        proxy.javaClass.methods.firstOrNull {
                            it.name == "setConnectionPolicy" &&
                                it.parameterTypes.contentEquals(
                                    arrayOf(
                                        BluetoothDevice::class.java,
                                        Int::class.javaPrimitiveType,
                                    ),
                                )
                        }
                            ?: error("${profile.displayName} policy API is unavailable")
                    checkReflectionResult(policyMethod.invoke(proxy, device, policy)) {
                        "Unable to update ${profile.displayName} policy"
                    }

                    val action = if (enabled) "connect" else "disconnect"
                    val actionMethod =
                        proxy.javaClass.methods.firstOrNull {
                            it.name == action &&
                                it.parameterTypes.contentEquals(
                                    arrayOf(BluetoothDevice::class.java),
                                )
                        }
                    actionMethod?.let {
                        checkReflectionResult(it.invoke(proxy, device)) {
                            "Unable to $action ${profile.displayName}"
                        }
                    }
                }
                refresh()
            }

        private suspend fun readProfileStates(devices: List<BluetoothDevice>): Map<String, List<BluetoothProfileState>> {
            if (devices.isEmpty()) return emptyMap()

            val relevantProfiles =
                devices
                    .flatMap { device ->
                        supportedProfileTypes(
                            uuids = device.uuids.orEmpty().map { it.uuid.toString() },
                            bluetoothClass = device.bluetoothClass,
                        )
                    }.toSet()

            val snapshots =
                coroutineScope {
                    relevantProfiles
                        .map { profile ->
                            async { readProfileState(profile, devices) }
                        }.awaitAll()
                        .flatten()
                }

            return snapshots.groupBy(
                keySelector = { it.address },
                valueTransform = { it.state },
            )
        }

        private suspend fun readProfileState(
            profile: BluetoothProfileType,
            devices: List<BluetoothDevice>,
        ): List<DeviceProfileState> =
            try {
                profileProxyManager.withProxy(profile.platformId) { proxy ->
                    devices.map { device ->
                        DeviceProfileState(
                            address = device.address,
                            state =
                                BluetoothProfileState(
                                    profile = profile,
                                    connectionState =
                                        proxy.getConnectionState(device).toDomainConnection(),
                                    policy = proxy.readPolicy(device),
                                ),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                Log.w(TAG, "Unable to read ${profile.displayName} profile state", throwable)
                devices.map { device ->
                    DeviceProfileState(
                        address = device.address,
                        state =
                            BluetoothProfileState(
                                profile = profile,
                                connectionState =
                                    bluetoothManager
                                        ?.getConnectionState(device, profile.platformId)
                                        ?.toDomainConnection()
                                        ?: BluetoothConnectionState.DISCONNECTED,
                                policy = BluetoothProfilePolicy.UNKNOWN,
                            ),
                    )
                }
            }

        private suspend fun invokeDeviceBoolean(
            address: String,
            methodName: String,
            failureMessage: String,
        ): ActionResult =
            execute {
                val device = getDevice(address)
                val method = BluetoothDevice::class.java.getMethod(methodName)
                checkReflectionResult(method.invoke(device)) { failureMessage }
                refresh()
            }

        private fun getDevice(address: String): BluetoothDevice {
            require(BluetoothAdapter.checkBluetoothAddress(address)) {
                "Invalid Bluetooth address"
            }
            return requireNotNull(adapter).getRemoteDevice(address)
        }

        private suspend fun execute(block: suspend () -> Unit): ActionResult =
            withContext(dispatcher) {
                runBluetoothAction(block, ::fail)
            }

        private fun fail(throwable: Throwable): ActionResult.Failure {
            Timber.w(throwable, "Bluetooth platform action failed")
            val message =
                throwable.cause?.message ?: throwable.message ?: "Unknown Bluetooth error"
            mutableState.value = mutableState.value.copy(lastError = message)
            return ActionResult.Failure(message, throwable)
        }

        private fun readUwbState(): UwbState {
            val supported = context.packageManager.hasSystemFeature(UWB_FEATURE)
            val manager = uwbManager
            return when {
                !supported || manager == null -> UwbState(isSupported = false)
                else -> {
                    runCatching {
                        val enabled = manager.javaClass.getMethod("isEnabled").invoke(manager) as? Boolean ?: false
                        UwbState(isSupported = true, isEnabled = enabled)
                    }.getOrElse { throwable ->
                        UwbState(
                            isSupported = true,
                            error = throwable.message ?: "Unable to read Ultra-Wideband state",
                        )
                    }
                }
            }
        }
    }

private data class DiscoveredDevice(
    val device: BluetoothDevice,
    val rssi: Int?,
)

private data class DeviceProfileState(
    val address: String,
    val state: BluetoothProfileState,
)

private fun BluetoothDevice.toDomain(
    rssi: Int? = null,
    profileStates: List<BluetoothProfileState> = emptyList(),
): BluetoothDeviceModel {
    val overallConnected = isConnectedCompat()
    val uuids = uuids.orEmpty().map { it.uuid.toString() }
    val supported = supportedProfileTypes(uuids, bluetoothClass)
    val snapshots = profileStates.associateBy(BluetoothProfileState::profile)
    val profiles =
        BluetoothProfileType
            .values()
            .filter { profile ->
                profile in supported ||
                    snapshots[profile]
                        ?.connectionState
                        ?.let { it != BluetoothConnectionState.DISCONNECTED } == true ||
                    snapshots[profile]?.policy == BluetoothProfilePolicy.ALLOWED
            }.map { profile ->
                snapshots[profile] ?: BluetoothProfileState(profile = profile)
            }
    return BluetoothDeviceModel(
        address = address,
        name = safeName(),
        alias = runCatching { alias }.getOrNull().takeUnless { it.isNullOrBlank() } ?: safeName(),
        bondState = bondState.toDomainBond(),
        connectionState =
            if (overallConnected) {
                BluetoothConnectionState.CONNECTED
            } else {
                BluetoothConnectionState.DISCONNECTED
            },
        deviceType = type.toDomainType(),
        bluetoothClass = bluetoothClass?.readableClass(),
        batteryLevel =
            runCatching {
                BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(this) as? Int
            }.getOrNull()
                ?.takeIf { it in 0..100 },
        rssi = rssi,
        uuids = uuids,
        profiles = profiles,
    )
}

private fun BluetoothDevice.safeName(): String = runCatching { name }.getOrNull().takeUnless { it.isNullOrBlank() } ?: address

private fun BluetoothDevice.isConnectedCompat(): Boolean =
    runCatching {
        BluetoothDevice::class.java.getMethod("isConnected").invoke(this) as? Boolean
    }.getOrNull() == true

private fun supportedProfileTypes(
    uuids: List<String>,
    bluetoothClass: BluetoothClass?,
): Set<BluetoothProfileType> {
    val normalized = uuids.map(String::lowercase)
    val audio =
        bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
    val selected =
        buildSet {
            if (audio || normalized.any { it.contains("0000110b") || it.contains("0000110a") }) {
                add(BluetoothProfileType.A2DP)
            }
            if (audio || normalized.any { it.contains("00001108") || it.contains("0000111e") }) {
                add(BluetoothProfileType.HEADSET)
            }
            if (normalized.any { it.contains("00001124") }) add(BluetoothProfileType.HID_HOST)
            if (normalized.any { it.contains("00001115") || it.contains("00001116") }) {
                add(BluetoothProfileType.PAN)
            }
            if (normalized.any { it.contains("0000112f") }) {
                add(BluetoothProfileType.PBAP_CLIENT)
            }
            if (normalized.any { it.contains("00001132") }) {
                add(BluetoothProfileType.MAP_CLIENT)
            }
        }
    return selected
}

private fun BluetoothProfile.readPolicy(device: BluetoothDevice): BluetoothProfilePolicy {
    val method =
        javaClass.methods.firstOrNull {
            it.name == "getConnectionPolicy" &&
                it.parameterTypes.contentEquals(arrayOf(BluetoothDevice::class.java))
        } ?: return BluetoothProfilePolicy.UNKNOWN
    return when (method.invoke(this, device) as? Int) {
        BluetoothHiddenApiBridge.connectionPolicyAllowed() -> BluetoothProfilePolicy.ALLOWED
        BluetoothHiddenApiBridge.connectionPolicyForbidden() -> BluetoothProfilePolicy.FORBIDDEN
        else -> BluetoothProfilePolicy.UNKNOWN
    }
}

private fun BluetoothAdapter.isDiscoverable(): Boolean = scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE

private fun BluetoothAdapter.currentDiscoverableTimeoutSeconds(): Int =
    if (isDiscoverable()) {
        discoverableTimeout
            ?.seconds
            ?.coerceIn(0L, Int.MAX_VALUE.toLong())
            ?.toInt()
            ?: 0
    } else {
        0
    }

private fun Int.toDomain(): BluetoothRadioState =
    when (this) {
        BluetoothAdapter.STATE_OFF -> BluetoothRadioState.OFF
        BluetoothAdapter.STATE_TURNING_OFF -> BluetoothRadioState.TURNING_OFF
        BluetoothAdapter.STATE_TURNING_ON -> BluetoothRadioState.TURNING_ON
        BluetoothAdapter.STATE_ON -> BluetoothRadioState.ON
        else -> BluetoothRadioState.UNKNOWN
    }

private fun Int.toDomainBond(): BluetoothBondState =
    when (this) {
        BluetoothDevice.BOND_BONDED -> BluetoothBondState.BONDED
        BluetoothDevice.BOND_BONDING -> BluetoothBondState.BONDING
        else -> BluetoothBondState.NONE
    }

private fun Int.toDomainConnection(): BluetoothConnectionState =
    when (this) {
        BluetoothProfile.STATE_CONNECTING -> BluetoothConnectionState.CONNECTING
        BluetoothProfile.STATE_CONNECTED -> BluetoothConnectionState.CONNECTED
        BluetoothProfile.STATE_DISCONNECTING -> BluetoothConnectionState.DISCONNECTING
        else -> BluetoothConnectionState.DISCONNECTED
    }

private fun Int.toDomainType(): BluetoothDeviceType =
    when (this) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> BluetoothDeviceType.CLASSIC
        BluetoothDevice.DEVICE_TYPE_LE -> BluetoothDeviceType.LOW_ENERGY
        BluetoothDevice.DEVICE_TYPE_DUAL -> BluetoothDeviceType.DUAL
        else -> BluetoothDeviceType.UNKNOWN
    }

private fun BluetoothClass.readableClass(): String =
    when (majorDeviceClass) {
        BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio / video"
        BluetoothClass.Device.Major.COMPUTER -> "Computer"
        BluetoothClass.Device.Major.HEALTH -> "Health"
        BluetoothClass.Device.Major.IMAGING -> "Imaging"
        BluetoothClass.Device.Major.MISC -> "Miscellaneous"
        BluetoothClass.Device.Major.NETWORKING -> "Networking"
        BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
        BluetoothClass.Device.Major.PHONE -> "Phone"
        BluetoothClass.Device.Major.TOY -> "Toy"
        BluetoothClass.Device.Major.WEARABLE -> "Wearable"
        else -> "Uncategorized"
    }

private inline fun checkReflectionResult(
    result: Any?,
    lazyMessage: () -> String,
) {
    when (result) {
        is Boolean -> check(result, lazyMessage)
        is Int -> check(result == BluetoothStatusCodes.SUCCESS, lazyMessage)
        else -> Unit
    }
}

private inline fun checkBluetoothStatus(
    result: Int,
    lazyMessage: () -> String,
) {
    check(result == BluetoothStatusCodes.SUCCESS) {
        "${lazyMessage()} (status=$result)"
    }
}

private class AndroidProfileProxyConnector(
    private val context: Context,
    private val adapter: BluetoothAdapter?,
) : ProfileProxyConnector<BluetoothProfile> {
    override fun request(
        profileId: Int,
        listener: ProfileProxyConnector.Listener<BluetoothProfile>,
    ): Boolean =
        adapter?.getProfileProxy(
            context,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(
                    profile: Int,
                    proxy: BluetoothProfile,
                ) {
                    listener.onConnected(profile, proxy)
                }

                override fun onServiceDisconnected(profile: Int) {
                    listener.onDisconnected(profile)
                }
            },
            profileId,
        ) == true

    override fun close(
        profileId: Int,
        proxy: BluetoothProfile,
    ) {
        adapter?.closeProfileProxy(profileId, proxy)
    }
}

private const val ACTION_BATTERY_LEVEL_CHANGED =
    "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
private const val MAX_DISCOVERABLE_SECONDS = 3_600
private const val PROFILE_PROXY_TIMEOUT_MS = 10_000L
private const val TAG = "MySystemBluetooth"
private const val UWB_FEATURE = "android.hardware.uwb"
private const val UWB_SERVICE_NAME = "uwb"
