package com.android.car.settings.core.settings

enum class SettingsFeatureId {
    SEARCH,
    DISPLAY,
    WIFI,
    BLUETOOTH,
    SOUND,
    DRIVER_ASSISTANCE,
    SEAT_CONTROL,
    VEHICLE_LIGHTING,
    DOOR_CONTROL,
    HVAC,
    LAUNCHER,
    APPLICATIONS,
    NOTIFICATIONS,
    PRIVACY,
    LOCATION,
    ACCESSIBILITY,
    ASSISTANT_VOICE,
    SECURITY,
    PROFILE_ACCOUNTS,
    SYSTEM,
    SYSTEM_UI,
}

/** Stable identity for the persistent two-pane Settings rail. */
enum class SettingsCategoryId {
    VEHICLE,
    CONNECTED_DEVICES,
    NETWORK_INTERNET,
    NOTIFICATIONS,
    SOUND,
    DISPLAY,
    PROFILE,
    LOCATION,
    PRIVACY,
    ACCESSIBILITY,
    SECURITY,
    APPS,
    ASSISTANCE_VOICE,
    SYSTEM,
}

enum class SettingsDestinationId {
    HOME,
    SEARCH,
    DISPLAY,
    DATE_TIME,
    TIME_ZONE,
    NETWORK_INTERNET,
    MOBILE_NETWORK,
    WIFI,
    WIFI_HOTSPOT,
    WIFI_PREFERENCES,
    WIFI_QR,
    WIFI_NETWORK_DETAILS,
    BLUETOOTH,
    BLUETOOTH_DEVICE_DETAILS,
    SOUND,
    PHONE_RINGTONE,
    NOTIFICATION_RINGTONE,
    ALARM_RINGTONE,
    APPLICATIONS,
    ALL_APPLICATIONS,
    APPLICATION_DETAILS,
    APPLICATION_STORAGE,
    APPLICATION_PERMISSIONS,
    PERMISSION_GROUP,
    APPLICATION_PERMISSIONS_DETAIL,
    UNUSED_APPLICATIONS,
    DEFAULT_APPLICATIONS,
    OPENING_LINKS,
    SPECIAL_APP_ACCESS,
    SPECIAL_ACCESS_LIST,
    PERFORMANCE_APPS,
    NOTIFICATIONS,
    NOTIFICATION_APP_DETAILS,
    PRIVACY,
    PRIVACY_MICROPHONE,
    PRIVACY_CAMERA,
    PRIVACY_LOCATION,
    PRIVACY_PERMISSION_APPS,
    LOCATION,
    LOCATION_APPS,
    ACCESSIBILITY,
    ASSISTANT_VOICE,
    SECURITY,
    SCREEN_LOCK,
    SCREEN_LOCK_SETUP,
    DEVICE_ADMINS,
    PROFILE_ACCOUNTS,
    PROFILES,
    ADD_PROFILE,
    PROFILE_DETAILS,
    ADD_ACCOUNT,
    ACCOUNT_DETAILS,
    SYSTEM,
    ABOUT,
    ABOUT_HARDWARE,
    LANGUAGE_INPUT,
    LANGUAGE_PICKER,
    AUTOFILL,
    KEYBOARD,
    TEXT_TO_SPEECH,
    UNITS,
    STORAGE,
    LEGAL,
    RESET_OPTIONS,
    RESET_NETWORK,
    RESET_APP_PREFERENCES,
    FACTORY_RESET,
    HVAC,
    DRIVER_ASSISTANCE,
    SEAT_CONTROL,
    VEHICLE_LIGHTING,
    DOOR_CONTROL,
    VEHICLE,
    LAUNCHER,
    SYSTEM_UI_CATALOG,
    SYSTEM_UI_COCKPIT,
    SYSTEM_UI_NOTIFICATION_CENTER,
    SYSTEM_UI_USER_CENTER,
}

enum class SettingsCapability {
    WIFI,
    BLUETOOTH,
    VEHICLE_SERVICE,
    SENSOR_PRIVACY,
    CREDENTIAL_STORAGE,
    SYSTEM_UI,
}

/** Safety classification applied at every app-shell navigation entry point. */
enum class UxSafetyClass {
    ALWAYS_SAFE,
    GLANCEABLE,
    PARKED_ONLY,
}

data class SettingsDestinationDefinition(
    val id: SettingsDestinationId,
    val route: String?,
    val uxSafetyClass: UxSafetyClass,
    val capabilities: Set<SettingsCapability> = emptySet(),
    val intentActions: Set<String> = emptySet(),
    val categoryId: SettingsCategoryId = categoryForDestination(id),
)

data class SettingsFeatureDefinition(
    val id: SettingsFeatureId,
    val rootDestination: SettingsDestinationId,
    val categoryId: SettingsCategoryId = categoryForDestination(rootDestination),
)

data class SettingsCategoryDefinition(
    val id: SettingsCategoryId,
    val rootDestination: SettingsDestinationId,
)

/**
 * Canonical destination registry shared by Home, search, Android intents and test deep links.
 * Feature navigation graphs still own their composables; this registry owns identity and policy.
 */
object DefaultSettingsRegistry {
    const val EXTRA_START_DESTINATION = "com.android.car.settings.extra.START_DESTINATION"

    val features: List<SettingsFeatureDefinition> =
        listOf(
            feature(SettingsFeatureId.HVAC, SettingsDestinationId.HVAC),
            feature(SettingsFeatureId.DRIVER_ASSISTANCE, SettingsDestinationId.DRIVER_ASSISTANCE),
            feature(SettingsFeatureId.SEAT_CONTROL, SettingsDestinationId.SEAT_CONTROL),
            feature(SettingsFeatureId.DOOR_CONTROL, SettingsDestinationId.DOOR_CONTROL),
            feature(SettingsFeatureId.VEHICLE_LIGHTING, SettingsDestinationId.VEHICLE_LIGHTING),
            feature(SettingsFeatureId.SEARCH, SettingsDestinationId.SEARCH),
            feature(SettingsFeatureId.DISPLAY, SettingsDestinationId.DISPLAY),
            feature(SettingsFeatureId.WIFI, SettingsDestinationId.WIFI),
            feature(SettingsFeatureId.BLUETOOTH, SettingsDestinationId.BLUETOOTH),
            feature(SettingsFeatureId.SOUND, SettingsDestinationId.SOUND),
            feature(SettingsFeatureId.LAUNCHER, SettingsDestinationId.LAUNCHER),
            feature(SettingsFeatureId.APPLICATIONS, SettingsDestinationId.APPLICATIONS),
            feature(SettingsFeatureId.NOTIFICATIONS, SettingsDestinationId.NOTIFICATIONS),
            feature(SettingsFeatureId.PRIVACY, SettingsDestinationId.PRIVACY),
            feature(SettingsFeatureId.LOCATION, SettingsDestinationId.LOCATION),
            feature(SettingsFeatureId.ACCESSIBILITY, SettingsDestinationId.ACCESSIBILITY),
            feature(SettingsFeatureId.ASSISTANT_VOICE, SettingsDestinationId.ASSISTANT_VOICE),
            feature(SettingsFeatureId.SECURITY, SettingsDestinationId.SECURITY),
            feature(SettingsFeatureId.PROFILE_ACCOUNTS, SettingsDestinationId.PROFILE_ACCOUNTS),
            feature(SettingsFeatureId.SYSTEM, SettingsDestinationId.SYSTEM),
            feature(SettingsFeatureId.SYSTEM_UI, SettingsDestinationId.SYSTEM_UI_CATALOG),
        )

    /** Rail order is intentionally independent from feature registration order. */
    val categories: List<SettingsCategoryDefinition> =
        listOf(
            category(SettingsCategoryId.VEHICLE, SettingsDestinationId.VEHICLE),
            category(SettingsCategoryId.CONNECTED_DEVICES, SettingsDestinationId.BLUETOOTH),
            category(SettingsCategoryId.NETWORK_INTERNET, SettingsDestinationId.NETWORK_INTERNET),
            category(SettingsCategoryId.NOTIFICATIONS, SettingsDestinationId.NOTIFICATIONS),
            category(SettingsCategoryId.SOUND, SettingsDestinationId.SOUND),
            category(SettingsCategoryId.DISPLAY, SettingsDestinationId.DISPLAY),
            category(SettingsCategoryId.PROFILE, SettingsDestinationId.PROFILE_ACCOUNTS),
            category(SettingsCategoryId.LOCATION, SettingsDestinationId.LOCATION),
            category(SettingsCategoryId.PRIVACY, SettingsDestinationId.PRIVACY),
            category(SettingsCategoryId.ACCESSIBILITY, SettingsDestinationId.ACCESSIBILITY),
            category(SettingsCategoryId.SECURITY, SettingsDestinationId.SECURITY),
            category(SettingsCategoryId.APPS, SettingsDestinationId.APPLICATIONS),
            category(SettingsCategoryId.ASSISTANCE_VOICE, SettingsDestinationId.ASSISTANT_VOICE),
            category(SettingsCategoryId.SYSTEM, SettingsDestinationId.SYSTEM),
        )

    val destinations: List<SettingsDestinationDefinition> =
        listOf(
            destination(SettingsDestinationId.HOME, "home", UxSafetyClass.ALWAYS_SAFE),
            destination(SettingsDestinationId.SEARCH, "search", UxSafetyClass.GLANCEABLE),
            destination(
                SettingsDestinationId.DISPLAY,
                "display",
                UxSafetyClass.GLANCEABLE,
                actions = setOf("android.settings.DISPLAY_SETTINGS"),
            ),
            destination(SettingsDestinationId.DATE_TIME, "display/date-time", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.TIME_ZONE, "display/date-time/time-zone", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.NETWORK_INTERNET, "network-internet", UxSafetyClass.GLANCEABLE),
            destination(SettingsDestinationId.MOBILE_NETWORK, "network-internet/mobile", UxSafetyClass.PARKED_ONLY),
            destination(
                SettingsDestinationId.WIFI,
                "wifi",
                UxSafetyClass.PARKED_ONLY,
                SettingsCapability.WIFI,
                actions = setOf("android.settings.WIFI_SETTINGS"),
            ),
            destination(SettingsDestinationId.WIFI_HOTSPOT, "wifi/hotspot", UxSafetyClass.PARKED_ONLY, SettingsCapability.WIFI),
            destination(SettingsDestinationId.WIFI_PREFERENCES, "wifi/preferences", UxSafetyClass.PARKED_ONLY, SettingsCapability.WIFI),
            destination(SettingsDestinationId.WIFI_QR, "wifi/qr/{kind}", UxSafetyClass.PARKED_ONLY, SettingsCapability.WIFI),
            destination(SettingsDestinationId.WIFI_NETWORK_DETAILS, "wifi/details", UxSafetyClass.PARKED_ONLY, SettingsCapability.WIFI),
            destination(
                SettingsDestinationId.BLUETOOTH,
                "bluetooth",
                UxSafetyClass.PARKED_ONLY,
                SettingsCapability.BLUETOOTH,
                actions = setOf("android.settings.BLUETOOTH_SETTINGS"),
            ),
            destination(
                SettingsDestinationId.BLUETOOTH_DEVICE_DETAILS,
                "bluetooth/details",
                UxSafetyClass.PARKED_ONLY,
                SettingsCapability.BLUETOOTH,
            ),
            destination(SettingsDestinationId.SOUND, "sound", UxSafetyClass.GLANCEABLE, actions = setOf("android.settings.SOUND_SETTINGS")),
            destination(SettingsDestinationId.PHONE_RINGTONE, "sound/ringtone/PHONE", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.NOTIFICATION_RINGTONE, "sound/ringtone/NOTIFICATION", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.ALARM_RINGTONE, "sound/ringtone/ALARM", UxSafetyClass.PARKED_ONLY),
            destination(
                SettingsDestinationId.APPLICATIONS,
                "applications",
                UxSafetyClass.GLANCEABLE,
                actions = setOf("android.settings.APPLICATION_SETTINGS"),
            ),
            destination(SettingsDestinationId.ALL_APPLICATIONS, "applications/all", UxSafetyClass.GLANCEABLE),
            destination(SettingsDestinationId.APPLICATION_DETAILS, "applications/details/{packageName}", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.APPLICATION_STORAGE, "applications/storage/{packageName}", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.APPLICATION_PERMISSIONS, "applications/permissions", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.PERMISSION_GROUP, "applications/permissions/group/{groupName}", UxSafetyClass.PARKED_ONLY),
            destination(
                SettingsDestinationId.APPLICATION_PERMISSIONS_DETAIL,
                "applications/permissions/app/{packageName}",
                UxSafetyClass.PARKED_ONLY,
            ),
            destination(SettingsDestinationId.UNUSED_APPLICATIONS, "applications/unused", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.DEFAULT_APPLICATIONS, "applications/defaults", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.OPENING_LINKS, "applications/defaults/opening-links", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.SPECIAL_APP_ACCESS, "applications/special-access", UxSafetyClass.PARKED_ONLY),
            destination(
                SettingsDestinationId.SPECIAL_ACCESS_LIST,
                "applications/special-access/{specialAccessType}",
                UxSafetyClass.PARKED_ONLY,
            ),
            destination(SettingsDestinationId.PERFORMANCE_APPS, "applications/performance-impacting", UxSafetyClass.GLANCEABLE),
            destination(
                SettingsDestinationId.NOTIFICATIONS,
                "notifications",
                UxSafetyClass.GLANCEABLE,
                actions = setOf("android.settings.NOTIFICATION_SETTINGS"),
            ),
            destination(
                SettingsDestinationId.NOTIFICATION_APP_DETAILS,
                "notifications/app/{packageName}",
                UxSafetyClass.PARKED_ONLY,
            ),
            destination(
                SettingsDestinationId.PRIVACY,
                "privacy",
                UxSafetyClass.GLANCEABLE,
                actions = setOf("android.settings.PRIVACY_SETTINGS"),
            ),
            destination(
                SettingsDestinationId.PRIVACY_MICROPHONE,
                "privacy/microphone",
                UxSafetyClass.GLANCEABLE,
                SettingsCapability.SENSOR_PRIVACY,
            ),
            destination(
                SettingsDestinationId.PRIVACY_CAMERA,
                "privacy/camera",
                UxSafetyClass.GLANCEABLE,
                SettingsCapability.SENSOR_PRIVACY,
            ),
            destination(SettingsDestinationId.PRIVACY_LOCATION, "privacy/location", UxSafetyClass.GLANCEABLE),
            destination(SettingsDestinationId.PRIVACY_PERMISSION_APPS, "privacy/permissions/{permissionType}", UxSafetyClass.PARKED_ONLY),
            destination(
                SettingsDestinationId.LOCATION,
                "location",
                UxSafetyClass.GLANCEABLE,
                actions = setOf("android.settings.LOCATION_SOURCE_SETTINGS"),
            ),
            destination(SettingsDestinationId.LOCATION_APPS, "location/apps", UxSafetyClass.GLANCEABLE),
            destination(
                SettingsDestinationId.ACCESSIBILITY,
                "accessibility",
                UxSafetyClass.PARKED_ONLY,
                actions = setOf("android.settings.ACCESSIBILITY_SETTINGS"),
            ),
            destination(
                SettingsDestinationId.ASSISTANT_VOICE,
                "assistant-voice",
                UxSafetyClass.PARKED_ONLY,
                actions = setOf("android.settings.VOICE_INPUT_SETTINGS"),
            ),
            destination(
                SettingsDestinationId.SECURITY,
                "security",
                UxSafetyClass.PARKED_ONLY,
                SettingsCapability.CREDENTIAL_STORAGE,
                actions = setOf("android.settings.SECURITY_SETTINGS"),
            ),
            destination(
                SettingsDestinationId.SCREEN_LOCK,
                "security/lock-types",
                UxSafetyClass.PARKED_ONLY,
                SettingsCapability.CREDENTIAL_STORAGE,
            ),
            destination(
                SettingsDestinationId.SCREEN_LOCK_SETUP,
                "security/lock/{lockType}",
                UxSafetyClass.PARKED_ONLY,
                SettingsCapability.CREDENTIAL_STORAGE,
            ),
            destination(SettingsDestinationId.DEVICE_ADMINS, "security/device-admins", UxSafetyClass.PARKED_ONLY),
            destination(
                SettingsDestinationId.PROFILE_ACCOUNTS,
                "profile-accounts",
                UxSafetyClass.PARKED_ONLY,
                actions = setOf("android.settings.SYNC_SETTINGS"),
            ),
            destination(SettingsDestinationId.PROFILES, "profile-accounts/profiles", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.ADD_PROFILE, "profile-accounts/profiles/add", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.PROFILE_DETAILS, "profile-accounts/profiles/{profileId}", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.ADD_ACCOUNT, "profile-accounts/accounts/add", UxSafetyClass.PARKED_ONLY),
            destination(
                SettingsDestinationId.ACCOUNT_DETAILS,
                "profile-accounts/accounts/{accountName}/{accountType}",
                UxSafetyClass.PARKED_ONLY,
            ),
            destination(
                SettingsDestinationId.SYSTEM,
                "system",
                UxSafetyClass.GLANCEABLE,
                actions = setOf("android.settings.SETTINGS", "android.settings.SYSTEM_UPDATE_SETTINGS"),
            ),
            destination(
                SettingsDestinationId.ABOUT,
                "system/about",
                UxSafetyClass.GLANCEABLE,
                actions = setOf("android.settings.DEVICE_INFO_SETTINGS"),
            ),
            destination(SettingsDestinationId.ABOUT_HARDWARE, "system/about/hardware", UxSafetyClass.GLANCEABLE),
            destination(SettingsDestinationId.LANGUAGE_INPUT, "system/language-input", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.LANGUAGE_PICKER, "system/language-input/language", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.AUTOFILL, "system/language-input/autofill", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.KEYBOARD, "system/language-input/keyboard", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.TEXT_TO_SPEECH, "system/language-input/text-to-speech", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.UNITS, "system/units", UxSafetyClass.GLANCEABLE),
            destination(SettingsDestinationId.STORAGE, "system/storage", UxSafetyClass.GLANCEABLE),
            destination(SettingsDestinationId.LEGAL, "system/legal", UxSafetyClass.GLANCEABLE),
            destination(SettingsDestinationId.RESET_OPTIONS, "system/reset", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.RESET_NETWORK, "system/reset/network", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.RESET_APP_PREFERENCES, "system/reset/app-preferences", UxSafetyClass.PARKED_ONLY),
            destination(SettingsDestinationId.FACTORY_RESET, "system/reset/factory", UxSafetyClass.PARKED_ONLY),
            destination(
                SettingsDestinationId.HVAC,
                "hvac",
                UxSafetyClass.GLANCEABLE,
                SettingsCapability.VEHICLE_SERVICE,
                actions = setOf("com.android.car.settings.action.OPEN_CLIMATE_SETTINGS"),
            ),
            destination(
                SettingsDestinationId.DRIVER_ASSISTANCE,
                "vehicle/driver-assistance",
                UxSafetyClass.GLANCEABLE,
                SettingsCapability.VEHICLE_SERVICE,
                actions = setOf("com.android.car.settings.action.OPEN_VEHICLE_CONTROL"),
            ),
            destination(
                SettingsDestinationId.SEAT_CONTROL,
                "vehicle/seat-control",
                UxSafetyClass.GLANCEABLE,
                SettingsCapability.VEHICLE_SERVICE,
            ),
            destination(
                SettingsDestinationId.VEHICLE_LIGHTING,
                "vehicle/lighting",
                UxSafetyClass.GLANCEABLE,
                SettingsCapability.VEHICLE_SERVICE,
            ),
            destination(
                SettingsDestinationId.DOOR_CONTROL,
                "vehicle/door-control",
                UxSafetyClass.GLANCEABLE,
                SettingsCapability.VEHICLE_SERVICE,
            ),
            destination(
                SettingsDestinationId.VEHICLE,
                "vehicle",
                UxSafetyClass.GLANCEABLE,
                SettingsCapability.VEHICLE_SERVICE,
            ),
            destination(
                SettingsDestinationId.LAUNCHER,
                "launcher",
                UxSafetyClass.GLANCEABLE,
                actions = setOf("com.android.car.settings.action.OPEN_LAUNCHER_SETTINGS"),
            ),
            destination(
                SettingsDestinationId.SYSTEM_UI_CATALOG,
                "system_ui",
                UxSafetyClass.GLANCEABLE,
                SettingsCapability.SYSTEM_UI,
                actions = setOf("com.android.car.settings.action.OPEN_SYSTEM_UI_CATALOG"),
            ),
            destination(SettingsDestinationId.SYSTEM_UI_COCKPIT, null, UxSafetyClass.GLANCEABLE, SettingsCapability.SYSTEM_UI),
            destination(SettingsDestinationId.SYSTEM_UI_NOTIFICATION_CENTER, null, UxSafetyClass.GLANCEABLE, SettingsCapability.SYSTEM_UI),
            destination(SettingsDestinationId.SYSTEM_UI_USER_CENTER, null, UxSafetyClass.PARKED_ONLY, SettingsCapability.SYSTEM_UI),
        )

    private val byId = destinations.associateBy(SettingsDestinationDefinition::id)
    private val byRoute = destinations.mapNotNull { definition -> definition.route?.let { it to definition } }.toMap()
    private val byIntent = destinations.flatMap { definition -> definition.intentActions.map { it to definition } }.toMap()

    fun definition(id: SettingsDestinationId): SettingsDestinationDefinition = byId.getValue(id)

    fun category(id: SettingsCategoryId): SettingsCategoryDefinition = categories.first { it.id == id }

    fun categoryForDestination(id: SettingsDestinationId): SettingsCategoryDefinition = category(definition(id).categoryId)

    fun categoryForRoute(route: String?): SettingsCategoryDefinition? = destinationForRoute(route)?.let { categoryForDestination(it.id) }

    fun route(id: SettingsDestinationId): String = requireNotNull(definition(id).route) { "$id does not have an in-app route" }

    fun destinationForRoute(route: String?): SettingsDestinationDefinition? {
        if (route == null) return null
        val cleanRoute = route.substringBefore('?')
        return byRoute[cleanRoute]
            ?: destinations.firstOrNull { definition ->
                definition.route?.let { registeredRoute -> routesMatch(registeredRoute, cleanRoute) } == true
            }
    }

    fun destinationForIntentAction(action: String?): SettingsDestinationDefinition =
        action?.let(byIntent::get) ?: definition(SettingsDestinationId.HOME)

    private fun feature(
        id: SettingsFeatureId,
        destination: SettingsDestinationId,
    ) = SettingsFeatureDefinition(id, destination)

    private fun category(
        id: SettingsCategoryId,
        rootDestination: SettingsDestinationId,
    ) = SettingsCategoryDefinition(id, rootDestination)

    private fun destination(
        id: SettingsDestinationId,
        route: String?,
        safety: UxSafetyClass,
        vararg capabilities: SettingsCapability,
        actions: Set<String> = emptySet(),
    ) = SettingsDestinationDefinition(id, route, safety, capabilities.toSet(), actions)

    private fun routesMatch(
        registeredRoute: String,
        actualRoute: String,
    ): Boolean {
        val registeredSegments = registeredRoute.split('/')
        val actualSegments = actualRoute.split('/')
        return registeredSegments.size == actualSegments.size &&
            registeredSegments.zip(actualSegments).all { (registered, actual) ->
                registered == actual ||
                    (registered.startsWith('{') && registered.endsWith('}')) ||
                    (actual.startsWith('{') && actual.endsWith('}'))
            }
    }
}

private fun categoryForDestination(id: SettingsDestinationId): SettingsCategoryId =
    when (id) {
        SettingsDestinationId.HOME,
        SettingsDestinationId.BLUETOOTH,
        SettingsDestinationId.BLUETOOTH_DEVICE_DETAILS,
        -> SettingsCategoryId.CONNECTED_DEVICES
        SettingsDestinationId.NETWORK_INTERNET,
        SettingsDestinationId.MOBILE_NETWORK,
        SettingsDestinationId.WIFI,
        SettingsDestinationId.WIFI_HOTSPOT,
        SettingsDestinationId.WIFI_PREFERENCES,
        SettingsDestinationId.WIFI_QR,
        SettingsDestinationId.WIFI_NETWORK_DETAILS,
        -> SettingsCategoryId.NETWORK_INTERNET
        SettingsDestinationId.NOTIFICATIONS,
        SettingsDestinationId.NOTIFICATION_APP_DETAILS,
        -> SettingsCategoryId.NOTIFICATIONS
        SettingsDestinationId.SOUND,
        SettingsDestinationId.PHONE_RINGTONE,
        SettingsDestinationId.NOTIFICATION_RINGTONE,
        SettingsDestinationId.ALARM_RINGTONE,
        -> SettingsCategoryId.SOUND
        SettingsDestinationId.DISPLAY,
        SettingsDestinationId.DATE_TIME,
        SettingsDestinationId.TIME_ZONE,
        -> SettingsCategoryId.DISPLAY
        SettingsDestinationId.PROFILE_ACCOUNTS,
        SettingsDestinationId.PROFILES,
        SettingsDestinationId.ADD_PROFILE,
        SettingsDestinationId.PROFILE_DETAILS,
        SettingsDestinationId.ADD_ACCOUNT,
        SettingsDestinationId.ACCOUNT_DETAILS,
        -> SettingsCategoryId.PROFILE
        SettingsDestinationId.LOCATION,
        SettingsDestinationId.LOCATION_APPS,
        -> SettingsCategoryId.LOCATION
        SettingsDestinationId.PRIVACY,
        SettingsDestinationId.PRIVACY_MICROPHONE,
        SettingsDestinationId.PRIVACY_CAMERA,
        SettingsDestinationId.PRIVACY_LOCATION,
        SettingsDestinationId.PRIVACY_PERMISSION_APPS,
        -> SettingsCategoryId.PRIVACY
        SettingsDestinationId.ACCESSIBILITY -> SettingsCategoryId.ACCESSIBILITY
        SettingsDestinationId.SECURITY,
        SettingsDestinationId.SCREEN_LOCK,
        SettingsDestinationId.SCREEN_LOCK_SETUP,
        SettingsDestinationId.DEVICE_ADMINS,
        -> SettingsCategoryId.SECURITY
        SettingsDestinationId.APPLICATIONS,
        SettingsDestinationId.ALL_APPLICATIONS,
        SettingsDestinationId.APPLICATION_DETAILS,
        SettingsDestinationId.APPLICATION_STORAGE,
        SettingsDestinationId.APPLICATION_PERMISSIONS,
        SettingsDestinationId.PERMISSION_GROUP,
        SettingsDestinationId.APPLICATION_PERMISSIONS_DETAIL,
        SettingsDestinationId.UNUSED_APPLICATIONS,
        SettingsDestinationId.DEFAULT_APPLICATIONS,
        SettingsDestinationId.OPENING_LINKS,
        SettingsDestinationId.SPECIAL_APP_ACCESS,
        SettingsDestinationId.SPECIAL_ACCESS_LIST,
        SettingsDestinationId.PERFORMANCE_APPS,
        SettingsDestinationId.LAUNCHER,
        -> SettingsCategoryId.APPS
        SettingsDestinationId.ASSISTANT_VOICE -> SettingsCategoryId.ASSISTANCE_VOICE
        SettingsDestinationId.VEHICLE,
        SettingsDestinationId.HVAC,
        SettingsDestinationId.DRIVER_ASSISTANCE,
        SettingsDestinationId.SEAT_CONTROL,
        SettingsDestinationId.VEHICLE_LIGHTING,
        SettingsDestinationId.DOOR_CONTROL,
        -> SettingsCategoryId.VEHICLE
        SettingsDestinationId.SEARCH -> SettingsCategoryId.CONNECTED_DEVICES
        SettingsDestinationId.SYSTEM,
        SettingsDestinationId.ABOUT,
        SettingsDestinationId.ABOUT_HARDWARE,
        SettingsDestinationId.LANGUAGE_INPUT,
        SettingsDestinationId.LANGUAGE_PICKER,
        SettingsDestinationId.AUTOFILL,
        SettingsDestinationId.KEYBOARD,
        SettingsDestinationId.TEXT_TO_SPEECH,
        SettingsDestinationId.UNITS,
        SettingsDestinationId.STORAGE,
        SettingsDestinationId.LEGAL,
        SettingsDestinationId.RESET_OPTIONS,
        SettingsDestinationId.RESET_NETWORK,
        SettingsDestinationId.RESET_APP_PREFERENCES,
        SettingsDestinationId.FACTORY_RESET,
        SettingsDestinationId.SYSTEM_UI_CATALOG,
        SettingsDestinationId.SYSTEM_UI_COCKPIT,
        SettingsDestinationId.SYSTEM_UI_NOTIFICATION_CENTER,
        SettingsDestinationId.SYSTEM_UI_USER_CENTER,
        -> SettingsCategoryId.SYSTEM
    }

fun UxSafetyClass.isAllowedWhileRestricted(): Boolean = this != UxSafetyClass.PARKED_ONLY
