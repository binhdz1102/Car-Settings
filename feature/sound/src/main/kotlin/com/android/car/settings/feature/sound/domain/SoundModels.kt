package com.android.car.settings.feature.sound.domain

data class SoundVolume(
    /** AAOS volume-group ID. Negative values are reserved for non-AAOS fallback streams. */
    val id: Int,
    val label: String,
    val current: Int,
    val minimum: Int,
    val maximum: Int,
    val isMuted: Boolean = false,
)

enum class RingerMode {
    SILENT,
    VIBRATE,
    NORMAL,
}

enum class RingtoneKind {
    PHONE,
    NOTIFICATION,
    ALARM,
}

data class RingtoneOption(
    val title: String,
    val uri: String?,
)

data class RingtoneSelection(
    val kind: RingtoneKind,
    val title: String = "Silent",
    val uri: String? = null,
)

enum class InterruptionMode {
    ALL,
    PRIORITY,
    ALARMS,
    NONE,
}

data class DoNotDisturbState(
    val hasPolicyAccess: Boolean = false,
    val mode: InterruptionMode = InterruptionMode.ALL,
)

data class SoundState(
    val volumes: List<SoundVolume> = emptyList(),
    val ringerMode: RingerMode = RingerMode.NORMAL,
    /** Automotive devices using a fixed-volume policy do not support ringer-mode changes. */
    val isRingerModeSupported: Boolean = true,
    val vibrateWhenRinging: Boolean = false,
    val doNotDisturb: DoNotDisturbState = DoNotDisturbState(),
    val ringtones: List<RingtoneSelection> = emptyList(),
    val lastError: String? = null,
)
