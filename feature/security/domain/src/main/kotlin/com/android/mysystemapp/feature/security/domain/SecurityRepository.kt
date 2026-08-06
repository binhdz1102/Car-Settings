package com.android.car.settings.feature.security.domain

import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.flow.Flow

interface SecurityRepository {
    val state: Flow<SecurityState>

    suspend fun refresh(): ActionResult

    suspend fun setLock(
        type: SecurityLockType,
        currentCredential: String,
        newCredential: String,
    ): ActionResult

    suspend fun resetCredentials(currentCredential: String): ActionResult

    suspend fun removeDeviceAdmin(componentName: String): ActionResult
}
