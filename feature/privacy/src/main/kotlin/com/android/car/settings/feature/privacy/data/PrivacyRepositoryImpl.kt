package com.android.car.settings.feature.privacy.data

import com.android.car.settings.feature.privacy.domain.PrivacyPermissionType
import com.android.car.settings.feature.privacy.domain.PrivacyRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PrivacyRepositoryImpl
    @Inject
    constructor(
        private val platform: PrivacyPlatform,
    ) : PrivacyRepository {
        override val state = platform.state

        override suspend fun refresh() = platform.refresh()

        override suspend fun setMicrophoneAccessEnabled(enabled: Boolean) = platform.setMicrophoneAccessEnabled(enabled)

        override suspend fun setCameraAccessEnabled(enabled: Boolean) = platform.setCameraAccessEnabled(enabled)

        override suspend fun setLocationEnabled(enabled: Boolean) = platform.setLocationEnabled(enabled)

        override suspend fun selectPermissionType(type: PrivacyPermissionType) = platform.selectPermissionType(type)

        override suspend fun setAppPermission(
            packageName: String,
            type: PrivacyPermissionType,
            granted: Boolean,
        ) = platform.setAppPermission(packageName, type, granted)
    }
