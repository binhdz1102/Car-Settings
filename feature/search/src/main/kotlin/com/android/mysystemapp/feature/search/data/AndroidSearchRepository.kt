package com.android.car.settings.feature.search.data

import android.content.Context
import android.content.pm.PackageManager
import com.android.car.settings.feature.search.domain.SearchAvailability
import com.android.car.settings.feature.search.domain.SearchDestination
import com.android.car.settings.feature.search.domain.SearchRepository
import com.android.car.settings.feature.search.domain.SettingsSearchResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app counterpart of AAOS Settings' XML/raw searchable preference index. The catalog only
 * contains pages implemented by My System App; items are marked unavailable instead of linking
 * users to a Settings page which this app cannot execute on the current vehicle image.
 */
@Singleton
internal class AndroidSearchRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SearchRepository {
    private val packageManager = context.packageManager

    override suspend fun search(query: String): List<SettingsSearchResult> {
        val normalizedQuery = normalize(query)
        val queryTokens = normalizedQuery.split(' ').filter(String::isNotBlank)
        return catalog
            .asSequence()
            .map { entry -> entry.toResult(availabilityFor(entry.destination)) }
            .filter { result ->
                queryTokens.all { token -> result.searchableText().contains(token) }
            }
            .sortedWith(
                compareByDescending<SettingsSearchResult> { result -> score(result, normalizedQuery) }
                    .thenBy { it.screenTitle }
                    .thenBy { it.title },
            )
            .toList()
    }

    private fun availabilityFor(destination: SearchDestination): Availability = when (destination) {
        SearchDestination.WIFI,
        SearchDestination.WIFI_HOTSPOT,
        SearchDestination.WIFI_PREFERENCES ->
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) available
            else unavailable("Wi-Fi hardware is not available on this vehicle")

        SearchDestination.BLUETOOTH ->
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) available
            else unavailable("Bluetooth hardware is not available on this vehicle")

        SearchDestination.PRIVACY_MICROPHONE,
        SearchDestination.PRIVACY_CAMERA ->
            if (hasPermission(PERMISSION_OBSERVE_SENSOR_PRIVACY)) available
            else unavailable(SENSOR_PRIVACY_ROLE_REQUIRED_MESSAGE)

        SearchDestination.SCREEN_LOCK ->
            if (hasPermission(PERMISSION_ACCESS_KEYGUARD_SECURE_STORAGE) &&
                hasPermission(PERMISSION_SET_AND_VERIFY_LOCKSCREEN_CREDENTIALS)
            ) {
                available
            } else {
                unavailable(SCREEN_LOCK_PERMISSION_REQUIRED_MESSAGE)
            }

        else -> available
    }

    private fun hasPermission(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun score(result: SettingsSearchResult, query: String): Int {
        if (query.isBlank()) return 0
        val title = normalize(result.title)
        val screen = normalize(result.screenTitle)
        val searchable = result.searchableText()
        return when {
            title == query -> 500
            title.startsWith(query) -> 400
            title.contains(query) -> 300
            screen.startsWith(query) -> 200
            searchable.contains(query) -> 100
            else -> 0
        }
    }
}

private data class IndexableSetting(
    val key: String,
    val title: String,
    val summary: String,
    val screenTitle: String,
    val keywords: String,
    val destination: SearchDestination,
) {
    fun toResult(availability: Availability) = SettingsSearchResult(
        key = key,
        title = title,
        summary = summary,
        screenTitle = screenTitle,
        destination = destination,
        availability = availability.value,
        unavailableReason = availability.reason,
    )
}

private data class Availability(
    val value: SearchAvailability,
    val reason: String? = null,
)

private val available = Availability(SearchAvailability.AVAILABLE)
private fun unavailable(reason: String) = Availability(SearchAvailability.UNAVAILABLE, reason)

private fun SettingsSearchResult.searchableText(): String = normalize(
    "$title $summary $screenTitle ${keywordsFor(key)}",
)

/* Keeps aliases outside the displayed result, analogous to AAOS search keywords. */
private fun keywordsFor(key: String): String = catalog.firstOrNull { it.key == key }?.keywords.orEmpty()

private fun normalize(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}".toRegex(), "")
        .lowercase(Locale.ROOT)

private val catalog = listOf(
    IndexableSetting("display", "Display", "Brightness, adaptive brightness and theme", "Display", "screen theme night day calm", SearchDestination.DISPLAY),
    IndexableSetting("brightness", "Brightness", "Change display brightness", "Display", "screen light adaptive", SearchDestination.DISPLAY),
    IndexableSetting("adaptive_brightness", "Adaptive brightness", "Automatically adjust brightness", "Display", "automatic screen light", SearchDestination.DISPLAY),
    IndexableSetting("theme", "Theme", "Automatic, Day or Night", "Display", "dark light mode", SearchDestination.DISPLAY),
    IndexableSetting("calm_mode", "Calm mode", "Reduce visual distraction", "Display", "distraction dim", SearchDestination.DISPLAY),
    IndexableSetting("date_time", "Date & time", "Time, date and time zone", "Display", "clock automatic", SearchDestination.DATE_TIME),
    IndexableSetting("time_zone", "Time zone", "Choose the vehicle time zone", "Date & time", "clock region", SearchDestination.TIME_ZONE),

    IndexableSetting("wifi", "Wi-Fi", "Networks and saved connections", "Wi-Fi", "wifi wireless internet", SearchDestination.WIFI),
    IndexableSetting("wifi_saved", "Saved networks", "Manage saved Wi-Fi networks", "Wi-Fi", "wifi remembered", SearchDestination.WIFI),
    IndexableSetting("wifi_hotspot", "Wi-Fi hotspot", "Share this vehicle's internet connection", "Wi-Fi", "tether tethering access point", SearchDestination.WIFI_HOTSPOT),
    IndexableSetting("wifi_preferences", "Wi-Fi preferences", "Scanning, wakeup and network notifications", "Wi-Fi", "wifi scanning wakeup", SearchDestination.WIFI_PREFERENCES),

    IndexableSetting("bluetooth", "Bluetooth", "Pair and manage connected devices", "Bluetooth", "wireless pair device", SearchDestination.BLUETOOTH),
    IndexableSetting("bluetooth_pair", "Pair new device", "Discover nearby Bluetooth devices", "Bluetooth", "scan connect", SearchDestination.BLUETOOTH),
    IndexableSetting("bluetooth_discoverable", "Device visibility", "Make this vehicle discoverable", "Bluetooth", "discoverable", SearchDestination.BLUETOOTH),

    IndexableSetting("sound", "Sound & vibration", "Volume, ringtones and Do Not Disturb", "Sound & vibration", "audio ringer vibration", SearchDestination.SOUND),
    IndexableSetting("volume", "Volume", "Media, call, ring, notification and alarm volume", "Sound & vibration", "audio slider", SearchDestination.SOUND),
    IndexableSetting("ringer_mode", "Ringer mode", "Ring, vibrate or silent", "Sound & vibration", "silent vibration", SearchDestination.SOUND),
    IndexableSetting("phone_ringtone", "Phone ringtone", "Choose the incoming call sound", "Sound & vibration", "call ring", SearchDestination.PHONE_RINGTONE),
    IndexableSetting("notification_sound", "Default notification sound", "Choose the notification sound", "Sound & vibration", "alert tone", SearchDestination.NOTIFICATION_RINGTONE),
    IndexableSetting("alarm_sound", "Default alarm sound", "Choose the alarm sound", "Sound & vibration", "clock tone", SearchDestination.ALARM_RINGTONE),
    IndexableSetting("do_not_disturb", "Do Not Disturb", "Control interruption filtering", "Sound & vibration", "dnd quiet", SearchDestination.SOUND),

    IndexableSetting("climate", "Climate", "Temperature, airflow, defrost and seat comfort", "Climate", "hvac air conditioning fan seat", SearchDestination.HVAC),
    IndexableSetting("climate_temperature", "Climate temperature", "Set temperature by vehicle zone", "Climate", "hvac driver passenger celsius fahrenheit", SearchDestination.HVAC),
    IndexableSetting("climate_airflow", "Climate airflow", "Fan speed, direction, A/C and recirculation", "Climate", "hvac fan ac defrost", SearchDestination.HVAC),

    IndexableSetting("apps", "Apps", "Recently opened apps and app settings", "Apps", "applications", SearchDestination.APPLICATIONS),
    IndexableSetting("all_apps", "All apps", "View all installed applications", "Apps", "application list", SearchDestination.ALL_APPLICATIONS),
    IndexableSetting("special_access", "Special app access", "Permissions with special access", "Apps", "overlay usage install unknown", SearchDestination.SPECIAL_APP_ACCESS),
    IndexableSetting("performance_apps", "Performance impacting apps", "Apps affecting vehicle performance", "Apps", "hibernation unused", SearchDestination.PERFORMANCE_APPS),

    IndexableSetting("notifications", "Notifications", "Recently sent notifications and app controls", "Notifications", "alerts", SearchDestination.NOTIFICATIONS),
    IndexableSetting("notification_apps", "App notifications", "Allow or block notifications by app", "Notifications", "all apps alert", SearchDestination.NOTIFICATIONS),

    IndexableSetting("privacy", "Privacy", "Microphone, camera, location and permissions", "Privacy", "permissions", SearchDestination.PRIVACY),
    IndexableSetting("microphone", "Microphone", "Allow apps to access the microphone", "Privacy", "mic record audio", SearchDestination.PRIVACY_MICROPHONE),
    IndexableSetting("camera", "Camera", "Allow apps to access the camera", "Privacy", "photo video", SearchDestination.PRIVACY_CAMERA),
    IndexableSetting("location", "Location", "Allow apps and services to use location", "Privacy", "gps position", SearchDestination.PRIVACY_LOCATION),

    IndexableSetting("security", "Security", "Screen lock, credentials and device admins", "Security", "password pin pattern", SearchDestination.SECURITY),
    IndexableSetting("screen_lock", "Screen lock", "None, Pattern, PIN or Password", "Security", "credential password pin", SearchDestination.SCREEN_LOCK),
    IndexableSetting("clear_credentials", "Clear credentials", "Reset credential storage", "Security", "certificates keystore", SearchDestination.SECURITY),
    IndexableSetting("device_admin", "Device admin apps", "Manage active device administrators", "Security", "administrator policy", SearchDestination.DEVICE_ADMINS),

    IndexableSetting("profiles", "Profiles & accounts", "Vehicle profiles, accounts and sync", "Profiles & accounts", "users account sync", SearchDestination.PROFILE_ACCOUNTS),
    IndexableSetting("manage_profiles", "Manage other profiles", "Add or manage vehicle users", "Profiles & accounts", "users guest", SearchDestination.PROFILES),
    IndexableSetting("auto_sync", "Automatically sync app data", "Control account synchronization", "Profiles & accounts", "accounts sync", SearchDestination.PROFILE_ACCOUNTS),

    IndexableSetting("system", "System", "About, language, storage and reset", "System", "settings", SearchDestination.SYSTEM),
    IndexableSetting("about", "About", "Vehicle and software information", "System", "build version hardware", SearchDestination.ABOUT),
    IndexableSetting("language", "Languages & input", "Language, keyboard and text to speech", "System", "keyboard tts input", SearchDestination.LANGUAGE_INPUT),
    IndexableSetting("units", "Units", "Vehicle display units", "System", "temperature distance", SearchDestination.UNITS),
    IndexableSetting("storage", "Storage", "Storage used by apps and media", "System", "space files", SearchDestination.STORAGE),
    IndexableSetting("legal", "Legal information", "Open source licenses and legal notices", "System", "licenses", SearchDestination.LEGAL),
    IndexableSetting("reset", "Reset options", "Network, app preferences and factory reset", "System", "factory reset", SearchDestination.RESET_OPTIONS),
)

private const val PERMISSION_OBSERVE_SENSOR_PRIVACY = "android.permission.OBSERVE_SENSOR_PRIVACY"
private const val PERMISSION_ACCESS_KEYGUARD_SECURE_STORAGE = "android.permission.ACCESS_KEYGUARD_SECURE_STORAGE"
private const val PERMISSION_SET_AND_VERIFY_LOCKSCREEN_CREDENTIALS =
    "android.permission.SET_AND_VERIFY_LOCKSCREEN_CREDENTIALS"
private const val SENSOR_PRIVACY_ROLE_REQUIRED_MESSAGE =
    "Requires the AAOS Settings sensor-privacy role on this vehicle"
private const val SCREEN_LOCK_PERMISSION_REQUIRED_MESSAGE =
    "Requires the AAOS screen-lock system permissions on this vehicle"
