package com.android.car.settings.feature.display.domain

data class DisplayState(
    val brightnessGamma: Int = GAMMA_SPACE_MAX,
    val adaptiveBrightnessEnabled: Boolean = false,
    val adaptiveBrightnessAvailable: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val themeModeAvailable: Boolean = false,
    val dateTime: DateTimeState = DateTimeState(),
)

enum class ThemeMode {
    AUTO,
    DAY,
    NIGHT,
}

/** Framework-derived state for the Date & time page embedded in Automotive Settings. */
data class DateTimeState(
    val currentEpochMillis: Long = 0L,
    val autoTimeEnabled: Boolean = false,
    val autoTimeAvailable: Boolean = false,
    val autoTimeZoneEnabled: Boolean = false,
    val autoTimeZoneAvailable: Boolean = false,
    val use24HourFormat: Boolean = false,
    val timeZoneId: String = "UTC",
    val canSetManualTime: Boolean = false,
    val canSetManualTimeZone: Boolean = false,
)

const val GAMMA_SPACE_MAX = 65_535
