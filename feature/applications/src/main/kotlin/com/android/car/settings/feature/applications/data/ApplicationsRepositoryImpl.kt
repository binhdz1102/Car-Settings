package com.android.car.settings.feature.applications.data

import com.android.car.settings.feature.applications.domain.AppPermissionState
import com.android.car.settings.feature.applications.domain.ApplicationDetails
import com.android.car.settings.feature.applications.domain.ApplicationsRepository
import com.android.car.settings.feature.applications.domain.ApplicationsState
import com.android.car.settings.feature.applications.domain.DefaultAppRole
import com.android.car.settings.feature.applications.domain.OpeningLinkState
import com.android.car.settings.feature.applications.domain.PermissionGroupSummary
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
        private val baseState: Flow<ApplicationsState> =
            combine(
                platform.apps,
                platform.recentApps,
                platform.unusedApps,
                platform.showSystemApps,
                platform.unusedAppCount,
            ) { apps, recentApps, unusedApps, showSystemApps, unusedAppCount ->
                ApplicationsState(
                    apps = apps,
                    recentApps = recentApps,
                    unusedApps = unusedApps,
                    showSystemApps = showSystemApps,
                    unusedAppCount = unusedAppCount,
                )
            }

        override val state: Flow<ApplicationsState> =
            combine(baseState, platform.performanceImpactingApps) { state, performanceImpactingApps ->
                state.copy(performanceImpactingApps = performanceImpactingApps)
            }

        override val details: Flow<ApplicationDetails?> = platform.selectedDetails

        override val specialAccess: Flow<SpecialAccessState> = platform.specialAccess

        override val permissionGroups: Flow<List<PermissionGroupSummary>> = platform.permissionGroups

        override val permissionApps: Flow<List<com.android.car.settings.feature.applications.domain.ApplicationSummary>> =
            platform.permissionApps

        override val selectedAppPermissions: Flow<List<AppPermissionState>> = platform.selectedAppPermissions

        override val defaultAppRoles: Flow<List<DefaultAppRole>> = platform.defaultAppRoles

        override val openingLinks: Flow<List<OpeningLinkState>> = platform.openingLinks

        override suspend fun refresh() = platform.refresh()

        override suspend fun setShowSystemApps(show: Boolean) = platform.setShowSystemApps(show)

        override suspend fun selectApplication(packageName: String) = platform.selectApplication(packageName)

        override suspend fun clearSelectedApplication() = platform.clearSelectedApplication()

        override suspend fun setNotificationsEnabled(enabled: Boolean) = platform.setNotificationsEnabled(enabled)

        override suspend fun setUnusedAppOptimizationEnabled(enabled: Boolean) = platform.setUnusedAppOptimizationEnabled(enabled)

        override suspend fun forceStop() = platform.forceStop()

        override suspend fun performPrimaryAction() = platform.performPrimaryAction()

        override suspend fun clearStorage() = platform.clearStorage()

        override suspend fun clearCache() = platform.clearCache()

        override suspend fun setPrioritizePerformanceEnabled(enabled: Boolean) = platform.setPrioritizePerformanceEnabled(enabled)

        override suspend fun selectSpecialAccess(type: SpecialAccessType) = platform.selectSpecialAccess(type)

        override suspend fun setSpecialAccessShowSystemApps(show: Boolean) = platform.setSpecialAccessShowSystemApps(show)

        override suspend fun setSpecialAccessAllowed(
            packageName: String,
            allowed: Boolean,
        ) = platform.setSpecialAccessAllowed(packageName, allowed)

        override suspend fun selectPermissionGroup(groupName: String) = platform.selectPermissionGroup(groupName)

        override suspend fun selectApplicationPermissions(packageName: String) = platform.selectApplicationPermissions(packageName)

        override suspend fun setPermission(
            packageName: String,
            permissionName: String,
            granted: Boolean,
        ) = platform.setPermission(packageName, permissionName, granted)

        override suspend fun refreshDefaultApps() = platform.refreshDefaultApps()

        override suspend fun setDefaultApp(
            roleName: String,
            packageName: String,
        ) = platform.setDefaultApp(roleName, packageName)

        override suspend fun refreshOpeningLinks() = platform.refreshOpeningLinks()

        override suspend fun setOpeningLinksEnabled(
            packageName: String,
            enabled: Boolean,
        ) = platform.setOpeningLinksEnabled(packageName, enabled)
    }
