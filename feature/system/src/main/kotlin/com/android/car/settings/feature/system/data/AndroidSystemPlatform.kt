@file:SuppressLint("MissingPermission", "InlinedApi")

package com.android.car.settings.feature.system.data

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.usage.StorageStatsManager
import android.bluetooth.BluetoothManager
import android.car.VehiclePropertyIds
import android.car.VehicleUnit
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.NetworkPolicyManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.UserManager
import android.os.storage.StorageManager
import android.provider.Settings
import android.service.autofill.AutofillService
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.euicc.EuiccManager
import android.view.autofill.AutofillManager
import android.view.inputmethod.InputMethodManager
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.core.vehicle.VehiclePropertyArea
import com.android.car.settings.core.vehicle.VehiclePropertyClient
import com.android.car.settings.core.vehicle.VehiclePropertyResult
import com.android.car.settings.core.vehicle.VehiclePropertySpec
import com.android.car.settings.core.vehicle.VehiclePropertyWriteResult
import com.android.car.settings.feature.system.domain.AboutInfo
import com.android.car.settings.feature.system.domain.AutofillInfo
import com.android.car.settings.feature.system.domain.AutofillServiceOption
import com.android.car.settings.feature.system.domain.DEVELOPER_TAP_COUNT
import com.android.car.settings.feature.system.domain.HardwareInfo
import com.android.car.settings.feature.system.domain.KeyboardInfo
import com.android.car.settings.feature.system.domain.LanguageInputInfo
import com.android.car.settings.feature.system.domain.LegalInfo
import com.android.car.settings.feature.system.domain.LocaleOption
import com.android.car.settings.feature.system.domain.NetworkSubscription
import com.android.car.settings.feature.system.domain.ResetOptionsInfo
import com.android.car.settings.feature.system.domain.StorageCategory
import com.android.car.settings.feature.system.domain.StorageCategoryId
import com.android.car.settings.feature.system.domain.StorageInfo
import com.android.car.settings.feature.system.domain.SystemExternalAction
import com.android.car.settings.feature.system.domain.SystemExternalActionId
import com.android.car.settings.feature.system.domain.SystemSettingsState
import com.android.car.settings.feature.system.domain.TextToSpeechEngine
import com.android.car.settings.feature.system.domain.TextToSpeechInfo
import com.android.car.settings.feature.system.domain.UnitOption
import com.android.car.settings.feature.system.domain.VehicleUnitSetting
import com.android.car.settings.feature.system.domain.VehicleUnitsInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automotive System settings implementation mapped to AAOS Settings/System controllers.
 * Privileged mutations deliberately run on the injected I/O dispatcher; state is only refreshed
 * from framework events or after a successful operation, never by polling.
 */
@Singleton
@Suppress("TooManyFunctions", "LargeClass")
internal class AndroidSystemPlatform
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
        private val vehiclePropertyClient: VehiclePropertyClient,
    ) : SystemPlatform {
        private val resolver = context.contentResolver
        private val packageManager = context.packageManager
        private val userManager = context.getSystemService(UserManager::class.java)
        private val powerManager = context.getSystemService(PowerManager::class.java)
        private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        private val wifiManager = context.getSystemService(WifiManager::class.java)
        private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        private val networkPolicyManager = context.getSystemService(NetworkPolicyManager::class.java)
        private val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
        private val euiccManager =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.getSystemService(EuiccManager::class.java)
            } else {
                null
            }
        private val accountManager = context.getSystemService(AccountManager::class.java)
        private val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
        private val autofillManager = context.getSystemService(AutofillManager::class.java)
        private val storageStatsManager = context.getSystemService(StorageStatsManager::class.java)
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val mutableState = MutableStateFlow(SystemSettingsState())
        private var developerTapsRemaining = DEVELOPER_TAP_COUNT

        private val availableLocales: List<LocaleOption> by lazy(::loadAvailableLocales)

        override val state: StateFlow<SystemSettingsState> = mutableState.asStateFlow()

        private val systemChangeReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    scope.launch { refresh() }
                }
            }

        private val secureSettingsObserver =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    scope.launch { refresh() }
                }
            }

        init {
            val systemChangeFilter =
                IntentFilter().apply {
                    addAction(Intent.ACTION_LOCALE_CHANGED)
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_CHANGED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addDataScheme("package")
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(systemChangeReceiver, systemChangeFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(systemChangeReceiver, systemChangeFilter)
            }
            listOf(
                Settings.Secure.ENABLED_INPUT_METHODS,
                Settings.Secure.DEFAULT_INPUT_METHOD,
                AUTOFILL_SERVICE_SETTING,
                Settings.Secure.TTS_DEFAULT_SYNTH,
                Settings.Secure.TTS_DEFAULT_RATE,
                Settings.Secure.TTS_DEFAULT_PITCH,
            ).forEach { key ->
                resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, secureSettingsObserver)
            }
            scope.launch { refresh() }
        }

        override suspend fun refresh(): ActionResult = execute { refreshState() }

        override suspend fun tapBuildNumber(): ActionResult =
            execute {
                check(isAdminUser()) { "Only an administrator can enable developer options" }
                check(userManager?.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES) != true) {
                    "Developer options are restricted by the administrator"
                }
                check(Settings.Global.getInt(resolver, Settings.Global.DEVICE_PROVISIONED, 0) != 0) {
                    "Finish device setup before enabling developer options"
                }
                if (developerOptionsEnabled()) {
                    developerTapsRemaining = -1
                } else {
                    developerTapsRemaining = (developerTapsRemaining - 1).coerceAtLeast(0)
                    if (developerTapsRemaining == 0) {
                        check(Settings.Global.putInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)) {
                            "The system did not allow enabling developer options"
                        }
                        developerTapsRemaining = -1
                    }
                }
                refreshState()
            }

        override suspend fun setSystemLocale(languageTag: String): ActionResult =
            execute {
                check(mutableState.value.languageInput.canConfigureLocale) {
                    "Changing the system language is restricted for this profile"
                }
                check(availableLocales.any { it.languageTag == languageTag }) { "Unsupported system language" }
                check(SystemHiddenApiBridge.updateSystemLocale(languageTag)) {
                    "The system did not allow changing the language"
                }
                refreshState()
            }

        override suspend fun setAutofillService(componentName: String?): ActionResult =
            execute {
                val autofill = mutableState.value.languageInput.autofill
                check(autofill.supported) { "Autofill is not supported on this device" }
                check(componentName == null || autofill.candidates.any { it.componentName == componentName }) {
                    "This autofill service is not available"
                }
                check(Settings.Secure.putString(resolver, AUTOFILL_SERVICE_SETTING, componentName)) {
                    "The system did not allow changing the autofill service"
                }
                refreshState()
            }

        override suspend fun setKeyboardEnabled(
            id: String,
            enabled: Boolean,
        ): ActionResult =
            execute {
                val keyboard =
                    mutableState.value.languageInput.keyboards
                        .firstOrNull { it.id == id }
                        ?: error("This keyboard is no longer installed")
                if (enabled == keyboard.enabled) return@execute
                check(enabled || keyboard.canDisable) { "At least one default keyboard must remain enabled" }
                val currentTokens = enabledInputMethodTokens().toMutableList()
                if (enabled) {
                    currentTokens += id
                } else {
                    currentTokens.removeAll { it.substringBefore(';') == id }
                }
                check(
                    Settings.Secure.putString(
                        resolver,
                        Settings.Secure.ENABLED_INPUT_METHODS,
                        currentTokens.distinctBy { it.substringBefore(';') }.joinToString(IME_DELIMITER),
                    ),
                ) { "The system did not allow changing the keyboard" }
                if (!enabled && Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD) == id) {
                    val newDefault =
                        mutableState.value.languageInput.keyboards
                            .firstOrNull { it.id != id && it.enabled && it.isSystem }
                            ?.id
                            ?: mutableState.value.languageInput.keyboards
                                .firstOrNull { it.id != id && it.enabled }
                                ?.id
                    if (newDefault != null) {
                        Settings.Secure.putString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD, newDefault)
                    }
                }
                refreshState()
            }

        override suspend fun setTextToSpeechEngine(packageName: String): ActionResult =
            execute {
                val engines = mutableState.value.languageInput.textToSpeech.engines
                check(engines.any { it.packageName == packageName }) { "This text-to-speech engine is not installed" }
                check(Settings.Secure.putString(resolver, Settings.Secure.TTS_DEFAULT_SYNTH, packageName)) {
                    "The system did not allow changing the text-to-speech engine"
                }
                refreshState()
            }

        override suspend fun setTextToSpeechPlayback(
            speechRate: Int,
            pitch: Int,
        ): ActionResult =
            execute {
                check(speechRate in TTS_MIN_RATE..TTS_MAX_RATE) { "Speech rate is outside the supported range" }
                check(pitch in TTS_MIN_PITCH..TTS_MAX_PITCH) { "Voice pitch is outside the supported range" }
                check(Settings.Secure.putInt(resolver, Settings.Secure.TTS_DEFAULT_RATE, speechRate)) {
                    "The system did not allow changing speech rate"
                }
                check(Settings.Secure.putInt(resolver, Settings.Secure.TTS_DEFAULT_PITCH, pitch)) {
                    "The system did not allow changing voice pitch"
                }
                refreshState()
            }

        override suspend fun speakTextToSpeechSample(): ActionResult =
            execute {
                val playback = mutableState.value.languageInput.textToSpeech
                check(playback.currentEngine != null) { "No text-to-speech engine is installed" }
                var engine: TextToSpeech? = null
                engine =
                    TextToSpeech(context) { status ->
                        val initializedEngine = engine ?: return@TextToSpeech
                        if (status != TextToSpeech.SUCCESS) {
                            initializedEngine.shutdown()
                            return@TextToSpeech
                        }
                        initializedEngine.setOnUtteranceProgressListener(
                            object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String) = Unit

                                override fun onDone(utteranceId: String) {
                                    if (utteranceId == TTS_SAMPLE_ID) initializedEngine.shutdown()
                                }

                                @Deprecated("Deprecated in Java")
                                override fun onError(utteranceId: String) {
                                    if (utteranceId == TTS_SAMPLE_ID) initializedEngine.shutdown()
                                }
                            },
                        )
                        initializedEngine.setSpeechRate(playback.speechRate / 100f)
                        initializedEngine.setPitch(playback.pitch / 100f)
                        initializedEngine.speak(
                            "This is a text to speech sample.",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            TTS_SAMPLE_ID,
                        )
                    }
            }

        override suspend fun setVehicleUnit(
            propertyId: Int,
            unitId: Int,
        ): ActionResult =
            execute {
                val setting =
                    mutableState.value.units.settings
                        .firstOrNull { it.propertyId == propertyId }
                        ?: error("This vehicle unit is not available")
                check(setting.supported.any { it.id == unitId }) { "Unsupported unit" }
                when (
                    val result =
                        vehiclePropertyClient
                            .setInt(propertyId, AREA_ID, unitId)
                            .first { it is VehiclePropertyWriteResult.Confirmed || it is VehiclePropertyWriteResult.Error }
                ) {
                    is VehiclePropertyWriteResult.Confirmed -> Unit
                    is VehiclePropertyWriteResult.Error ->
                        error("Unable to change vehicle unit: ${result.error.description}")
                    is VehiclePropertyWriteResult.Pending -> error("Vehicle unit write did not complete")
                }
                refreshState()
            }

        override suspend fun launchExternal(actionId: SystemExternalActionId): ActionResult =
            execute {
                val allActions = mutableState.value.allExternalActions()
                val action =
                    allActions.firstOrNull { it.id == actionId }
                        ?: error("This system page is not available on this device")
                context.startActivity(
                    Intent()
                        .setClassName(action.packageName, action.className)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }

        override suspend fun restartSystem(): ActionResult =
            execute {
                check(mutableState.value.reset.canRestartSystem) { "Restart is not available on this device" }
                requireNotNull(powerManager).reboot(null)
            }

        override suspend fun resetNetwork(
            subscriptionId: Int?,
            eraseEsim: Boolean,
        ): ActionResult =
            execute {
                check(mutableState.value.reset.canResetNetwork) { "Network reset is restricted for this profile" }
                connectivityManager?.callHidden("factoryReset")
                wifiManager?.callHidden("factoryReset")
                bluetoothManager?.adapter?.callHidden("clearBluetooth")

                val selectedSubscription = subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
                telephonyManager?.callHidden(
                    "factoryReset",
                    arrayOf(Int::class.javaPrimitiveType!!),
                    selectedSubscription,
                )
                val subscriberId =
                    if (isUsableSubscriptionId(selectedSubscription)) {
                        runCatching { telephonyManager?.createForSubscriptionId(selectedSubscription)?.subscriberId }.getOrNull()
                    } else {
                        null
                    }
                networkPolicyManager?.callHidden("factoryReset", arrayOf(String::class.java), subscriberId)
                restoreDefaultApn(selectedSubscription)
                clearSmsRawTable()
                if (eraseEsim) {
                    check(mutableState.value.reset.eraseEsimAvailable) { "eSIM reset is not available" }
                    check(wipeEuiccData()) { "The system could not erase eSIM data" }
                }
                refreshState()
            }

        override suspend fun resetAppPreferences(): ActionResult =
            execute {
                check(mutableState.value.reset.canResetAppPreferences) {
                    "Resetting app preferences is restricted by the administrator"
                }
                check(SystemHiddenApiBridge.resetApplicationPreferences(context)) {
                    "The system did not allow resetting app preferences"
                }
            }

        override suspend fun factoryReset(eraseEsim: Boolean): ActionResult =
            execute {
                check(mutableState.value.reset.canFactoryReset) { "Factory reset is restricted for this profile" }
                context.sendBroadcast(
                    Intent(ACTION_FACTORY_RESET)
                        .setPackage("android")
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        .putExtra(EXTRA_REASON, "MySystemAppFactoryReset")
                        .putExtra(EXTRA_WIPE_ESIMS, eraseEsim),
                )
            }

        private suspend fun refreshState() {
            val about = loadAbout()
            val legal = loadLegal()
            val reset = loadResetOptions()
            val developerEnabled = developerOptionsEnabled()
            if (developerEnabled) developerTapsRemaining = -1
            mutableState.value =
                SystemSettingsState(
                    about = about,
                    legal = legal,
                    reset = reset,
                    systemUpdate =
                        if (isAdminUser()) {
                            findSystemAction(SystemExternalActionId.SYSTEM_UPDATE, Intent(ACTION_SYSTEM_UPDATE_SETTINGS))
                        } else {
                            null
                        },
                    developerOptions =
                        if (developerEnabled) {
                            findSystemAction(
                                SystemExternalActionId.DEVELOPER_OPTIONS,
                                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                            )
                        } else {
                            null
                        },
                    developerOptionsEnabled = developerEnabled,
                    developerTapsRemaining = developerTapsRemaining,
                    languageInput = loadLanguageInput(),
                    units = loadVehicleUnits(),
                    storage = loadStorageInfo(),
                )
        }

        private fun loadLanguageInput(): LanguageInputInfo {
            val currentLocale = Locale.getDefault()
            return LanguageInputInfo(
                currentLocaleTag = currentLocale.toLanguageTag(),
                currentLocaleName = currentLocale.getDisplayName(currentLocale),
                canConfigureLocale = userManager?.hasUserRestriction(UserManager.DISALLOW_CONFIG_LOCALE) != true,
                availableLocales = availableLocales,
                keyboards = loadKeyboards(),
                autofill = loadAutofill(),
                textToSpeech = loadTextToSpeech(),
            )
        }

        private fun loadKeyboards(): List<KeyboardInfo> {
            val enabledIds = enabledInputMethodTokens().map { it.substringBefore(';') }.toSet()
            val defaultId = Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            val inputMethods = runCatching { inputMethodManager?.inputMethodList.orEmpty() }.getOrDefault(emptyList())
            val enabledSystemMethods =
                inputMethods.filter {
                    it.id in enabledIds && it.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                }
            return inputMethods
                .filterNot { it.packageName in GOOGLE_VOICE_TYPING_PACKAGES }
                .map { inputMethod ->
                    val enabled = inputMethod.id in enabledIds
                    val canDisable =
                        !enabled ||
                            inputMethod.id != defaultId ||
                            enabledSystemMethods.any { it.id != inputMethod.id }
                    val subtypeSummary =
                        runCatching {
                            inputMethodManager
                                ?.getEnabledInputMethodSubtypeList(inputMethod, true)
                                .orEmpty()
                                .map {
                                    it
                                        .getDisplayName(
                                            context,
                                            inputMethod.packageName,
                                            inputMethod.serviceInfo.applicationInfo,
                                        ).toString()
                                }.filter(String::isNotBlank)
                                .distinct()
                                .joinToString(", ")
                        }.getOrDefault("")
                    KeyboardInfo(
                        id = inputMethod.id,
                        label = inputMethod.loadLabel(packageManager).toString(),
                        summary = subtypeSummary.ifBlank { if (inputMethod.id == defaultId) "Default keyboard" else "" },
                        enabled = enabled,
                        canDisable = canDisable,
                        isSystem = inputMethod.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    )
                }.sortedWith(compareBy<KeyboardInfo> { it.label.lowercase(Locale.getDefault()) }.thenBy { it.id })
        }

        private fun enabledInputMethodTokens(): List<String> =
            Settings.Secure
                .getString(resolver, Settings.Secure.ENABLED_INPUT_METHODS)
                .orEmpty()
                .split(IME_DELIMITER)
                .filter(String::isNotBlank)

        private fun loadAutofill(): AutofillInfo {
            val supported = runCatching { autofillManager?.isAutofillSupported == true }.getOrDefault(false)
            if (!supported) return AutofillInfo()
            val candidates =
                packageManager
                    .queryIntentServices(
                        Intent(AutofillService.SERVICE_INTERFACE),
                        PackageManager.GET_META_DATA,
                    ).mapNotNull { resolveInfo ->
                        val service = resolveInfo.serviceInfo ?: return@mapNotNull null
                        if (service.permission != android.Manifest.permission.BIND_AUTOFILL_SERVICE) return@mapNotNull null
                        AutofillServiceOption(
                            componentName = ComponentName(service.packageName, service.name).flattenToString(),
                            label =
                                resolveInfo
                                    .loadLabel(packageManager)
                                    ?.toString()
                                    .orEmpty()
                                    .ifBlank { service.packageName },
                        )
                    }.sortedBy { it.label.lowercase(Locale.getDefault()) }
            val selected = Settings.Secure.getString(resolver, AUTOFILL_SERVICE_SETTING)
            return AutofillInfo(
                supported = true,
                currentService = candidates.firstOrNull { it.componentName == selected },
                candidates = candidates,
            )
        }

        private fun loadTextToSpeech(): TextToSpeechInfo {
            val engines =
                packageManager
                    .queryIntentServices(
                        Intent(ACTION_TTS_SERVICE),
                        PackageManager.GET_META_DATA,
                    ).mapNotNull { resolveInfo ->
                        val service = resolveInfo.serviceInfo ?: return@mapNotNull null
                        TextToSpeechEngine(
                            packageName = service.packageName,
                            label =
                                resolveInfo
                                    .loadLabel(packageManager)
                                    ?.toString()
                                    .orEmpty()
                                    .ifBlank { service.packageName },
                        )
                    }.distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase(Locale.getDefault()) }
            val currentPackage = Settings.Secure.getString(resolver, Settings.Secure.TTS_DEFAULT_SYNTH)
            return TextToSpeechInfo(
                currentEngine = engines.firstOrNull { it.packageName == currentPackage } ?: engines.firstOrNull(),
                engines = engines,
                speechRate = Settings.Secure.getInt(resolver, Settings.Secure.TTS_DEFAULT_RATE, TTS_DEFAULT_RATE),
                pitch = Settings.Secure.getInt(resolver, Settings.Secure.TTS_DEFAULT_PITCH, TTS_DEFAULT_PITCH),
            )
        }

        private fun loadAvailableLocales(): List<LocaleOption> {
            val displayLocale = Locale.getDefault()
            return Locale
                .getAvailableLocales()
                .asSequence()
                .filter { it.language.isNotBlank() && it.toLanguageTag() != "und" }
                .map { LocaleOption(it.toLanguageTag(), it.getDisplayName(displayLocale)) }
                .filter { it.name.isNotBlank() }
                .distinctBy { it.languageTag }
                .sortedBy { it.name.lowercase(displayLocale) }
                .toList()
        }

        private suspend fun loadVehicleUnits(): VehicleUnitsInfo {
            return VehicleUnitsInfo(
                settings =
                    UNIT_PROPERTIES.mapNotNull { definition ->
                        val capability =
                            when (
                                val result =
                                    vehiclePropertyClient.capability(
                                        VehiclePropertySpec.int(definition.propertyId),
                                    )
                            ) {
                                is VehiclePropertyResult.Success -> result.value
                                is VehiclePropertyResult.Failure -> return@mapNotNull null
                            }
                        val areaCapability =
                            capability.area(VehiclePropertyArea.GLOBAL)
                                ?: capability.areas.firstOrNull()
                                ?: return@mapNotNull null
                        if (!areaCapability.access.canWrite) return@mapNotNull null
                        val supported =
                            areaCapability.supportedEnumValues
                                .filter { it != VehicleUnit.SHOULD_NOT_USE }
                                .map { UnitOption(it, vehicleUnitLabel(it)) }
                        if (supported.isEmpty()) return@mapNotNull null
                        val currentId =
                            (vehiclePropertyClient.getInt(definition.propertyId, AREA_ID) as? VehiclePropertyResult.Success)
                                ?.value
                                ?.value
                                ?: supported.first().id
                        VehicleUnitSetting(
                            propertyId = definition.propertyId,
                            title = definition.title,
                            current = supported.firstOrNull { it.id == currentId } ?: UnitOption(currentId, vehicleUnitLabel(currentId)),
                            supported = supported,
                        )
                    },
            )
        }

        private fun vehicleUnitLabel(unitId: Int): String =
            when (unitId) {
                VehicleUnit.MILES_PER_HOUR -> "mph"
                VehicleUnit.KILOMETERS_PER_HOUR -> "km/h"
                VehicleUnit.MILE -> "mile"
                VehicleUnit.KILOMETER -> "kilometer"
                VehicleUnit.CELSIUS -> "°C"
                VehicleUnit.FAHRENHEIT -> "°F"
                VehicleUnit.KELVIN -> "K"
                VehicleUnit.LITER -> "L"
                VehicleUnit.MILLILITER -> "mL"
                VehicleUnit.US_GALLON -> "US gal"
                VehicleUnit.IMPERIAL_GALLON -> "Imperial gal"
                VehicleUnit.KILOPASCAL -> "kPa"
                VehicleUnit.PSI -> "psi"
                VehicleUnit.BAR -> "bar"
                VehicleUnit.KILOWATT_HOUR -> "kWh"
                else -> "Unit $unitId"
            }

        private fun loadStorageInfo(): StorageInfo {
            val stats = StatFs(Environment.getDataDirectory().absolutePath)
            val total = stats.blockCountLong * stats.blockSizeLong
            val free = stats.availableBlocksLong * stats.blockSizeLong
            val used = (total - free).coerceAtLeast(0)
            val usage = loadStorageCategories(used)
            return StorageInfo(totalBytes = total, usedBytes = used, freeBytes = free, categories = usage)
        }

        /**
         * Uses the same public storage accounting inputs as AAOS StorageAsyncLoader: per-package
         * default-volume stats plus the current user's external-media stats. Any unavailable provider
         * is treated as zero so the System remainder still represents the entire used partition.
         */
        private fun loadStorageCategories(usedBytes: Long): List<StorageCategory> {
            val statsManager = storageStatsManager ?: return fallbackStorageCategories(usedBytes)
            val user = Process.myUserHandle()
            val appBytes =
                runCatching {
                    packageManager
                        .getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
                        .sumOf { application ->
                            runCatching {
                                statsManager
                                    .queryStatsForPackage(StorageManager.UUID_DEFAULT, application.packageName, user)
                                    .let { it.appBytes + it.dataBytes + it.cacheBytes }
                            }.getOrDefault(0L)
                        }
                }.getOrDefault(0L)
            val external =
                runCatching {
                    statsManager.queryExternalStatsForUser(StorageManager.UUID_DEFAULT, user)
                }.getOrNull()
            val mediaBytes = external?.let { (it.audioBytes + it.videoBytes + it.imageBytes).coerceAtLeast(0L) } ?: 0L
            val filesBytes =
                external?.let {
                    (it.totalBytes - it.appBytes - it.audioBytes - it.videoBytes - it.imageBytes).coerceAtLeast(0L)
                } ?: 0L
            return categorizedStorage(usedBytes, mediaBytes, appBytes, filesBytes)
        }

        private fun fallbackStorageCategories(usedBytes: Long): List<StorageCategory> =
            categorizedStorage(usedBytes, mediaBytes = 0L, appBytes = 0L, filesBytes = 0L)

        private fun categorizedStorage(
            usedBytes: Long,
            mediaBytes: Long,
            appBytes: Long,
            filesBytes: Long,
        ): List<StorageCategory> {
            val attributed = (mediaBytes + appBytes + filesBytes).coerceAtMost(usedBytes)
            return listOf(
                StorageCategory(StorageCategoryId.MUSIC_AND_AUDIO, "Music & audio", mediaBytes.coerceAtMost(usedBytes)),
                StorageCategory(
                    StorageCategoryId.OTHER_APPS,
                    "Other apps",
                    appBytes.coerceAtMost((usedBytes - mediaBytes).coerceAtLeast(0L)),
                ),
                StorageCategory(
                    StorageCategoryId.FILES,
                    "Files",
                    filesBytes.coerceAtMost((usedBytes - mediaBytes - appBytes).coerceAtLeast(0L)),
                ),
                StorageCategory(StorageCategoryId.SYSTEM, "System", (usedBytes - attributed).coerceAtLeast(0L)),
            )
        }

        private fun loadAbout(): AboutInfo =
            AboutInfo(
                hardware =
                    HardwareInfo(
                        model = Build.MODEL.orEmpty(),
                        serialNumber = runCatching { Build.getSerial() }.getOrDefault("Unavailable"),
                        revision = systemProperty("ro.boot.hardware.revision"),
                    ),
                firmwareVersion =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Build.VERSION.RELEASE_OR_CODENAME.orEmpty()
                    } else {
                        Build.VERSION.RELEASE.orEmpty()
                    },
                securityPatch = Build.VERSION.SECURITY_PATCH.orEmpty(),
                kernelVersion = readKernelVersion(),
                buildNumber = Build.DISPLAY.orEmpty(),
                bluetoothMacAddress =
                    if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
                        runCatching { bluetoothManager?.adapter?.address }.getOrNull()
                    } else {
                        null
                    },
                regulatoryInfo =
                    findSystemAction(
                        SystemExternalActionId.REGULATORY_INFO,
                        Intent(Settings.ACTION_SHOW_REGULATORY_INFO),
                    ),
            )

        private fun loadLegal(): LegalInfo =
            LegalInfo(
                terms = findSystemAction(SystemExternalActionId.TERMS, Intent(ACTION_TERMS)),
                webViewLicenses = findSystemAction(SystemExternalActionId.WEBVIEW_LICENSES, Intent(ACTION_WEBVIEW_LICENSE)),
                thirdPartyLicenses =
                    findSystemAction(
                        SystemExternalActionId.THIRD_PARTY_LICENSES,
                        Intent(ACTION_THIRD_PARTY_LICENSE),
                    ),
            )

        private fun loadResetOptions(): ResetOptionsInfo {
            val isAdmin = isAdminUser()
            val canResetNetwork = isAdmin && userManager?.hasUserRestriction(UserManager.DISALLOW_NETWORK_RESET) != true
            val canFactoryReset =
                (isAdmin || userManager?.isDemoUser == true) &&
                    userManager?.hasUserRestriction(UserManager.DISALLOW_FACTORY_RESET) != true
            return ResetOptionsInfo(
                canRestartSystem =
                    powerManager != null &&
                        context.checkSelfPermission(
                            android.Manifest.permission.REBOOT,
                        ) == PackageManager.PERMISSION_GRANTED,
                canResetNetwork = canResetNetwork,
                canResetAppPreferences = userManager?.hasUserRestriction(UserManager.DISALLOW_APPS_CONTROL) != true,
                canFactoryReset = canFactoryReset,
                resettableNetworks =
                    buildList {
                        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) add("Wi‑Fi networks")
                        if (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) add("Mobile networks")
                        if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) add("Bluetooth")
                    },
                subscriptions = loadSubscriptions(),
                eraseEsimAvailable = canEraseEsim(),
                accountsAffected = loadAccountsAffected(),
                otherProfilesPresent = loadOtherProfilesPresent(),
            )
        }

        private fun loadSubscriptions(): List<NetworkSubscription> =
            runCatching {
                subscriptionManager?.activeSubscriptionInfoList.orEmpty().map { info ->
                    NetworkSubscription(info.subscriptionId, info.displayLabel())
                }
            }.getOrDefault(emptyList())

        private fun SubscriptionInfo.displayLabel(): String =
            displayName?.toString()?.takeIf(String::isNotBlank)
                ?: number?.takeIf(String::isNotBlank)
                ?: carrierName?.toString()?.takeIf(String::isNotBlank)
                ?: "SIM ${simSlotIndex + 1}"

        private fun canEraseEsim(): Boolean =
            runCatching {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@runCatching false
                euiccManager?.isEnabled == true &&
                    (Settings.Global.getInt(resolver, EUICC_PROVISIONED, 0) != 0 || developerOptionsEnabled())
            }.getOrDefault(false)

        private fun loadAccountsAffected(): List<String> =
            runCatching {
                accountManager
                    ?.accounts
                    .orEmpty()
                    .map { it.name }
                    .distinct()
                    .sorted()
            }.getOrDefault(emptyList())

        private fun loadOtherProfilesPresent(): Boolean =
            runCatching {
                val aliveUsers = userManager?.callHidden("getAliveUsers") as? List<*> ?: emptyList<Any>()
                val currentUserId = android.os.Process.myUid() / PER_USER_RANGE
                aliveUsers.count { user -> user?.callHidden("getUserHandle")?.callHidden("getIdentifier") != currentUserId } > 0
            }.getOrDefault(false)

        private fun developerOptionsEnabled(): Boolean =
            Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0

        @Suppress("ReturnCount")
        private fun findSystemAction(
            id: SystemExternalActionId,
            probe: Intent,
        ): SystemExternalAction? {
            val resolved =
                packageManager.queryIntentActivities(probe, 0).firstOrNull { info ->
                    info.activityInfo
                        ?.applicationInfo
                        ?.flags
                        ?.and(ApplicationInfo.FLAG_SYSTEM) != 0
                } ?: return null
            val activity = resolved.activityInfo ?: return null
            return SystemExternalAction(
                id = id,
                title =
                    resolved
                        .loadLabel(packageManager)
                        ?.toString()
                        .orEmpty()
                        .ifBlank { activity.name },
                packageName = activity.packageName,
                className = activity.name,
            )
        }

        private fun readKernelVersion(): String =
            runCatching { File("/proc/version").readText().trim() }
                .getOrNull()
                .takeUnless { it.isNullOrBlank() }
                ?: System.getProperty("os.version").orEmpty().ifBlank { "Unavailable" }

        private fun systemProperty(key: String): String =
            runCatching {
                Class
                    .forName("android.os.SystemProperties")
                    .getMethod("get", String::class.java)
                    .invoke(null, key) as? String
            }.getOrNull().orEmpty()

        private fun restoreDefaultApn(subscriptionId: Int) {
            var uri = Uri.parse(RESTORE_CARRIERS_URI)
            if (isUsableSubscriptionId(subscriptionId)) {
                uri = Uri.withAppendedPath(uri, "subId/$subscriptionId")
            }
            resolver.delete(uri, null, null)
        }

        private fun isAdminUser(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && userManager?.isAdminUser == true

        private fun isUsableSubscriptionId(subscriptionId: Int): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                SubscriptionManager.isUsableSubscriptionId(subscriptionId)

        private fun clearSmsRawTable() {
            resolver.delete(Uri.parse("content://sms/raw/permanentDelete"), null, null)
        }

        private fun wipeEuiccData(): Boolean =
            runCatching {
                Class
                    .forName("android.os.RecoverySystem")
                    .getMethod("wipeEuiccData", Context::class.java, String::class.java)
                    .invoke(null, context, context.packageName) as? Boolean
            }.getOrNull() == true

        private suspend fun execute(block: suspend () -> Unit): ActionResult =
            withContext(dispatcher) {
                try {
                    block()
                    ActionResult.Success
                } catch (exception: Exception) {
                    ActionResult.Failure(exception.message ?: "The system operation failed", exception)
                }
            }

        private fun SystemSettingsState.allExternalActions(): List<SystemExternalAction> =
            buildList {
                systemUpdate?.let(::add)
                developerOptions?.let(::add)
                about.regulatoryInfo?.let(::add)
                legal.terms?.let(::add)
                legal.webViewLicenses?.let(::add)
                legal.thirdPartyLicenses?.let(::add)
                addAll(systemExtras)
            }

        private companion object {
            const val ACTION_FACTORY_RESET = "android.intent.action.FACTORY_RESET"
            const val ACTION_SYSTEM_UPDATE_SETTINGS = "android.settings.SYSTEM_UPDATE_SETTINGS"
            const val ACTION_TERMS = "android.settings.TERMS"
            const val ACTION_WEBVIEW_LICENSE = "android.settings.WEBVIEW_LICENSE"
            const val ACTION_THIRD_PARTY_LICENSE = "android.settings.THIRD_PARTY_LICENSE"
            const val RESTORE_CARRIERS_URI = "content://telephony/carriers/restore"
            const val EXTRA_REASON = "android.intent.extra.REASON"
            const val EXTRA_WIPE_ESIMS = "android.intent.extra.WIPE_ESIMS"
            const val EUICC_PROVISIONED = "euicc_provisioned"
            const val PER_USER_RANGE = 100_000
            const val AREA_ID = 0
            const val IME_DELIMITER = ":"
            const val AUTOFILL_SERVICE_SETTING = "autofill_service"
            const val ACTION_TTS_SERVICE = "android.intent.action.TTS_SERVICE"
            const val TTS_MIN_RATE = 10
            const val TTS_MAX_RATE = 600
            const val TTS_MIN_PITCH = 25
            const val TTS_MAX_PITCH = 400
            const val TTS_DEFAULT_RATE = 100
            const val TTS_DEFAULT_PITCH = 100
            const val TTS_SAMPLE_ID = "MySystemAppTtsSample"

            val GOOGLE_VOICE_TYPING_PACKAGES =
                setOf(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.tts",
                )

            val UNIT_PROPERTIES =
                listOf(
                    UnitProperty(VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS, "Speed"),
                    UnitProperty(VehiclePropertyIds.DISTANCE_DISPLAY_UNITS, "Distance"),
                    UnitProperty(VehiclePropertyIds.FUEL_VOLUME_DISPLAY_UNITS, "Fuel consumption"),
                    UnitProperty(VehiclePropertyIds.EV_BATTERY_DISPLAY_UNITS, "Energy consumption"),
                    UnitProperty(VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS, "Temperature"),
                    UnitProperty(VehiclePropertyIds.TIRE_PRESSURE_DISPLAY_UNITS, "Pressure"),
                )
        }
    }

private data class UnitProperty(
    val propertyId: Int,
    val title: String,
)

private fun Any.callHidden(
    methodName: String,
    parameterTypes: Array<Class<*>> = emptyArray(),
    vararg args: Any?,
): Any? =
    try {
        javaClass.getMethod(methodName, *parameterTypes).invoke(this, *args)
    } catch (exception: InvocationTargetException) {
        throw (exception.targetException ?: exception)
    }
