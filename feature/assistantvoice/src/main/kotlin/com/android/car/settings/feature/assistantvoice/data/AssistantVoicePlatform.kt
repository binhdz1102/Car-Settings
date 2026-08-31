package com.android.car.settings.feature.assistantvoice.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.assistantvoice.domain.AssistantVoiceState
import com.android.car.settings.feature.assistantvoice.domain.VoiceInputOption
import kotlinx.coroutines.flow.StateFlow

internal interface AssistantVoicePlatform {
    val state: StateFlow<AssistantVoiceState>

    suspend fun refresh(): ActionResult

    suspend fun setTextFromScreenEnabled(enabled: Boolean): ActionResult

    suspend fun setScreenshotEnabled(enabled: Boolean): ActionResult

    suspend fun setDefaultVoiceInput(option: VoiceInputOption): ActionResult
}
