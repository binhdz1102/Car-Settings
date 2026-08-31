package com.android.car.settings.feature.hvac.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.hvac.domain.ClimateState
import kotlinx.coroutines.flow.StateFlow

internal interface HvacPlatform {
    val state: StateFlow<ClimateState>

    suspend fun refresh(): ActionResult

    suspend fun setBoolean(
        key: String,
        value: Boolean,
    ): ActionResult

    suspend fun setInt(
        key: String,
        value: Int,
    ): ActionResult

    suspend fun setFloat(
        key: String,
        value: Float,
    ): ActionResult
}
