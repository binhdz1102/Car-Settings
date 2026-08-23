package com.android.car.settings.feature.privacy.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.privacy.domain.PrivacyPermissionType
import com.android.car.settings.feature.privacy.domain.PrivacyState
import kotlinx.coroutines.flow.StateFlow

internal interface PrivacyPlatform {
    val state: StateFlow<PrivacyState>

    suspend fun refresh(): ActionResult

    suspend fun setMicrophoneAccessEnabled(enabled: Boolean): ActionResult

    suspend fun setCameraAccessEnabled(enabled: Boolean): ActionResult

    suspend fun setLocationEnabled(enabled: Boolean): ActionResult

    suspend fun selectPermissionType(type: PrivacyPermissionType): ActionResult

    suspend fun setAppPermission(
        packageName: String,
        type: PrivacyPermissionType,
        granted: Boolean,
    ): ActionResult
}
