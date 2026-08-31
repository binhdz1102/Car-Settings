package com.android.car.settings.feature.assistantvoice.data

import com.android.car.settings.feature.assistantvoice.domain.AssistantVoiceRepository
import com.android.car.settings.feature.assistantvoice.domain.VoiceInputOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AssistantVoiceRepositoryImpl
    @Inject
    constructor(
        private val platform: AssistantVoicePlatform,
    ) : AssistantVoiceRepository {
        override val state = platform.state

        override suspend fun refresh() = platform.refresh()

        override suspend fun setTextFromScreenEnabled(enabled: Boolean) = platform.setTextFromScreenEnabled(enabled)

        override suspend fun setScreenshotEnabled(enabled: Boolean) = platform.setScreenshotEnabled(enabled)

        override suspend fun setDefaultVoiceInput(option: VoiceInputOption) = platform.setDefaultVoiceInput(option)
    }
