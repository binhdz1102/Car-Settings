package com.android.car.settings.feature.sound.data

import com.android.car.settings.feature.sound.domain.SoundRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SoundDataModule {
    @Binds
    @Singleton
    abstract fun bindSoundPlatform(implementation: AndroidSoundPlatform): SoundPlatform

    @Binds
    @Singleton
    abstract fun bindSoundRepository(implementation: SoundRepositoryImpl): SoundRepository
}
