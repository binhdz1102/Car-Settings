package com.android.car.settings.feature.location.data

import com.android.car.settings.feature.location.domain.LocationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class LocationRepositoryImpl
    @Inject
    constructor(
        private val platform: LocationPlatform,
    ) : LocationRepository {
        override val state = platform.state

        override suspend fun refresh() = platform.refresh()

        override suspend fun setLocationEnabled(enabled: Boolean) = platform.setLocationEnabled(enabled)

        override suspend fun setAdasLocationEnabled(enabled: Boolean) = platform.setAdasLocationEnabled(enabled)

        override suspend fun setAppPermission(
            packageName: String,
            granted: Boolean,
        ) = platform.setAppPermission(packageName, granted)
    }
