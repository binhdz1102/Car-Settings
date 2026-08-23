package com.android.car.settings.feature.applications.data

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.car.Car
import android.car.watchdog.CarWatchdogManager
import android.car.watchdog.PackageKillableState
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager
import android.permission.PermissionControllerManager
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.core.common.rethrowIfCancellation
import com.android.car.settings.core.vehicle.CarServiceProvider
import com.android.car.settings.feature.applications.domain.AppPermissionState
import com.android.car.settings.feature.applications.domain.ApplicationDetails
import com.android.car.settings.feature.applications.domain.ApplicationPrimaryAction
import com.android.car.settings.feature.applications.domain.ApplicationSummary
import com.android.car.settings.feature.applications.domain.DefaultAppCandidate
import com.android.car.settings.feature.applications.domain.DefaultAppRole
import com.android.car.settings.feature.applications.domain.OpeningLinkState
import com.android.car.settings.feature.applications.domain.PermissionGrantMode
import com.android.car.settings.feature.applications.domain.PermissionGroupSummary
import com.android.car.settings.feature.applications.domain.SpecialAccessApp
import com.android.car.settings.feature.applications.domain.SpecialAccessState
import com.android.car.settings.feature.applications.domain.SpecialAccessType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privileged AAOS Applications implementation based on Car Settings' AppsFragment,
 * ApplicationsSettingsFragment and ApplicationDetailsFragment controllers.
 */
@Singleton
@Suppress("LargeClass", "TooManyFunctions")
internal class AndroidApplicationsPlatform
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
        private val carServiceProvider: CarServiceProvider,
    ) : ApplicationsPlatform {
        private val packageManager = context.packageManager
        private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
        private val storageStatsManager = context.getSystemService(StorageStatsManager::class.java)
        private val permissionControllerManager = context.getSystemService(PermissionControllerManager::class.java)
        private val roleManager = context.getSystemService(RoleManager::class.java)
        private val preferences: SharedPreferences =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)

        @Volatile private var carWatchdogManager: CarWatchdogManager? = null
        private val mutableApps = MutableStateFlow<List<ApplicationSummary>>(emptyList())
        private val mutableRecentApps = MutableStateFlow<List<ApplicationSummary>>(emptyList())
        private val mutableUnusedApps = MutableStateFlow<List<ApplicationSummary>>(emptyList())
        private val mutableUnusedAppCount = MutableStateFlow<Int?>(null)
        private val mutableSelectedDetails = MutableStateFlow<ApplicationDetails?>(null)
        private val mutablePerformanceImpactingApps = MutableStateFlow<List<ApplicationSummary>>(emptyList())
        private val mutablePermissionGroups = MutableStateFlow<List<PermissionGroupSummary>>(emptyList())
        private val mutablePermissionApps = MutableStateFlow<List<ApplicationSummary>>(emptyList())
        private val mutableSelectedAppPermissions = MutableStateFlow<List<AppPermissionState>>(emptyList())
        private val mutableDefaultAppRoles = MutableStateFlow<List<DefaultAppRole>>(emptyList())
        private val mutableOpeningLinks = MutableStateFlow<List<OpeningLinkState>>(emptyList())
        private val mutableSpecialAccess = MutableStateFlow(SpecialAccessState())
        private val mutableShowSystemApps =
            MutableStateFlow(preferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false))

        override val apps: StateFlow<List<ApplicationSummary>> = mutableApps.asStateFlow()
        override val recentApps: StateFlow<List<ApplicationSummary>> = mutableRecentApps.asStateFlow()
        override val unusedApps: StateFlow<List<ApplicationSummary>> = mutableUnusedApps.asStateFlow()
        override val unusedAppCount: StateFlow<Int?> = mutableUnusedAppCount.asStateFlow()
        override val selectedDetails: StateFlow<ApplicationDetails?> = mutableSelectedDetails.asStateFlow()
        override val showSystemApps: StateFlow<Boolean> = mutableShowSystemApps.asStateFlow()
        override val specialAccess: StateFlow<SpecialAccessState> = mutableSpecialAccess.asStateFlow()
        override val performanceImpactingApps: StateFlow<List<ApplicationSummary>> =
            mutablePerformanceImpactingApps.asStateFlow()
        override val permissionGroups: StateFlow<List<PermissionGroupSummary>> = mutablePermissionGroups.asStateFlow()
        override val permissionApps: StateFlow<List<ApplicationSummary>> = mutablePermissionApps.asStateFlow()
        override val selectedAppPermissions: StateFlow<List<AppPermissionState>> =
            mutableSelectedAppPermissions.asStateFlow()
        override val defaultAppRoles: StateFlow<List<DefaultAppRole>> = mutableDefaultAppRoles.asStateFlow()
        override val openingLinks: StateFlow<List<OpeningLinkState>> = mutableOpeningLinks.asStateFlow()

        private val packageChangeReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    scope.launch { refresh() }
                }
            }

        init {
            val filter =
                IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_CHANGED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addDataScheme("package")
                }
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(packageChangeReceiver, filter)
            }
            carServiceProvider.register { connectedCar, ready ->
                carWatchdogManager =
                    if (ready) {
                        connectedCar.getCarManager(Car.CAR_WATCHDOG_SERVICE) as? CarWatchdogManager
                    } else {
                        null
                    }
                scope.launch { refresh() }
            }
            scope.launch { refresh() }
        }

        override suspend fun refresh(): ActionResult =
            execute {
                val allInstalled = loadInstalledApplications()
                val visible = allInstalled.filter(::shouldShowInList)
                mutableApps.value = visible
                mutableRecentApps.value = loadRecentApplications(allInstalled)
                mutableUnusedApps.value = loadUnusedApplications(allInstalled)
                mutablePerformanceImpactingApps.value = loadPerformanceImpactingApps(allInstalled)
                refreshUnusedAppCount()
                loadPermissionGroups()
                mutablePermissionApps.value = loadPermissionApps(mutablePermissionGroups.value.firstOrNull()?.groupName)
                loadDefaultAppRoles()
                loadOpeningLinks()
                mutableSelectedDetails.value?.packageName?.let(::refreshSelectedDetails)
                mutableSpecialAccess.value.type?.let(::loadSpecialAccess)
            }

        override suspend fun setShowSystemApps(show: Boolean): ActionResult =
            execute {
                preferences.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, show).apply()
                mutableShowSystemApps.value = show
                refreshOrThrow()
            }

        override suspend fun selectApplication(packageName: String): ActionResult =
            execute {
                mutableSelectedDetails.value = loadApplicationDetails(packageName)
                checkNotNull(mutableSelectedDetails.value) { "Application is no longer installed" }
            }

        override suspend fun clearSelectedApplication(): ActionResult =
            execute {
                mutableSelectedDetails.value = null
            }

        override suspend fun setNotificationsEnabled(enabled: Boolean): ActionResult =
            execute {
                val details = requireSelectedDetails()
                checkNotNull(details.notificationsEnabled) {
                    "Notification control is not available on this system image"
                }
                check(details.notificationsChangeable) {
                    "Notification settings are locked for this application"
                }
                val uid = packageUid(details.packageName)
                check(
                    ApplicationsHiddenApiBridge.setNotificationsEnabled(
                        context,
                        details.packageName,
                        uid,
                        enabled,
                    ),
                ) { "The system did not allow changing application notifications" }
                refreshSelectedDetails(details.packageName)
            }

        override suspend fun setUnusedAppOptimizationEnabled(enabled: Boolean): ActionResult =
            execute {
                val details = requireSelectedDetails()
                checkNotNull(details.unusedAppOptimizationEnabled) {
                    "Unused-app optimization is not available on this system image"
                }
                check(
                    ApplicationsHiddenApiBridge.setUnusedAppOptimizationEnabled(
                        context,
                        packageUid(details.packageName),
                        enabled,
                    ),
                ) { "The system did not allow changing unused-app optimization" }
                refreshSelectedDetails(details.packageName)
            }

        override suspend fun forceStop(): ActionResult =
            execute {
                val details = requireSelectedDetails()
                check(details.canForceStop) { "Force stop is not available for this application" }
                check(ApplicationsHiddenApiBridge.forceStop(context, details.packageName)) {
                    "The system did not allow force stopping this application"
                }
                refreshSelectedDetails(details.packageName)
            }

        override suspend fun performPrimaryAction(): ActionResult =
            execute {
                val details = requireSelectedDetails()
                when (details.primaryAction) {
                    ApplicationPrimaryAction.ENABLE -> {
                        packageManager.setApplicationEnabledSetting(
                            details.packageName,
                            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                            0,
                        )
                    }
                    ApplicationPrimaryAction.DISABLE -> {
                        packageManager.setApplicationEnabledSetting(
                            details.packageName,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                            0,
                        )
                    }
                    ApplicationPrimaryAction.UNINSTALL -> {
                        error("Uninstall must be confirmed by the package installer")
                    }
                    ApplicationPrimaryAction.NONE -> error("No application action is available")
                }
                refreshOrThrow()
            }

        override suspend fun clearStorage(): ActionResult =
            execute {
                val details = requireSelectedDetails()
                check(ApplicationsHiddenApiBridge.clearUserData(context, details.packageName)) {
                    "The system did not allow clearing application storage"
                }
                refreshSelectedDetails(details.packageName)
            }

        override suspend fun clearCache(): ActionResult =
            execute {
                val details = requireSelectedDetails()
                check(ApplicationsHiddenApiBridge.clearCache(context, details.packageName)) {
                    "The system did not allow clearing application cache"
                }
                refreshSelectedDetails(details.packageName)
            }

        @SuppressLint("MissingPermission")
        override suspend fun setPrioritizePerformanceEnabled(enabled: Boolean): ActionResult =
            execute {
                val details = requireSelectedDetails()
                check(details.canPrioritizePerformance) {
                    "App performance priority is not available for this application"
                }
                check(hasCarWatchdogConfigPermission()) {
                    "Vehicle watchdog configuration permission is not granted"
                }
                val info = packageManager.getApplicationInfo(details.packageName, 0)
                val manager =
                    requireNotNull(carWatchdogManager) {
                        "Vehicle watchdog service is not connected"
                    }
                manager.setKillablePackageAsUser(
                    details.packageName,
                    UserHandle.getUserHandleForUid(info.uid),
                    // isKillable=
                    !enabled,
                )
                refreshSelectedDetails(details.packageName)
            }

        override suspend fun selectSpecialAccess(type: SpecialAccessType): ActionResult =
            execute {
                loadSpecialAccess(type)
            }

        override suspend fun setSpecialAccessShowSystemApps(show: Boolean): ActionResult =
            execute {
                val type =
                    requireNotNull(mutableSpecialAccess.value.type) {
                        "No special access page is selected"
                    }
                mutableSpecialAccess.value = mutableSpecialAccess.value.copy(showSystemApps = show)
                loadSpecialAccess(type)
            }

        override suspend fun setSpecialAccessAllowed(
            packageName: String,
            allowed: Boolean,
        ): ActionResult =
            execute {
                val type =
                    requireNotNull(mutableSpecialAccess.value.type) {
                        "No special access page is selected"
                    }
                val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                val changed =
                    if (type == SpecialAccessType.NOTIFICATION_ACCESS) {
                        ApplicationsHiddenApiBridge.setNotificationListenerEnabled(context, packageName, allowed)
                    } else {
                        ApplicationsHiddenApiBridge.setAppOpMode(
                            context,
                            type.appOp,
                            applicationInfo.uid,
                            packageName,
                            if (allowed) APP_OP_MODE_ALLOWED else type.deniedMode,
                        )
                    }
                check(changed) { "The system did not allow changing ${type.title.lowercase(Locale.getDefault())}" }
                loadSpecialAccess(type)
            }

        override suspend fun selectPermissionGroup(groupName: String): ActionResult =
            execute {
                mutablePermissionApps.value = loadPermissionApps(groupName)
            }

        override suspend fun selectApplicationPermissions(packageName: String): ActionResult =
            execute {
                mutableSelectedAppPermissions.value = loadApplicationPermissions(packageName)
            }

        override suspend fun setPermission(
            packageName: String,
            permissionName: String,
            granted: Boolean,
        ): ActionResult =
            execute {
                val user = Process.myUserHandle()
                check(
                    ApplicationsHiddenApiBridge.setRuntimePermission(
                        context,
                        packageName,
                        permissionName,
                        user,
                        granted,
                    ),
                ) { "The system did not allow changing this permission" }
                mutableSelectedAppPermissions.value = loadApplicationPermissions(packageName)
                loadPermissionGroups()
            }

        override suspend fun refreshDefaultApps(): ActionResult = execute { loadDefaultAppRoles() }

        override suspend fun setDefaultApp(
            roleName: String,
            packageName: String,
        ): ActionResult =
            execute {
                check(ApplicationsHiddenApiBridge.setDefaultApplication(context, roleName, packageName)) {
                    "The system did not allow changing the default application"
                }
                loadDefaultAppRoles()
            }

        override suspend fun refreshOpeningLinks(): ActionResult = execute { loadOpeningLinks() }

        override suspend fun setOpeningLinksEnabled(
            packageName: String,
            enabled: Boolean,
        ): ActionResult =
            execute {
                check(ApplicationsHiddenApiBridge.setDomainVerificationLinkHandlingAllowed(context, packageName, enabled)) {
                    "Opening links are not supported on this image"
                }
                loadOpeningLinks()
            }

        private fun loadInstalledApplications(): List<ApplicationSummary> =
            packageManager
                .getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
                .map { applicationInfo -> applicationInfo.toSummary() }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        private fun loadRecentApplications(allInstalled: List<ApplicationSummary>): List<ApplicationSummary> {
            val cutoff = System.currentTimeMillis() - RECENT_APPS_WINDOW_MILLIS
            val summariesByPackage = allInstalled.associateBy { it.packageName }
            return runCatching {
                requireNotNull(usageStatsManager)
                    .queryUsageStats(UsageStatsManager.INTERVAL_BEST, cutoff, System.currentTimeMillis())
                    .orEmpty()
                    .asSequence()
                    .filter { it.lastTimeUsed >= cutoff }
                    .groupBy { it.packageName }
                    .map { (packageName, stats) ->
                        summariesByPackage[packageName]?.copy(
                            lastUsedMillis = stats.maxOf { it.lastTimeUsed },
                        )
                    }.filterNotNull()
                    .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
                    .sortedByDescending { it.lastUsedMillis }
                    .take(MAXIMUM_RECENT_APPS)
                    .toList()
            }.getOrDefault(emptyList())
        }

        /** Mirrors PermissionController's unused-app list without launching its Activity. */
        private fun loadUnusedApplications(allInstalled: List<ApplicationSummary>): List<ApplicationSummary> {
            val cutoff = System.currentTimeMillis() - UNUSED_APP_WINDOW_MILLIS
            val lastUsedByPackage =
                runCatching {
                    requireNotNull(usageStatsManager)
                        .queryUsageStats(UsageStatsManager.INTERVAL_BEST, cutoff, System.currentTimeMillis())
                        .orEmpty()
                        .groupBy { it.packageName }
                        .mapValues { (_, stats) -> stats.maxOfOrNull { it.lastTimeUsed } ?: 0L }
                }.getOrDefault(emptyMap())
            return allInstalled
                .asSequence()
                .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
                .filter { it.packageName != context.packageName }
                .map { summary ->
                    summary.copy(lastUsedMillis = lastUsedByPackage[summary.packageName] ?: 0L)
                }.filter { summary ->
                    val optimization =
                        ApplicationsHiddenApiBridge.getUnusedAppOptimizationMode(
                            context,
                            summary.packageName,
                            packageUid(summary.packageName),
                        )
                    optimization == APP_OP_MODE_IGNORED || summary.lastUsedMillis < cutoff
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                .toList()
        }

        private fun loadPermissionGroups() {
            val groups =
                packageManager
                    .getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.MATCH_DISABLED_COMPONENTS)
                    .asSequence()
                    .filter { packageInfo ->
                        val info = packageInfo.applicationInfo ?: return@filter false
                        mutableShowSystemApps.value ||
                            !info.isSystemApplication() ||
                            packageManager.getLaunchIntentForPackage(packageInfo.packageName) != null
                    }.flatMap { packageInfo ->
                        packageInfo.requestedPermissions.orEmpty().asSequence().mapNotNull { permissionName ->
                            val permissionInfo = runCatching { packageManager.getPermissionInfo(permissionName, 0) }.getOrNull()
                            val groupName = permissionInfo?.group
                            if (permissionInfo != null &&
                                ApplicationsHiddenApiBridge.isRuntimePermission(permissionInfo) &&
                                groupName != null
                            ) {
                                Triple(
                                    packageInfo.packageName,
                                    groupName,
                                    packageManager.checkPermission(permissionName, packageInfo.packageName) ==
                                        PackageManager.PERMISSION_GRANTED,
                                )
                            } else {
                                null
                            }
                        }
                    }.groupBy { it.second }
                    .map { (groupName, permissions) ->
                        val applicationStates = permissions.groupBy { it.first }.values
                        PermissionGroupSummary(
                            groupName = groupName,
                            label =
                                runCatching {
                                    packageManager
                                        .getPermissionGroupInfo(groupName, 0)
                                        .loadLabel(packageManager)
                                        .toString()
                                }.getOrDefault(
                                    groupName.substringAfterLast('.').replace('_', ' ').replaceFirstChar(Char::uppercaseChar),
                                ),
                            applicationCount = applicationStates.size,
                            grantedApplicationCount = applicationStates.count { states -> states.all { it.third } },
                        )
                    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            mutablePermissionGroups.value = groups
        }

        private fun loadPermissionApps(groupName: String?): List<ApplicationSummary> {
            if (groupName == null) return emptyList()
            val packages =
                packageManager
                    .getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.MATCH_DISABLED_COMPONENTS)
                    .asSequence()
                    .filter { packageInfo ->
                        packageInfo.requestedPermissions.orEmpty().any { permissionName ->
                            val info = runCatching { packageManager.getPermissionInfo(permissionName, 0) }.getOrNull()
                            info != null && ApplicationsHiddenApiBridge.isRuntimePermission(info) && info.group == groupName
                        }
                    }.mapNotNull { packageInfo ->
                        packageInfo.applicationInfo
                            ?.takeIf { info ->
                                mutableShowSystemApps.value ||
                                    !info.isSystemApplication() ||
                                    packageManager.getLaunchIntentForPackage(packageInfo.packageName) != null
                            }?.toSummary()
                    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                    .toList()
            return packages
        }

        private fun loadApplicationPermissions(packageName: String): List<AppPermissionState> {
            val packageInfo =
                runCatching {
                    packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS or PackageManager.MATCH_DISABLED_COMPONENTS)
                }.getOrNull() ?: return emptyList()
            val applicationLabel = packageInfo.applicationInfo?.loadLabel(packageManager)?.toString() ?: packageName
            return packageInfo.requestedPermissions
                .orEmpty()
                .mapNotNull { permissionName ->
                    val info =
                        runCatching { packageManager.getPermissionInfo(permissionName, 0) }.getOrNull()
                            ?: return@mapNotNull null
                    if (!ApplicationsHiddenApiBridge.isRuntimePermission(info) || info.group == null) return@mapNotNull null
                    val flags = ApplicationsHiddenApiBridge.getPermissionFlags(context, permissionName, packageName, Process.myUserHandle())
                    val granted = packageManager.checkPermission(permissionName, packageName) == PackageManager.PERMISSION_GRANTED
                    val fixed =
                        flags and (
                            PERMISSION_FLAG_SYSTEM_FIXED or
                                PERMISSION_FLAG_POLICY_FIXED or
                                PERMISSION_FLAG_USER_FIXED
                        ) != 0
                    AppPermissionState(
                        packageName = packageName,
                        applicationLabel = applicationLabel,
                        permissionName = permissionName,
                        label = info.loadLabel(packageManager)?.toString() ?: permissionName.substringAfterLast('.'),
                        groupName = info.group.orEmpty(),
                        isGranted = granted,
                        grantMode = if (granted) PermissionGrantMode.ALLOW else PermissionGrantMode.DENY,
                        isFixed = fixed,
                        canChange = !fixed,
                    )
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }

        private fun loadPerformanceImpactingApps(allInstalled: List<ApplicationSummary>): List<ApplicationSummary> {
            val packageNames =
                android.provider.Settings.Secure
                    .getString(context.contentResolver, PACKAGES_DISABLED_ON_RESOURCE_OVERUSE)
                    .orEmpty()
                    .split(';')
                    .filter(String::isNotBlank)
                    .toSet()
            return allInstalled.filter { it.packageName in packageNames }
        }

        private fun loadSpecialAccess(type: SpecialAccessType) {
            val showSystemApps = mutableSpecialAccess.value.showSystemApps
            mutableSpecialAccess.value = mutableSpecialAccess.value.copy(type = type, isLoading = true)
            val apps =
                packageManager
                    .getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.MATCH_DISABLED_COMPONENTS)
                    .asSequence()
                    .filter { packageInfo -> packageInfo.supportsSpecialAccess(type) }
                    .filter { packageInfo ->
                        type != SpecialAccessType.WIFI_CONTROL ||
                            !packageInfo.requestsPermission(PERMISSION_NETWORK_SETTINGS)
                    }.mapNotNull { packageInfo -> packageInfo.toSpecialAccessApp(type, showSystemApps) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                    .toList()
            mutableSpecialAccess.value =
                SpecialAccessState(
                    type = type,
                    apps = apps,
                    showSystemApps = showSystemApps,
                    isLoading = false,
                )
        }

        private fun PackageInfo.requestsPermission(permission: String): Boolean = requestedPermissions.orEmpty().any { it == permission }

        @Suppress("ReturnCount")
        private fun PackageInfo.toSpecialAccessApp(
            type: SpecialAccessType,
            showSystemApps: Boolean,
        ): SpecialAccessApp? {
            val info = applicationInfo ?: return null
            val isSystem = info.isSystemApplication()
            val launchable = packageManager.getLaunchIntentForPackage(packageName) != null
            if (!showSystemApps && isSystem && !launchable) return null
            val allowed =
                if (type == SpecialAccessType.NOTIFICATION_ACCESS) {
                    ApplicationsHiddenApiBridge.isNotificationListenerEnabled(context, packageName)
                } else {
                    val mode =
                        ApplicationsHiddenApiBridge.getAppOpMode(context, type.appOp, info.uid, packageName)
                            ?: return null
                    mode == APP_OP_MODE_ALLOWED
                }
            return SpecialAccessApp(
                packageName = packageName,
                label = info.loadLabel(packageManager).toString(),
                isSystemApp = isSystem,
                isEnabled = info.enabled,
                isAllowed = allowed,
            )
        }

        private fun PackageInfo.supportsSpecialAccess(type: SpecialAccessType): Boolean =
            when (type) {
                SpecialAccessType.NOTIFICATION_ACCESS ->
                    packageManager
                        .queryIntentServices(
                            Intent("android.service.notification.NotificationListenerService").setPackage(packageName),
                            PackageManager.MATCH_DISABLED_COMPONENTS,
                        ).any { resolve -> resolve.serviceInfo?.permission == PERMISSION_BIND_NOTIFICATION_LISTENER }
                else -> requestsPermission(type.requiredPermission)
            }

        private fun shouldShowInList(summary: ApplicationSummary): Boolean =
            mutableShowSystemApps.value ||
                !summary.isSystemApp ||
                packageManager.getLaunchIntentForPackage(summary.packageName) != null

        private fun ApplicationInfo.toSummary(): ApplicationSummary {
            val packageInfo =
                runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
            return ApplicationSummary(
                packageName = packageName,
                label = loadLabel(packageManager).toString(),
                versionName = packageInfo?.versionName.orEmpty(),
                isSystemApp = isSystemApplication(),
                isEnabled = enabled,
            )
        }

        private fun loadApplicationDetails(packageName: String): ApplicationDetails? =
            runCatching {
                val packageInfo =
                    packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_PERMISSIONS or PackageManager.MATCH_DISABLED_COMPONENTS,
                    )
                val appInfo = requireNotNull(packageInfo.applicationInfo)
                val uid = appInfo.uid
                val storage =
                    runCatching {
                        requireNotNull(storageStatsManager).queryStatsForPackage(
                            StorageManager.UUID_DEFAULT,
                            packageName,
                            Process.myUserHandle(),
                        )
                    }.getOrNull()
                val notificationEnabled =
                    ApplicationsHiddenApiBridge.areNotificationsEnabled(context, packageName, uid)
                val notificationsChangeable =
                    ApplicationsHiddenApiBridge.areNotificationsChangeable(context, packageName, uid)
                val unusedAppMode =
                    ApplicationsHiddenApiBridge.getUnusedAppOptimizationMode(context, packageName, uid)
                val killableState = getKillableState(packageName, uid)
                ApplicationDetails(
                    packageName = packageName,
                    label = appInfo.loadLabel(packageManager).toString(),
                    versionName = packageInfo.versionName.orEmpty(),
                    isSystemApp = appInfo.isSystemApplication(),
                    isEnabled = appInfo.enabled,
                    storageBytes = storage?.dataBytes?.plus(storage.cacheBytes),
                    cacheBytes = storage?.cacheBytes,
                    permissionsSummary = packageInfo.permissionSummary(),
                    notificationsEnabled = notificationEnabled,
                    notificationsChangeable = notificationsChangeable,
                    unusedAppOptimizationEnabled = unusedAppMode?.let { it != APP_OP_MODE_IGNORED },
                    canForceStop = canForceStop(packageName, appInfo),
                    primaryAction = primaryAction(packageName, appInfo),
                    prioritizePerformanceEnabled =
                        killableState?.let { it == PackageKillableState.KILLABLE_STATE_NO },
                    canPrioritizePerformance =
                        killableState != null &&
                            killableState != PackageKillableState.KILLABLE_STATE_NEVER,
                )
            }.getOrNull()

        private fun PackageInfo.permissionSummary(): String {
            val permissions = requestedPermissions.orEmpty()
            if (permissions.isEmpty()) return "No permissions requested"
            val granted =
                permissions.count { permission ->
                    runCatching {
                        packageManager.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED
                    }.getOrDefault(false)
                }
            return "$granted of ${permissions.size} permissions allowed"
        }

        private fun canForceStop(
            packageName: String,
            appInfo: ApplicationInfo,
        ): Boolean =
            packageName != context.packageName &&
                appInfo.enabled &&
                !isDefaultHome(packageName)

        private fun primaryAction(
            packageName: String,
            appInfo: ApplicationInfo,
        ): ApplicationPrimaryAction {
            if (packageName == context.packageName || isDefaultHome(packageName)) {
                return ApplicationPrimaryAction.NONE
            }
            return if (appInfo.isSystemApplication()) {
                if (appInfo.enabled) ApplicationPrimaryAction.DISABLE else ApplicationPrimaryAction.ENABLE
            } else {
                ApplicationPrimaryAction.UNINSTALL
            }
        }

        private fun isDefaultHome(packageName: String): Boolean =
            runCatching {
                val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                packageManager
                    .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo
                    ?.packageName == packageName
            }.getOrDefault(false)

        private fun ApplicationInfo.isSystemApplication(): Boolean =
            flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

        private fun packageUid(packageName: String): Int = packageManager.getApplicationInfo(packageName, 0).uid

        @SuppressLint("MissingPermission")
        private fun getKillableState(
            packageName: String,
            uid: Int,
        ): Int? {
            if (!hasCarWatchdogConfigPermission()) return null
            return runCatching {
                carWatchdogManager
                    ?.getPackageKillableStatesAsUser(UserHandle.getUserHandleForUid(uid))
                    ?.firstOrNull { it.packageName == packageName }
                    ?.killableState
            }.getOrNull()
        }

        private fun refreshSelectedDetails(packageName: String) {
            mutableSelectedDetails.value = loadApplicationDetails(packageName)
        }

        private fun refreshUnusedAppCount() {
            if (!hasManageAppHibernationPermission()) {
                mutableUnusedAppCount.value = null
                return
            }
            runCatching {
                permissionControllerManager?.getUnusedAppCount(mainExecutor()) { count ->
                    mutableUnusedAppCount.value = count
                }
            }
        }

        private fun mainExecutor(): Executor =
            if (Build.VERSION.SDK_INT >= 28) {
                context.mainExecutor
            } else {
                Executor { runnable -> Handler(Looper.getMainLooper()).post(runnable) }
            }

        private fun hasManageAppHibernationPermission(): Boolean =
            context.checkSelfPermission(PERMISSION_MANAGE_APP_HIBERNATION) ==
                PackageManager.PERMISSION_GRANTED

        private fun hasCarWatchdogConfigPermission(): Boolean =
            context.checkSelfPermission(PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG) ==
                PackageManager.PERMISSION_GRANTED

        private fun requireSelectedDetails(): ApplicationDetails =
            requireNotNull(mutableSelectedDetails.value) { "No application selected" }

        private suspend fun execute(block: suspend () -> Unit): ActionResult =
            withContext(dispatcher) {
                try {
                    block()
                    ActionResult.Success
                } catch (throwable: Throwable) {
                    throwable.rethrowIfCancellation()
                    ActionResult.Failure(
                        message = throwable.message ?: throwable.javaClass.simpleName,
                        cause = throwable,
                    )
                }
            }

        private suspend fun refreshOrThrow() {
            check(refresh() == ActionResult.Success) { "Unable to refresh applications state" }
        }

        private fun loadDefaultAppRoles() {
            val manager =
                roleManager ?: run {
                    mutableDefaultAppRoles.value = emptyList()
                    return
                }
            mutableDefaultAppRoles.value =
                DEFAULT_ROLE_SPECS.mapNotNull { spec ->
                    if (!ApplicationsHiddenApiBridge.isRoleAvailable(context, spec.roleName)) return@mapNotNull null
                    val candidates =
                        packageManager
                            .queryIntentActivities(spec.intent, PackageManager.MATCH_DEFAULT_ONLY)
                            .asSequence()
                            .mapNotNull { it.activityInfo?.applicationInfo }
                            .distinctBy { it.packageName }
                            .filter { it.enabled }
                            .map { DefaultAppCandidate(it.packageName, it.loadLabel(packageManager).toString()) }
                            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                            .toList()
                    DefaultAppRole(
                        roleName = spec.roleName,
                        title = spec.title,
                        defaultPackageName = ApplicationsHiddenApiBridge.getRoleHolders(context, spec.roleName).firstOrNull(),
                        candidates = candidates,
                    ).takeIf { candidates.isNotEmpty() }
                }
        }

        private fun loadOpeningLinks() {
            val managed =
                runCatching {
                    ApplicationsHiddenApiBridge.queryValidDomainPackages(context).mapNotNull { packageName: String ->
                        val appInfo =
                            runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
                                ?: return@mapNotNull null
                        OpeningLinkState(
                            packageName = packageName,
                            label = appInfo.loadLabel(packageManager).toString(),
                            domains = ApplicationsHiddenApiBridge.getDomainHosts(context, packageName).sorted(),
                            handlesLinks = ApplicationsHiddenApiBridge.isDomainLinkHandlingAllowed(context, packageName),
                        )
                    }
                }.getOrDefault(emptyList())
            if (managed.isNotEmpty()) {
                mutableOpeningLinks.value = managed.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                return
            }
            val links =
                listOf(
                    Intent(Intent.ACTION_VIEW, Uri.parse("http://example.invalid")).addCategory(Intent.CATEGORY_BROWSABLE),
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid")).addCategory(Intent.CATEGORY_BROWSABLE),
                ).flatMap { intent -> packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY) }
                    .groupBy { it.activityInfo?.applicationInfo?.packageName }
                    .mapNotNull { (packageName, resolves) ->
                        val name = packageName ?: return@mapNotNull null
                        val appInfo = resolves.firstNotNullOfOrNull { it.activityInfo?.applicationInfo } ?: return@mapNotNull null
                        OpeningLinkState(
                            packageName = name,
                            label = appInfo.loadLabel(packageManager).toString(),
                            domains = listOf("http", "https"),
                            handlesLinks = true,
                        )
                    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            mutableOpeningLinks.value = links
        }
    }

private data class DefaultRoleSpec(
    val roleName: String,
    val title: String,
    val intent: Intent,
)

private val DEFAULT_ROLE_SPECS =
    listOf(
        DefaultRoleSpec(
            RoleManager.ROLE_BROWSER,
            "Browser app",
            Intent(Intent.ACTION_VIEW, Uri.parse("http://example.invalid")).addCategory(Intent.CATEGORY_BROWSABLE),
        ),
        DefaultRoleSpec(
            RoleManager.ROLE_HOME,
            "Home app",
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        ),
        DefaultRoleSpec(
            RoleManager.ROLE_DIALER,
            "Phone app",
            Intent(Intent.ACTION_DIAL),
        ),
        DefaultRoleSpec(
            RoleManager.ROLE_SMS,
            "SMS app",
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:555")),
        ),
        DefaultRoleSpec(
            RoleManager.ROLE_ASSISTANT,
            "Assistant app",
            Intent("android.service.voice.VoiceInteractionService"),
        ),
    )

private const val PREFERENCES_NAME = "applications_settings"
private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
private const val MAXIMUM_RECENT_APPS = 5
private const val RECENT_APPS_WINDOW_MILLIS = 7 * 24 * 60 * 60 * 1_000L
private const val UNUSED_APP_WINDOW_MILLIS = 90L * 24 * 60 * 60 * 1_000L
private const val APP_OP_MODE_IGNORED = 1
private const val APP_OP_MODE_ALLOWED = 0
private const val APP_OP_MODE_ERRORED = 2
private const val PACKAGES_DISABLED_ON_RESOURCE_OVERUSE = "packages_disabled_on_resource_overuse"
private const val PERMISSION_NETWORK_SETTINGS = "android.permission.NETWORK_SETTINGS"
private const val PERMISSION_MANAGE_APP_HIBERNATION = "android.permission.MANAGE_APP_HIBERNATION"
private const val PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG =
    "android.car.permission.CONTROL_CAR_WATCHDOG_CONFIG"
private const val PERMISSION_FLAG_USER_FIXED = 0x00000002
private const val PERMISSION_FLAG_SYSTEM_FIXED = 0x00000010
private const val PERMISSION_FLAG_POLICY_FIXED = 0x00000004
private const val PERMISSION_BIND_NOTIFICATION_LISTENER = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"

private val SpecialAccessType.requiredPermission: String
    get() =
        when (this) {
            SpecialAccessType.ALARMS_AND_REMINDERS -> "android.permission.SCHEDULE_EXACT_ALARM"
            SpecialAccessType.MODIFY_SYSTEM_SETTINGS -> "android.permission.WRITE_SETTINGS"
            SpecialAccessType.USAGE_ACCESS -> "android.permission.PACKAGE_USAGE_STATS"
            SpecialAccessType.WIFI_CONTROL -> "android.permission.CHANGE_WIFI_STATE"
            SpecialAccessType.NOTIFICATION_ACCESS -> PERMISSION_BIND_NOTIFICATION_LISTENER
            SpecialAccessType.PREMIUM_SMS -> "android.permission.SEND_SMS"
        }

private val SpecialAccessType.appOp: String
    get() =
        when (this) {
            SpecialAccessType.ALARMS_AND_REMINDERS -> "android:schedule_exact_alarm"
            SpecialAccessType.MODIFY_SYSTEM_SETTINGS -> "android:write_settings"
            SpecialAccessType.USAGE_ACCESS -> "android:get_usage_stats"
            SpecialAccessType.WIFI_CONTROL -> "android:change_wifi_state"
            SpecialAccessType.NOTIFICATION_ACCESS -> "android:notification_listener"
            SpecialAccessType.PREMIUM_SMS -> "android:write_sms"
        }

private val SpecialAccessType.deniedMode: Int
    get() =
        when (this) {
            SpecialAccessType.ALARMS_AND_REMINDERS,
            SpecialAccessType.MODIFY_SYSTEM_SETTINGS,
            -> APP_OP_MODE_ERRORED
            SpecialAccessType.USAGE_ACCESS,
            SpecialAccessType.WIFI_CONTROL,
            SpecialAccessType.NOTIFICATION_ACCESS,
            SpecialAccessType.PREMIUM_SMS,
            -> APP_OP_MODE_IGNORED
        }
