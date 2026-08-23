package com.android.car.settings.feature.assistantvoice.domain

import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.flow.StateFlow

interface AssistantVoiceRepository {
    val state: StateFlow<AssistantVoiceState>

    suspend fun refresh(): ActionResult

    suspend fun setTextFromScreenEnabled(enabled: Boolean): ActionResult

    suspend fun setScreenshotEnabled(enabled: Boolean): ActionResult

    suspend fun setDefaultVoiceInput(option: VoiceInputOption): ActionResult
}
