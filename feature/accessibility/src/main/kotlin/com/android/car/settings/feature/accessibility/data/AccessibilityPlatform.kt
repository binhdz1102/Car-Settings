package com.android.car.settings.feature.accessibility.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.accessibility.domain.AccessibilityState
import com.android.car.settings.feature.accessibility.domain.CaptionTextSize
import com.android.car.settings.feature.accessibility.domain.CaptionTextStyle
import kotlinx.coroutines.flow.StateFlow

internal interface AccessibilityPlatform {
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
