package com.android.car.settings.feature.security.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.security.domain.SecurityLockType
import com.android.car.settings.feature.security.domain.SecurityState
import kotlinx.coroutines.flow.StateFlow

internal interface SecurityPlatform {
    val state: StateFlow<SecurityState>

    suspend fun refresh(): ActionResult

    suspend fun setLock(
        type: SecurityLockType,
        currentCredential: String,
        newCredential: String,
    ): ActionResult

    suspend fun resetCredentials(currentCredential: String): ActionResult

    suspend fun removeDeviceAdmin(componentName: String): ActionResult
}
