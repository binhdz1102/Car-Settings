package com.android.car.settings.feature.applications.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.applications.domain.ApplicationDetails
import com.android.car.settings.feature.applications.domain.ApplicationSummary
import com.android.car.settings.feature.applications.domain.SpecialAccessState
import com.android.car.settings.feature.applications.domain.SpecialAccessType
import kotlinx.coroutines.flow.StateFlow

internal interface ApplicationsPlatform {
    val apps: StateFlow<List<ApplicationSummary>>

    val recentApps: StateFlow<List<ApplicationSummary>>

    val unusedAppCount: StateFlow<Int?>

    val selectedDetails: StateFlow<ApplicationDetails?>

    val showSystemApps: StateFlow<Boolean>

    val specialAccess: StateFlow<SpecialAccessState>

    val performanceImpactingApps: StateFlow<List<ApplicationSummary>>

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

    suspend fun setSpecialAccessAllowed(packageName: String, allowed: Boolean): ActionResult
}
