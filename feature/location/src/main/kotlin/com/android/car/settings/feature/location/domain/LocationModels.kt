package com.android.car.settings.feature.location.domain

data class LocationPermissionApp(
    val packageName: String,
    val label: String,
    val granted: Boolean,
    val enabled: Boolean,
)

data class LocationRecentAccess(
    val packageName: String,
    val label: String,
    val lastAccessMillis: Long,
)

data class LocationProvider(
    val name: String,
    val enabled: Boolean,
)

data class LocationState(
    val locationEnabled: Boolean = false,
    val locationSupported: Boolean = true,
    val adasLocationSupported: Boolean = false,
    val adasLocationEnabled: Boolean = false,
    val permissionApps: List<LocationPermissionApp> = emptyList(),
    val recentAccesses: List<LocationRecentAccess> = emptyList(),
    val providers: List<LocationProvider> = emptyList(),
    val accessDisclaimer: String = "Location can be used by apps and vehicle services.",
    val lastError: String? = null,
)
