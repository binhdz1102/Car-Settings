package com.android.car.settings.feature.applications.domain

import javax.inject.Inject

class ApplicationsUseCases
    @Inject
    constructor(
        private val repository: ApplicationsRepository,
    ) {
        fun observeState() = repository.state

        fun observeDetails() = repository.details

        fun observeSpecialAccess() = repository.specialAccess

        fun observePermissionGroups() = repository.permissionGroups

        fun observePermissionApps() = repository.permissionApps

        fun observeSelectedAppPermissions() = repository.selectedAppPermissions

        fun observeDefaultAppRoles() = repository.defaultAppRoles

        fun observeOpeningLinks() = repository.openingLinks

        suspend fun refresh() = repository.refresh()

        suspend fun setShowSystemApps(show: Boolean) = repository.setShowSystemApps(show)

        suspend fun selectApplication(packageName: String) = repository.selectApplication(packageName)

        suspend fun clearSelectedApplication() = repository.clearSelectedApplication()

        suspend fun setNotificationsEnabled(enabled: Boolean) = repository.setNotificationsEnabled(enabled)

        suspend fun setUnusedAppOptimizationEnabled(enabled: Boolean) = repository.setUnusedAppOptimizationEnabled(enabled)

        suspend fun forceStop() = repository.forceStop()

        suspend fun performPrimaryAction() = repository.performPrimaryAction()

        suspend fun clearStorage() = repository.clearStorage()

        suspend fun clearCache() = repository.clearCache()

        suspend fun setPrioritizePerformanceEnabled(enabled: Boolean) = repository.setPrioritizePerformanceEnabled(enabled)

        suspend fun selectSpecialAccess(type: SpecialAccessType) = repository.selectSpecialAccess(type)

        suspend fun setSpecialAccessShowSystemApps(show: Boolean) = repository.setSpecialAccessShowSystemApps(show)

        suspend fun setSpecialAccessAllowed(
            packageName: String,
            allowed: Boolean,
        ) = repository.setSpecialAccessAllowed(packageName, allowed)

        suspend fun selectPermissionGroup(groupName: String) = repository.selectPermissionGroup(groupName)

        suspend fun selectApplicationPermissions(packageName: String) = repository.selectApplicationPermissions(packageName)

        suspend fun setPermission(
            packageName: String,
            permissionName: String,
            granted: Boolean,
        ) = repository.setPermission(packageName, permissionName, granted)

        suspend fun refreshDefaultApps() = repository.refreshDefaultApps()

        suspend fun setDefaultApp(
            roleName: String,
            packageName: String,
        ) = repository.setDefaultApp(roleName, packageName)

        suspend fun refreshOpeningLinks() = repository.refreshOpeningLinks()

        suspend fun setOpeningLinksEnabled(
            packageName: String,
            enabled: Boolean,
        ) = repository.setOpeningLinksEnabled(packageName, enabled)
    }
