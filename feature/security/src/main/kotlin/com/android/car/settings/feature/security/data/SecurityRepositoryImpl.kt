package com.android.car.settings.feature.security.data

import com.android.car.settings.feature.security.domain.SecurityLockType
import com.android.car.settings.feature.security.domain.SecurityRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SecurityRepositoryImpl
    @Inject
    constructor(
        private val platform: SecurityPlatform,
    ) : SecurityRepository {
        override val state = platform.state

        override suspend fun refresh() = platform.refresh()

        override suspend fun setLock(
            type: SecurityLockType,
            currentCredential: String,
            newCredential: String,
        ) = platform.setLock(type, currentCredential, newCredential)

        override suspend fun resetCredentials(currentCredential: String) = platform.resetCredentials(currentCredential)

        override suspend fun removeDeviceAdmin(componentName: String) = platform.removeDeviceAdmin(componentName)
    }
