package com.android.car.settings.feature.assistantvoice.data

import com.android.car.settings.feature.assistantvoice.domain.AssistantVoiceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AssistantVoiceDataModule {
    @Binds
    abstract fun bindPlatform(implementation: AndroidAssistantVoicePlatform): AssistantVoicePlatform

    @Binds
    abstract fun bindRepository(implementation: AssistantVoiceRepositoryImpl): AssistantVoiceRepository
}
