package com.android.car.settings.feature.security.domain

import javax.inject.Inject

class SecurityUseCases @Inject constructor(private val repository: SecurityRepository) {
    fun observeState() = repository.state
    suspend fun refresh() = repository.refresh()
    suspend fun setLock(type: SecurityLockType, currentCredential: String, newCredential: String) =
        repository.setLock(type, currentCredential, newCredential)
    suspend fun resetCredentials(currentCredential: String) = repository.resetCredentials(currentCredential)
    suspend fun removeDeviceAdmin(componentName: String) = repository.removeDeviceAdmin(componentName)
}
