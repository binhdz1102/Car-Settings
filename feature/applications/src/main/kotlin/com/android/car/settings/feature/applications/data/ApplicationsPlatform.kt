package com.android.car.settings.feature.applications.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.applications.domain.AppPermissionState
import com.android.car.settings.feature.applications.domain.ApplicationDetails
import com.android.car.settings.feature.applications.domain.ApplicationSummary
import com.android.car.settings.feature.applications.domain.DefaultAppRole
import com.android.car.settings.feature.applications.domain.OpeningLinkState
import com.android.car.settings.feature.applications.domain.PermissionGroupSummary
import com.android.car.settings.feature.applications.domain.SpecialAccessState
import com.android.car.settings.feature.applications.domain.SpecialAccessType
import kotlinx.coroutines.flow.StateFlow

@Suppress("TooManyFunctions")
internal interface ApplicationsPlatform {
    val apps: StateFlow<List<ApplicationSummary>>

    val recentApps: StateFlow<List<ApplicationSummary>>

    val unusedApps: StateFlow<List<ApplicationSummary>>

    val unusedAppCount: StateFlow<Int?>

    val selectedDetails: StateFlow<ApplicationDetails?>

    val showSystemApps: StateFlow<Boolean>

    val specialAccess: StateFlow<SpecialAccessState>

    val performanceImpactingApps: StateFlow<List<ApplicationSummary>>

    val permissionGroups: StateFlow<List<PermissionGroupSummary>>

    val permissionApps: StateFlow<List<ApplicationSummary>>

    val selectedAppPermissions: StateFlow<List<AppPermissionState>>

    val defaultAppRoles: StateFlow<List<DefaultAppRole>>

    val openingLinks: StateFlow<List<OpeningLinkState>>

    suspend fun refresh(): ActionResult

    suspend fun setShowSystemApps(show: Boolean): ActionResult

    suspend fun selectApplication(packageName: String): ActionResult

    suspend fun clearSelectedApplication(): ActionResult

    suspend fun setNotificationsEnabled(enabled: Boolean): ActionResult

    suspend fun setUnusedAppOptimizationEnabled(enabled: Boolean): ActionResult

    suspend fun forceStop(): ActionResult

    suspend fun performPrimaryAction(): ActionResult

    suspend fun clearStorage(): ActionResult

    suspend fun clearCache(): ActionResult

    suspend fun setPrioritizePerformanceEnabled(enabled: Boolean): ActionResult

    suspend fun selectSpecialAccess(type: SpecialAccessType): ActionResult

    suspend fun setSpecialAccessShowSystemApps(show: Boolean): ActionResult

    suspend fun setSpecialAccessAllowed(
        packageName: String,
        allowed: Boolean,
    ): ActionResult

    suspend fun selectPermissionGroup(groupName: String): ActionResult

    suspend fun selectApplicationPermissions(packageName: String): ActionResult

    suspend fun setPermission(
        packageName: String,
        permissionName: String,
        granted: Boolean,
    ): ActionResult

    suspend fun refreshDefaultApps(): ActionResult

    suspend fun setDefaultApp(
        roleName: String,
        packageName: String,
    ): ActionResult

    suspend fun refreshOpeningLinks(): ActionResult

    suspend fun setOpeningLinksEnabled(
        packageName: String,
        enabled: Boolean,
    ): ActionResult
}
