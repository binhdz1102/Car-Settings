package com.android.car.settings.feature.location.domain

import javax.inject.Inject

class LocationUseCases
    @Inject
    constructor(
        private val repository: LocationRepository,
    ) {
        fun observeState() = repository.state

        suspend fun refresh() = repository.refresh()

        suspend fun setLocationEnabled(enabled: Boolean) = repository.setLocationEnabled(enabled)

        suspend fun setAdasLocationEnabled(enabled: Boolean) = repository.setAdasLocationEnabled(enabled)

        suspend fun setAppPermission(
            packageName: String,
            granted: Boolean,
        ) = repository.setAppPermission(packageName, granted)
    }
