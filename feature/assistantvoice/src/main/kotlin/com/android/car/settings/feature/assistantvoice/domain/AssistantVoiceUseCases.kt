package com.android.car.settings.feature.assistantvoice.domain

import javax.inject.Inject

class AssistantVoiceUseCases
    @Inject
    constructor(
        private val repository: AssistantVoiceRepository,
    ) {
        fun observeState() = repository.state

        suspend fun refresh() = repository.refresh()

        suspend fun setTextFromScreenEnabled(enabled: Boolean) = repository.setTextFromScreenEnabled(enabled)

        suspend fun setScreenshotEnabled(enabled: Boolean) = repository.setScreenshotEnabled(enabled)

        suspend fun setDefaultVoiceInput(option: VoiceInputOption) = repository.setDefaultVoiceInput(option)
    }
