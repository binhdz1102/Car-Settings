package com.android.car.settings.feature.applications.domain

data class ApplicationsState(
    val apps: List<ApplicationSummary> = emptyList(),
    val recentApps: List<ApplicationSummary> = emptyList(),
    val performanceImpactingApps: List<ApplicationSummary> = emptyList(),
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
