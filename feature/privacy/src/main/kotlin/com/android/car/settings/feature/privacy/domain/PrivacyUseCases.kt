package com.android.car.settings.feature.privacy.domain

import javax.inject.Inject

class PrivacyUseCases
    @Inject
    constructor(
        private val repository: PrivacyRepository,
    ) {
        fun observeState() = repository.state

        suspend fun refresh() = repository.refresh()

        suspend fun setMicrophoneAccessEnabled(enabled: Boolean) = repository.setMicrophoneAccessEnabled(enabled)

        suspend fun setCameraAccessEnabled(enabled: Boolean) = repository.setCameraAccessEnabled(enabled)

        suspend fun setLocationEnabled(enabled: Boolean) = repository.setLocationEnabled(enabled)

        suspend fun selectPermissionType(type: PrivacyPermissionType) = repository.selectPermissionType(type)

        suspend fun setAppPermission(
            packageName: String,
            type: PrivacyPermissionType,
            granted: Boolean,
        ) = repository.setAppPermission(packageName, type, granted)
    }
