package com.android.car.settings.feature.applications.domain

data class ApplicationsState(
    val apps: List<ApplicationSummary> = emptyList(),
    val recentApps: List<ApplicationSummary> = emptyList(),
    val performanceImpactingApps: List<ApplicationSummary> = emptyList(),
    val unusedApps: List<ApplicationSummary> = emptyList(),
    val showSystemApps: Boolean = false,
    val unusedAppCount: Int? = null,
    val isLoading: Boolean = false,
)

data class ApplicationSummary(
    val packageName: String,
    val label: String,
    val versionName: String = "",
    val isSystemApp: Boolean = false,
    val isEnabled: Boolean = true,
    val lastUsedMillis: Long = 0L,
)

data class ApplicationDetails(
    val packageName: String = "",
    val label: String = "",
    val versionName: String = "",
    val isSystemApp: Boolean = false,
    val isEnabled: Boolean = true,
    val storageBytes: Long? = null,
    val cacheBytes: Long? = null,
    val permissionsSummary: String = "",
    val notificationsEnabled: Boolean? = null,
    val notificationsChangeable: Boolean = false,
    val unusedAppOptimizationEnabled: Boolean? = null,
    val canForceStop: Boolean = false,
    val primaryAction: ApplicationPrimaryAction = ApplicationPrimaryAction.NONE,
    val prioritizePerformanceEnabled: Boolean? = null,
    val canPrioritizePerformance: Boolean = false,
)

/** A permission group as presented by the package/permission controller APIs. */
data class PermissionGroupSummary(
    val groupName: String,
    val label: String,
    val applicationCount: Int,
    val grantedApplicationCount: Int,
)

enum class PermissionGrantMode {
    ALLOW,
    WHILE_IN_USE,
    ALWAYS,
    ASK_EVERY_TIME,
    DENY,
}

data class AppPermissionState(
    val packageName: String,
    val applicationLabel: String,
    val permissionName: String,
    val label: String,
    val groupName: String,
    val isGranted: Boolean,
    val grantMode: PermissionGrantMode,
    val isFixed: Boolean,
    val canChange: Boolean,
)

data class DefaultAppCandidate(
    val packageName: String,
    val label: String,
)

data class DefaultAppRole(
    val roleName: String,
    val title: String,
    val defaultPackageName: String?,
    val candidates: List<DefaultAppCandidate>,
)

data class OpeningLinkState(
    val packageName: String,
    val label: String,
    val domains: List<String>,
    val handlesLinks: Boolean,
)

enum class ApplicationPrimaryAction {
    NONE,
    UNINSTALL,
    DISABLE,
    ENABLE,
}

/** The AppOps-backed special access pages exposed by AAOS Car Settings. */
enum class SpecialAccessType(
    val title: String,
    val description: String,
) {
    ALARMS_AND_REMINDERS(
        title = "Alarms & reminders",
        description = "Allow apps to schedule exact alarms",
    ),
    MODIFY_SYSTEM_SETTINGS(
        title = "Modify system settings",
        description = "Allow apps to change system settings",
    ),
    USAGE_ACCESS(
        title = "Usage access",
        description = "Allow apps to access usage information",
    ),
    WIFI_CONTROL(
        title = "Wi-Fi control",
        description = "Allow apps to control Wi-Fi",
    ),
    NOTIFICATION_ACCESS(
        title = "Notification access",
        description = "Allow apps to read and act on notifications",
    ),
    PREMIUM_SMS(
        title = "Premium SMS",
        description = "Allow apps to send premium SMS messages",
    ),
}

data class SpecialAccessApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val isAllowed: Boolean,
)

data class SpecialAccessState(
    val type: SpecialAccessType? = null,
    val apps: List<SpecialAccessApp> = emptyList(),
    val showSystemApps: Boolean = false,
    val isLoading: Boolean = false,
)
