package com.android.car.settings.feature.location.domain

import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.flow.StateFlow

interface LocationRepository {
    val state: StateFlow<LocationState>

    suspend fun refresh(): ActionResult

    suspend fun setLocationEnabled(enabled: Boolean): ActionResult

    suspend fun setAdasLocationEnabled(enabled: Boolean): ActionResult

    suspend fun setAppPermission(
        packageName: String,
        granted: Boolean,
    ): ActionResult
}
