package com.android.car.settings.feature.security.domain

enum class SecurityLockType(val displayName: String) {
    NONE("None"),
    PATTERN("Pattern"),
    PIN("PIN"),
    PASSWORD("Password"),
    UNKNOWN("Unknown"),
}

data class DeviceAdminApp(
    val componentName: String,
    val label: String,
    val packageName: String,
)

data class SecurityState(
    val lockType: SecurityLockType = SecurityLockType.UNKNOWN,
    val canManageScreenLock: Boolean = false,
    val screenLockUnavailableReason: String? = null,
    val isGuestUser: Boolean = false,
    val deviceAdmins: List<DeviceAdminApp> = emptyList(),
    val lastError: String? = null,
)
