package com.android.car.settings.feature.search.domain

enum class SearchAvailability {
    AVAILABLE,
    UNAVAILABLE,
}

/** Stable feature-level destinations. The app shell maps these to Compose navigation routes. */
enum class SearchDestination {
    DISPLAY,
    DATE_TIME,
    TIME_ZONE,
    WIFI,
    WIFI_HOTSPOT,
    WIFI_PREFERENCES,
    BLUETOOTH,
    SOUND,
    PHONE_RINGTONE,
    NOTIFICATION_RINGTONE,
    ALARM_RINGTONE,
    APPLICATIONS,
    ALL_APPLICATIONS,
    SPECIAL_APP_ACCESS,
    PERFORMANCE_APPS,
    NOTIFICATIONS,
    PRIVACY,
    PRIVACY_MICROPHONE,
    PRIVACY_CAMERA,
    PRIVACY_LOCATION,
    SECURITY,
    SCREEN_LOCK,
    DEVICE_ADMINS,
    PROFILE_ACCOUNTS,
    PROFILES,
    SYSTEM,
    ABOUT,
    LANGUAGE_INPUT,
    UNITS,
    STORAGE,
    LEGAL,
    RESET_OPTIONS,
    HVAC,
}

data class SettingsSearchResult(
    val key: String,
    val title: String,
    val summary: String,
    val screenTitle: String,
    val destination: SearchDestination,
    val availability: SearchAvailability = SearchAvailability.AVAILABLE,
    val unavailableReason: String? = null,
)
