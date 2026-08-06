package com.android.car.settings.feature.applications.domain

import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.flow.Flow

interface ApplicationsRepository {
    val state: Flow<ApplicationsState>

    val details: Flow<ApplicationDetails?>

    val specialAccess: Flow<SpecialAccessState>

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
