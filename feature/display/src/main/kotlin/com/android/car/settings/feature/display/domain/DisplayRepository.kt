package com.android.car.settings.feature.display.domain

import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.flow.Flow

interface DisplayRepository {
    val state: Flow<DisplayState>

    suspend fun refresh(): ActionResult

    suspend fun setBrightness(gamma: Int): ActionResult

    suspend fun setAdaptiveBrightness(enabled: Boolean): ActionResult

    suspend fun setThemeMode(mode: ThemeMode): ActionResult

    suspend fun setAutoTime(enabled: Boolean): ActionResult

    suspend fun setAutoTimeZone(enabled: Boolean): ActionResult

    suspend fun setUse24HourFormat(enabled: Boolean): ActionResult

    suspend fun setManualTime(epochMillis: Long): ActionResult

    suspend fun setManualTimeZone(timeZoneId: String): ActionResult
}
