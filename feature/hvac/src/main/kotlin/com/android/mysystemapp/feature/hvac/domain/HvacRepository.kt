package com.android.car.settings.feature.hvac.domain

import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.flow.Flow

interface HvacRepository {
    val state: Flow<ClimateState>

    suspend fun refresh(): ActionResult

    suspend fun setBoolean(key: String, value: Boolean): ActionResult

    suspend fun setInt(key: String, value: Int): ActionResult

    suspend fun setFloat(key: String, value: Float): ActionResult
}
