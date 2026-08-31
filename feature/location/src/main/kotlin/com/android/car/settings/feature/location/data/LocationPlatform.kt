package com.android.car.settings.feature.location.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.location.domain.LocationState
import kotlinx.coroutines.flow.StateFlow

internal interface LocationPlatform {
    val state: StateFlow<LocationState>

    suspend fun refresh(): ActionResult

    suspend fun setLocationEnabled(enabled: Boolean): ActionResult

    suspend fun setAdasLocationEnabled(enabled: Boolean): ActionResult

    suspend fun setAppPermission(
        packageName: String,
        granted: Boolean,
    ): ActionResult
}
