package com.android.car.settings.feature.applications.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.applications.domain.ApplicationDetails
import com.android.car.settings.feature.applications.domain.ApplicationsRepository
import com.android.car.settings.feature.applications.domain.ApplicationsState
import com.android.car.settings.feature.applications.domain.SpecialAccessState
import com.android.car.settings.feature.applications.domain.SpecialAccessType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ApplicationsRepositoryImpl
    @Inject
    constructor(
        private val platform: ApplicationsPlatform,
    ) : ApplicationsRepository {
        override val state: Flow<ApplicationsState> =
            combine(
                platform.apps,
                platform.recentApps,
                platform.showSystemApps,
                platform.unusedAppCount,
                platform.performanceImpactingApps,
            ) { apps, recentApps, showSystemApps, unusedAppCount, performanceImpactingApps ->
                ApplicationsState(
                    apps = apps,
                    recentApps = recentApps,
                    showSystemApps = showSystemApps,
                    unusedAppCount = unusedAppCount,
                    performanceImpactingApps = performanceImpactingApps,
                )
            }

        override val details: Flow<ApplicationDetails?> = platform.selectedDetails

        override val specialAccess: Flow<SpecialAccessState> = platform.specialAccess

        override suspend fun refresh() = platform.refresh()

        override suspend fun setShowSystemApps(show: Boolean) = platform.setShowSystemApps(show)

        override suspend fun selectApplication(packageName: String) = platform.selectApplication(packageName)

        override suspend fun clearSelectedApplication() = platform.clearSelectedApplication()

        override suspend fun setNotificationsEnabled(enabled: Boolean) =
            platform.setNotificationsEnabled(enabled)

        override suspend fun setUnusedAppOptimizationEnabled(enabled: Boolean) =
            platform.setUnusedAppOptimizationEnabled(enabled)

        override suspend fun forceStop() = platform.forceStop()

        override suspend fun performPrimaryAction() = platform.performPrimaryAction()

        override suspend fun clearStorage() = platform.clearStorage()

        override suspend fun clearCache() = platform.clearCache()

        override suspend fun setPrioritizePerformanceEnabled(enabled: Boolean) =
            platform.setPrioritizePerformanceEnabled(enabled)

        override suspend fun selectSpecialAccess(type: SpecialAccessType) =
            platform.selectSpecialAccess(type)

        override suspend fun setSpecialAccessShowSystemApps(show: Boolean) =
            platform.setSpecialAccessShowSystemApps(show)

        override suspend fun setSpecialAccessAllowed(packageName: String, allowed: Boolean) =
            platform.setSpecialAccessAllowed(packageName, allowed)
    }
