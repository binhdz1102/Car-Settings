package com.android.car.settings.navigation

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import com.android.car.settings.feature.applications.presentation.ALL_APPLICATIONS_ROUTE
import com.android.car.settings.feature.applications.presentation.APPLICATIONS_ROUTE
import com.android.car.settings.feature.applications.presentation.applicationDetailsRoute
import com.android.car.settings.feature.assistantvoice.presentation.ASSISTANT_VOICE_ROUTE
import com.android.car.settings.feature.accessibility.presentation.ACCESSIBILITY_ROUTE
import com.android.car.settings.feature.bluetooth.presentation.BLUETOOTH_ROUTE
import com.android.car.settings.feature.display.presentation.DATE_TIME_ROUTE
import com.android.car.settings.feature.display.presentation.DISPLAY_ROUTE
import com.android.car.settings.feature.display.presentation.TIME_ZONE_ROUTE
import com.android.car.settings.feature.hvac.presentation.HVAC_ROUTE
import com.android.car.settings.feature.location.presentation.LOCATION_ROUTE
import com.android.car.settings.feature.notifications.presentation.NOTIFICATIONS_ROUTE
import com.android.car.settings.feature.privacy.presentation.PRIVACY_LOCATION_ROUTE
import com.android.car.settings.feature.privacy.presentation.PRIVACY_ROUTE
import com.android.car.settings.feature.profileaccounts.presentation.PROFILE_ACCOUNTS_ROUTE
import com.android.car.settings.feature.profileaccounts.presentation.PROFILES_ROUTE
import com.android.car.settings.feature.search.presentation.SEARCH_ROUTE
import com.android.car.settings.feature.security.presentation.SECURITY_DEVICE_ADMINS_ROUTE
import com.android.car.settings.feature.security.presentation.SECURITY_ROUTE
import com.android.car.settings.feature.security.presentation.SECURITY_LOCK_TYPES_ROUTE
import com.android.car.settings.feature.sound.domain.RingtoneKind
import com.android.car.settings.feature.sound.presentation.SOUND_ROUTE
import com.android.car.settings.feature.sound.presentation.ringtoneRoute
import com.android.car.settings.feature.system.presentation.LANGUAGE_INPUT_ROUTE
import com.android.car.settings.feature.system.presentation.LEGAL_ROUTE
import com.android.car.settings.feature.system.presentation.RESET_OPTIONS_ROUTE
import com.android.car.settings.feature.system.presentation.STORAGE_ROUTE
import com.android.car.settings.feature.system.presentation.SYSTEM_ABOUT_ROUTE
import com.android.car.settings.feature.system.presentation.SYSTEM_ROUTE
import com.android.car.settings.feature.system.presentation.UNITS_ROUTE
import com.android.car.settings.feature.wifi.presentation.MOBILE_NETWORK_ROUTE
import com.android.car.settings.feature.wifi.presentation.WIFI_HOTSPOT_ROUTE
import com.android.car.settings.feature.wifi.presentation.WIFI_PREFERENCES_ROUTE
import com.android.car.settings.feature.wifi.presentation.WIFI_ROUTE
import timber.log.Timber

/** Maps the public AAOS Settings contract to this app's Compose destinations. */
object SettingsIntentRouter {
    const val HOME_ROUTE = "home"

    fun destinationFor(intent: Intent?): String {
        val packageFromData = intent?.data?.takeIf { it.scheme == URI_SCHEME_PACKAGE }?.schemeSpecificPart
        return destinationFor(
            action = intent?.action,
            componentClassName = intent?.component?.className,
            dataPackage = packageFromData,
            packageExtra = intent?.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
                ?: intent?.getStringExtra(EXTRA_APP_PACKAGE),
            ringtoneType = intent?.getIntExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_RINGTONE,
            ) ?: RingtoneManager.TYPE_RINGTONE,
        )
    }

    internal fun destinationFor(
        action: String?,
        componentClassName: String? = null,
        dataPackage: String? = null,
        packageExtra: String? = null,
        ringtoneType: Int = RingtoneManager.TYPE_RINGTONE,
    ): String {
        componentDestination(componentClassName, dataPackage, packageExtra, ringtoneType)?.let {
            Timber.d("Routing component %s to %s", componentClassName, it)
            return it
        }
        action ?: return HOME_ROUTE
        return when {
            action == ACTION_SETTINGS || action == ACTION_HOME_SETTINGS -> HOME_ROUTE

            action == Settings.ACTION_WIFI_SETTINGS ||
                action == ACTION_WIFI_PANEL ||
                action == ACTION_PICK_WIFI_NETWORK ||
                action == ACTION_PROCESS_WIFI_EASY_CONNECT_URI ||
                action == ACTION_INTERNET_CONNECTIVITY_PANEL ||
                action == ACTION_WIRELESS_SETTINGS ||
                action == ACTION_WIFI_ADD_NETWORKS -> WIFI_ROUTE
            action == Settings.ACTION_WIFI_IP_SETTINGS -> WIFI_PREFERENCES_ROUTE
            action == ACTION_WIFI_TETHER_SETTINGS -> WIFI_HOTSPOT_ROUTE
            action == Settings.ACTION_NETWORK_OPERATOR_SETTINGS -> MOBILE_NETWORK_ROUTE

            action == Settings.ACTION_BLUETOOTH_SETTINGS ||
                action == ACTION_BLUETOOTH_PAIRING_SETTINGS -> BLUETOOTH_ROUTE

            action == Settings.ACTION_SOUND_SETTINGS || action == ACTION_VOLUME_PANEL -> SOUND_ROUTE
            action == ACTION_RINGTONE_PICKER -> ringtoneRoute(ringtoneKind(ringtoneType))

            action == Settings.ACTION_DISPLAY_SETTINGS -> DISPLAY_ROUTE
            action == Settings.ACTION_DATE_SETTINGS || action == ACTION_QUICK_CLOCK -> DATE_TIME_ROUTE
            action == ACTION_TIMEZONE_SETTINGS -> TIME_ZONE_ROUTE

            action == Settings.ACTION_APPLICATION_SETTINGS -> APPLICATIONS_ROUTE
            action == Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS ||
                action == Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS -> ALL_APPLICATIONS_ROUTE
            action in APPLICATION_DETAIL_ACTIONS -> {
                packageName(dataPackage, packageExtra)?.let(::applicationDetailsRoute) ?: APPLICATIONS_ROUTE
            }
            action == Settings.ACTION_APP_SEARCH_SETTINGS -> SEARCH_ROUTE
            action == Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS -> APPLICATIONS_ROUTE
            action in SPECIAL_APP_ACCESS_ACTIONS ->
                com.android.car.settings.feature.applications.presentation.SPECIAL_APP_ACCESS_ROUTE

            action == ACTION_NOTIFICATION_SETTINGS ||
                action == ACTION_ALL_APPS_NOTIFICATION_SETTINGS ||
                action == ACTION_VOICE_CONTROL_DO_NOT_DISTURB_MODE ||
                action == ACTION_AUTOMATIC_ZEN_RULE_SETTINGS ||
                action == ACTION_ZEN_MODE_PRIORITY_SETTINGS ||
                action == Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS ||
                action == ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS -> NOTIFICATIONS_ROUTE

            action == Settings.ACTION_PRIVACY_SETTINGS -> PRIVACY_ROUTE
            action == Settings.ACTION_LOCATION_SOURCE_SETTINGS -> LOCATION_ROUTE

            action == Settings.ACTION_ACCESSIBILITY_SETTINGS -> ACCESSIBILITY_ROUTE

            action == Settings.ACTION_SECURITY_SETTINGS || action == ACTION_BIOMETRIC_ENROLL -> SECURITY_ROUTE
            action == ACTION_DEVICE_ADMIN_SETTINGS -> SECURITY_DEVICE_ADMINS_ROUTE

            action == ACTION_USER_SETTINGS -> PROFILES_ROUTE
            action == Settings.ACTION_SYNC_SETTINGS -> PROFILE_ACCOUNTS_ROUTE
            action == ACTION_ADD_ACCOUNT_SETTINGS -> PROFILE_ACCOUNTS_ROUTE

            action == Settings.ACTION_DEVICE_INFO_SETTINGS || action == ACTION_DEVICE_NAME ->
                SYSTEM_ABOUT_ROUTE
            action == Settings.ACTION_INTERNAL_STORAGE_SETTINGS || action == ACTION_MANAGE_STORAGE ->
                STORAGE_ROUTE
            action == Settings.ACTION_REGIONAL_PREFERENCES_SETTINGS ||
                action == Settings.ACTION_MEASUREMENT_SYSTEM_SETTINGS ||
                action == ACTION_TEMPERATURE_UNIT_SETTINGS -> UNITS_ROUTE
            action == Settings.ACTION_LOCALE_SETTINGS ||
                action == Settings.ACTION_INPUT_METHOD_SETTINGS ||
                action == Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS ||
                action == ACTION_REQUEST_SET_AUTOFILL_SERVICE ||
                action == ACTION_TEXT_TO_SPEECH_SETTINGS -> LANGUAGE_INPUT_ROUTE
            action == Settings.ACTION_VOICE_INPUT_SETTINGS -> ASSISTANT_VOICE_ROUTE
            action == ACTION_LEGAL_INFORMATION_SETTINGS -> LEGAL_ROUTE
            action == ACTION_RESET_SETTINGS -> RESET_OPTIONS_ROUTE
            action == ACTION_SYSTEM_UPDATE_SETTINGS || action == ACTION_REGION_SETTINGS -> SYSTEM_ROUTE
            action == ACTION_HVAC_SETTINGS -> HVAC_ROUTE

            else -> HOME_ROUTE
        }
    }

    private fun ringtoneKind(type: Int): RingtoneKind =
        when (type) {
            RingtoneManager.TYPE_NOTIFICATION -> RingtoneKind.NOTIFICATION
            RingtoneManager.TYPE_ALARM -> RingtoneKind.ALARM
            else -> RingtoneKind.PHONE
        }

    private fun packageName(dataPackage: String?, packageExtra: String?): String? =
        dataPackage ?: packageExtra

    private fun componentDestination(
        componentClassName: String?,
        dataPackage: String?,
        packageExtra: String?,
        ringtoneType: Int,
    ): String? {
        val activityName = componentClassName?.substringAfterLast('$') ?: return null
        return when (activityName) {
            "HomepageActivity" -> HOME_ROUTE
            "DisplaySettingsActivity" -> DISPLAY_ROUTE
            "SoundSettingsActivity" -> SOUND_ROUTE
            "RingtonePickerActivity" -> ringtoneRoute(ringtoneKind(ringtoneType))
            "NetworkAndInternetActivity", "WifiSettingsActivity", "WifiControlActivity",
            "AddWifiActivity" -> WIFI_ROUTE
            "WifiTetherActivity" -> WIFI_HOTSPOT_ROUTE
            "WifiPreferencesActivity" -> WIFI_PREFERENCES_ROUTE
            "BluetoothSettingsActivity" -> BLUETOOTH_ROUTE
            "UnitsSettingsActivity" -> UNITS_ROUTE
            "LocationSettingsActivity", "VehicleDataActivity" -> LOCATION_ROUTE
            "AccessibilitySettingsActivity" -> ACCESSIBILITY_ROUTE
            "AppsActivity" -> APPLICATIONS_ROUTE
            "ApplicationsSettingsActivity" -> ALL_APPLICATIONS_ROUTE
            "ApplicationsDetailsActivity", "AppAspectRatioActivity" ->
                packageName(dataPackage, packageExtra)?.let(::applicationDetailsRoute) ?: APPLICATIONS_ROUTE
            "NotificationsActivity", "NotificationAccessActivity" -> NOTIFICATIONS_ROUTE
            "DatetimeSettingsActivity" -> DATE_TIME_ROUTE
            "ProfileDetailsActivity" -> PROFILES_ROUTE
            "PrivacySettingsActivity" -> PRIVACY_ROUTE
            "StorageSettingsActivity" -> STORAGE_ROUTE
            "SecuritySettingsActivity" -> SECURITY_ROUTE
            "AssistantAndVoiceSettingsActivity" -> ASSISTANT_VOICE_ROUTE
            "LanguagesAndInputActivity",
            "LanguagePickerActivity", "DefaultAutofillPickerActivity", "KeyboardActivity",
            "TextToSpeechOutputActivity" -> LANGUAGE_INPUT_ROUTE
            "AboutSettingsActivity" -> SYSTEM_ABOUT_ROUTE
            "LegalInformationActivity" -> LEGAL_ROUTE
            "ResetOptionsActivity" -> RESET_OPTIONS_ROUTE
            "SystemSettingsActivity" -> SYSTEM_ROUTE
            "MobileNetworkActivity", "MobileNetworkListActivity" -> MOBILE_NETWORK_ROUTE
            "SpecialAccessSettingsActivity", "ModifySystemSettingsActivity",
            "PremiumSmsAccessActivity", "UsageAccessActivity", "AlarmsAndRemindersActivity" ->
                com.android.car.settings.feature.applications.presentation.SPECIAL_APP_ACCESS_ROUTE
            "ChooseAccountActivity" -> PROFILE_ACCOUNTS_ROUTE
            else -> null
        }
    }
}

private const val ACTION_HOME_SETTINGS = "android.settings.HOME_SETTINGS"
private const val ACTION_SETTINGS = "android.settings.SETTINGS"
private const val ACTION_WIFI_PANEL = "android.settings.panel.action.WIFI"
private const val ACTION_INTERNET_CONNECTIVITY_PANEL =
    "android.settings.panel.action.INTERNET_CONNECTIVITY"
private const val ACTION_WIRELESS_SETTINGS = "android.settings.WIRELESS_SETTINGS"
private const val ACTION_PICK_WIFI_NETWORK = "android.net.wifi.PICK_WIFI_NETWORK"
private const val ACTION_PROCESS_WIFI_EASY_CONNECT_URI = "android.settings.PROCESS_WIFI_EASY_CONNECT_URI"
private const val ACTION_WIFI_ADD_NETWORKS = "android.settings.WIFI_ADD_NETWORKS"
private const val ACTION_WIFI_TETHER_SETTINGS = "com.android.settings.WIFI_TETHER_SETTINGS"
private const val ACTION_BLUETOOTH_PAIRING_SETTINGS = "android.settings.BLUETOOTH_PAIRING_SETTINGS"
private const val ACTION_VOLUME_PANEL = "android.settings.panel.action.VOLUME"
private const val ACTION_QUICK_CLOCK = "android.intent.action.QUICK_CLOCK"
private const val ACTION_TIMEZONE_SETTINGS = "android.settings.TIMEZONE_SETTINGS"
private const val ACTION_RINGTONE_PICKER = "android.intent.action.RINGTONE_PICKER"
private const val ACTION_NOTIFICATION_SETTINGS = "android.settings.NOTIFICATION_SETTINGS"
private const val ACTION_USER_SETTINGS = "android.settings.USER_SETTINGS"
private const val ACTION_ADD_ACCOUNT_SETTINGS = "android.settings.ADD_ACCOUNT_SETTINGS"
private const val ACTION_SYSTEM_UPDATE_SETTINGS = "android.settings.SYSTEM_UPDATE_SETTINGS"
private const val ACTION_ALL_APPS_NOTIFICATION_SETTINGS = "android.settings.ALL_APPS_NOTIFICATION_SETTINGS"
private const val ACTION_VOICE_CONTROL_DO_NOT_DISTURB_MODE =
    "android.settings.VOICE_CONTROL_DO_NOT_DISTURB_MODE"
private const val ACTION_AUTOMATIC_ZEN_RULE_SETTINGS = "android.settings.AUTOMATIC_ZEN_RULE_SETTINGS"
private const val ACTION_ZEN_MODE_PRIORITY_SETTINGS = "android.settings.ZEN_MODE_PRIORITY_SETTINGS"
private const val ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS = "android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS"
private const val ACTION_BIOMETRIC_ENROLL = "android.settings.BIOMETRIC_ENROLL"
private const val ACTION_DEVICE_ADMIN_SETTINGS = "android.settings.DEVICE_ADMIN_SETTINGS"
private const val ACTION_DEVICE_NAME = "android.settings.DEVICE_NAME"
private const val ACTION_MANAGE_STORAGE = "android.os.storage.action.MANAGE_STORAGE"
private const val ACTION_TEMPERATURE_UNIT_SETTINGS = "android.settings.TEMPERATURE_UNIT_SETTINGS"
private const val ACTION_TEXT_TO_SPEECH_SETTINGS = "com.android.settings.TTS_SETTINGS"
private const val ACTION_REQUEST_SET_AUTOFILL_SERVICE =
    "android.settings.REQUEST_SET_AUTOFILL_SERVICE"
private const val ACTION_LEGAL_INFORMATION_SETTINGS = "android.settings.SHOW_REGULATORY_INFO"
private const val ACTION_RESET_SETTINGS = "android.settings.RESET_SETTINGS"
private const val ACTION_REGION_SETTINGS = "android.settings.REGION_SETTINGS"
private const val ACTION_HVAC_SETTINGS = "android.settings.HVAC_SETTINGS"
private const val EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE"
private const val URI_SCHEME_PACKAGE = "package"
private const val ACTION_MANAGE_USER_ASPECT_RATIO_SETTINGS =
    "android.settings.MANAGE_USER_ASPECT_RATIO_SETTINGS"

private val APPLICATION_DETAIL_ACTIONS = setOf(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS,
    Settings.ACTION_APP_NOTIFICATION_SETTINGS,
    Intent.ACTION_AUTO_REVOKE_PERMISSIONS,
    ACTION_MANAGE_USER_ASPECT_RATIO_SETTINGS,
)

    private val SPECIAL_APP_ACCESS_ACTIONS = setOf(
        Settings.ACTION_USAGE_ACCESS_SETTINGS,
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
    )
