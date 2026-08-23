package com.android.car.settings.feature.location.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.core.common.rethrowIfCancellation
import com.android.car.settings.feature.location.domain.LocationPermissionApp
import com.android.car.settings.feature.location.domain.LocationProvider
import com.android.car.settings.feature.location.domain.LocationRecentAccess
import com.android.car.settings.feature.location.domain.LocationState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AndroidLocationPlatform
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : LocationPlatform {
        private val packageManager = context.packageManager
        private val locationManager = context.getSystemService(LocationManager::class.java)
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val mutableState = MutableStateFlow(LocationState())

        override val state: StateFlow<LocationState> = mutableState.asStateFlow()

        init {
            scope.launch { refresh() }
        }

        override suspend fun refresh(): ActionResult =
            execute {
                val adasSupported = LocationHiddenApiBridge.supportsAdasLocation(context)
                mutableState.value =
                    LocationState(
                        locationEnabled = LocationHiddenApiBridge.isLocationEnabled(context),
                        locationSupported = locationManager != null,
                        adasLocationSupported = adasSupported,
                        adasLocationEnabled =
                            adasSupported &&
                                LocationHiddenApiBridge.isAdasLocationEnabled(context),
                        permissionApps = loadPermissionApps(),
                        recentAccesses = loadRecentAccesses(),
                        providers =
                            locationManager?.allProviders.orEmpty().map { provider ->
                                LocationProvider(
                                    provider,
                                    runCatching {
                                        locationManager?.isProviderEnabled(provider) == true
                                    }.getOrDefault(false),
                                )
                            },
                    )
            }

        override suspend fun setLocationEnabled(enabled: Boolean): ActionResult =
            execute {
                check(locationManager != null) { "Location is not supported on this vehicle" }
                LocationHiddenApiBridge.setLocationEnabled(context, enabled)
                refreshOrThrow()
            }

        override suspend fun setAdasLocationEnabled(enabled: Boolean): ActionResult =
            execute {
                check(LocationHiddenApiBridge.supportsAdasLocation(context)) {
                    "ADAS location is not supported on this vehicle"
                }
                check(!enabled || LocationHiddenApiBridge.isLocationEnabled(context)) {
                    "Enable Location before enabling ADAS location"
                }
                LocationHiddenApiBridge.setAdasLocationEnabled(context, enabled)
                refreshOrThrow()
            }

        override suspend fun setAppPermission(
            packageName: String,
            granted: Boolean,
        ): ActionResult =
            execute {
                LocationHiddenApiBridge.setLocationPermissions(context, packageName, granted)
                refreshOrThrow()
            }

        private fun loadPermissionApps(): List<LocationPermissionApp> =
            packageManager
                .getInstalledPackages(
                    PackageManager.GET_PERMISSIONS or PackageManager.MATCH_DISABLED_COMPONENTS,
                ).asSequence()
                .filter { packageInfo ->
                    packageInfo.packageName != context.packageName &&
                        packageInfo.requestedPermissions.orEmpty().any {
                            it == Manifest.permission.ACCESS_COARSE_LOCATION ||
                                it == Manifest.permission.ACCESS_FINE_LOCATION
                        }
                }.mapNotNull { packageInfo ->
                    val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                    LocationPermissionApp(
                        packageName = packageInfo.packageName,
                        label = appInfo.loadLabel(packageManager).toString(),
                        granted =
                            packageManager.checkPermission(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                packageInfo.packageName,
                            ) == PackageManager.PERMISSION_GRANTED ||
                                packageManager.checkPermission(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    packageInfo.packageName,
                                ) == PackageManager.PERMISSION_GRANTED,
                        enabled = appInfo.enabled,
                    )
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                .toList()

        private fun loadRecentAccesses(): List<LocationRecentAccess> =
            LocationHiddenApiBridge
                .getRecentAccesses(context)
                .mapNotNull { access ->
                    val appInfo =
                        runCatching {
                            packageManager.getApplicationInfo(access.packageName, 0)
                        }.getOrNull() ?: return@mapNotNull null
                    LocationRecentAccess(
                        packageName = access.packageName,
                        label = appInfo.loadLabel(packageManager).toString(),
                        lastAccessMillis = access.lastAccessMillis,
                    )
                }.sortedByDescending(LocationRecentAccess::lastAccessMillis)

        private suspend fun execute(block: suspend () -> Unit): ActionResult =
            withContext(dispatcher) {
                try {
                    block()
                    ActionResult.Success
                } catch (throwable: Throwable) {
                    throwable.rethrowIfCancellation()
                    val message = throwable.cause?.message ?: throwable.message ?: "Unknown location error"
                    mutableState.value = mutableState.value.copy(lastError = message)
                    ActionResult.Failure(message, throwable)
                }
            }

        private suspend fun refreshOrThrow() {
            check(refresh() == ActionResult.Success) { "Unable to refresh location state" }
        }
    }
