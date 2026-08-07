package com.android.car.settings.feature.privacy.domain

enum class PrivacyPermissionType(
    val title: String,
    val permission: String,
) {
    MICROPHONE("Microphone", "android.permission.RECORD_AUDIO"),
    CAMERA("Camera", "android.permission.CAMERA"),
    LOCATION("Location", "android.permission.ACCESS_FINE_LOCATION"),
}

data class PrivacySensorState(
    val supported: Boolean = false,
    /** True means apps may access the sensor; SensorPrivacyManager itself stores the inverse. */
    val accessEnabled: Boolean = false,
    /**
     * Non-null when the automotive image does not expose sensor privacy to this package. This is
     * distinct from a vehicle which has no physical sensor toggle.
     */
    val unavailableReason: String? = null,
)

data class PrivacyAccessApp(
    val packageName: String,
    val label: String,
    val lastAccessMillis: Long = 0L,
)

data class PrivacyPermissionApp(
    val packageName: String,
    val label: String,
    val granted: Boolean,
    val isEnabled: Boolean,
)

data class PrivacyState(
    val microphone: PrivacySensorState = PrivacySensorState(),
    val camera: PrivacySensorState = PrivacySensorState(),
    val locationEnabled: Boolean = false,
    val recentMicrophoneAccess: List<PrivacyAccessApp> = emptyList(),
    val recentCameraAccess: List<PrivacyAccessApp> = emptyList(),
    val recentLocationAccess: List<PrivacyAccessApp> = emptyList(),
    val selectedPermissionType: PrivacyPermissionType? = null,
    val permissionApps: List<PrivacyPermissionApp> = emptyList(),
    val lastError: String? = null,
)
