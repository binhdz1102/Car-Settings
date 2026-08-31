package com.android.car.settings.feature.privacy.domain

import androidx.annotation.StringRes
import com.android.car.settings.feature.privacy.R

enum class PrivacyPermissionType(
    @StringRes val titleRes: Int,
    @StringRes val subjectRes: Int,
    val permission: String,
    val permissions: Set<String> = setOf(permission),
) {
    MICROPHONE(
        R.string.privacy_microphone,
        R.string.privacy_microphone_subject,
        "android.permission.RECORD_AUDIO",
    ),
    CAMERA(
        R.string.privacy_camera,
        R.string.privacy_camera_subject,
        "android.permission.CAMERA",
    ),
    LOCATION(
        R.string.privacy_location,
        R.string.privacy_location_subject,
        "android.permission.ACCESS_FINE_LOCATION",
        setOf(
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
        ),
    ),
}

enum class PrivacySensorWriteBlock {
    MANAGE_PERMISSION_REQUIRED,
}

data class PrivacySensorState(
    val supported: Boolean = false,
    /** Whether this package can mutate the sensor privacy state on the current image/user. */
    val writable: Boolean = false,
    /** True means apps may access the sensor; SensorPrivacyManager itself stores the inverse. */
    val accessEnabled: Boolean = false,
    /**
     * Non-null when the automotive image does not expose sensor privacy to this package. This is
     * distinct from a vehicle which has no physical sensor toggle.
     */
    val unavailableReason: String? = null,
    val writeBlock: PrivacySensorWriteBlock? = null,
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
    val locationSupported: Boolean = false,
    val locationEnabled: Boolean = false,
    val recentMicrophoneAccess: List<PrivacyAccessApp> = emptyList(),
    val recentCameraAccess: List<PrivacyAccessApp> = emptyList(),
    val recentLocationAccess: List<PrivacyAccessApp> = emptyList(),
    val selectedPermissionType: PrivacyPermissionType? = null,
    val permissionApps: List<PrivacyPermissionApp> = emptyList(),
    val lastError: String? = null,
)
