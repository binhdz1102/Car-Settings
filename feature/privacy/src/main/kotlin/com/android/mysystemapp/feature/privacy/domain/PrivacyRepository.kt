package com.android.car.settings.feature.privacy.domain

import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.flow.Flow

interface PrivacyRepository {
    val state: Flow<PrivacyState>

    suspend fun refresh(): ActionResult

    suspend fun setMicrophoneAccessEnabled(enabled: Boolean): ActionResult

    suspend fun setCameraAccessEnabled(enabled: Boolean): ActionResult

    suspend fun setLocationEnabled(enabled: Boolean): ActionResult

    suspend fun selectPermissionType(type: PrivacyPermissionType): ActionResult

    suspend fun setAppPermission(packageName: String, type: PrivacyPermissionType, granted: Boolean): ActionResult
}
