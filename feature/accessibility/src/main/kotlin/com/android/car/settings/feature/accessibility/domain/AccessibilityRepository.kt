package com.android.car.settings.feature.accessibility.domain

import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.flow.StateFlow

interface AccessibilityRepository {
    val state: StateFlow<AccessibilityState>

    suspend fun refresh(): ActionResult

    suspend fun setCaptionsEnabled(enabled: Boolean): ActionResult

    suspend fun setCaptionTextSize(size: CaptionTextSize): ActionResult

    suspend fun setCaptionTextStyle(style: CaptionTextStyle): ActionResult

    suspend fun setServiceEnabled(
        componentName: String,
        enabled: Boolean,
    ): ActionResult
}
