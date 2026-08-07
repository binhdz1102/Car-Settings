package com.android.car.settings.feature.system.domain

/** Snapshot of the Automotive Settings > System hierarchy. */
data class SystemSettingsState(
    val about: AboutInfo = AboutInfo(),
    val legal: LegalInfo = LegalInfo(),
    val reset: ResetOptionsInfo = ResetOptionsInfo(),
    val systemUpdate: SystemExternalAction? = null,
    val developerOptions: SystemExternalAction? = null,
    val developerOptionsEnabled: Boolean = false,
    val developerTapsRemaining: Int = DEVELOPER_TAP_COUNT,
    val languageInput: LanguageInputInfo = LanguageInputInfo(),
    val units: VehicleUnitsInfo = VehicleUnitsInfo(),
    val storage: StorageInfo = StorageInfo(),
    val systemExtras: List<SystemExternalAction> = emptyList(),
)

data class LanguageInputInfo(
    val currentLocaleTag: String = "",
    val currentLocaleName: String = "",
    val canConfigureLocale: Boolean = false,
    val availableLocales: List<LocaleOption> = emptyList(),
    val keyboards: List<KeyboardInfo> = emptyList(),
    val autofill: AutofillInfo = AutofillInfo(),
    val textToSpeech: TextToSpeechInfo = TextToSpeechInfo(),
)

data class LocaleOption(
    val languageTag: String,
    val name: String,
)

/** An installed input method, including its enabled state in Settings.Secure. */
data class KeyboardInfo(
    val id: String,
    val label: String,
    val summary: String = "",
    val enabled: Boolean = false,
    val canDisable: Boolean = true,
    val isSystem: Boolean = false,
)

data class AutofillInfo(
    val supported: Boolean = false,
    val currentService: AutofillServiceOption? = null,
    val candidates: List<AutofillServiceOption> = emptyList(),
)

data class AutofillServiceOption(
    val componentName: String,
    val label: String,
)

/** Mirrors AAOS Text-to-speech output's engine, rate and pitch preferences. */
data class TextToSpeechInfo(
    val currentEngine: TextToSpeechEngine? = null,
    val engines: List<TextToSpeechEngine> = emptyList(),
    val speechRate: Int = 100,
    val pitch: Int = 100,
)

data class TextToSpeechEngine(
    val packageName: String,
    val label: String,
)

data class VehicleUnitsInfo(
    val settings: List<VehicleUnitSetting> = emptyList(),
)

data class VehicleUnitSetting(
    val propertyId: Int,
    val title: String,
    val current: UnitOption,
    val supported: List<UnitOption>,
)

data class UnitOption(
    val id: Int,
    val label: String,
)

data class StorageInfo(
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val categories: List<StorageCategory> = emptyList(),
)

/** The four categories exposed by AAOS StorageSettingsFragment. */
data class StorageCategory(
    val id: StorageCategoryId,
    val title: String,
    val usedBytes: Long,
)

enum class StorageCategoryId {
    MUSIC_AND_AUDIO,
    OTHER_APPS,
    FILES,
    SYSTEM,
}

data class AboutInfo(
    val hardware: HardwareInfo = HardwareInfo(),
    val firmwareVersion: String = "",
    val securityPatch: String = "",
    val kernelVersion: String = "",
    val buildNumber: String = "",
    val bluetoothMacAddress: String? = null,
    val regulatoryInfo: SystemExternalAction? = null,
)

data class HardwareInfo(
    val model: String = "",
    val serialNumber: String = "",
    val revision: String = "",
)

data class LegalInfo(
    val terms: SystemExternalAction? = null,
    val webViewLicenses: SystemExternalAction? = null,
    val thirdPartyLicenses: SystemExternalAction? = null,
)

data class ResetOptionsInfo(
    val canRestartSystem: Boolean = false,
    val canResetNetwork: Boolean = false,
    val canResetAppPreferences: Boolean = false,
    val canFactoryReset: Boolean = false,
    val resettableNetworks: List<String> = emptyList(),
    val subscriptions: List<NetworkSubscription> = emptyList(),
    val eraseEsimAvailable: Boolean = false,
    val accountsAffected: List<String> = emptyList(),
    val otherProfilesPresent: Boolean = false,
)

data class NetworkSubscription(
    val id: Int,
    val label: String,
)

/** A system-image-owned Settings surface intentionally delegated by AAOS Settings. */
data class SystemExternalAction(
    val id: SystemExternalActionId,
    val title: String,
    val packageName: String,
    val className: String,
)

enum class SystemExternalActionId {
    SYSTEM_UPDATE,
    DEVELOPER_OPTIONS,
    REGULATORY_INFO,
    TERMS,
    WEBVIEW_LICENSES,
    THIRD_PARTY_LICENSES,
    EXTRA,
}

const val DEVELOPER_TAP_COUNT = 7
