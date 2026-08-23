package com.android.car.settings.feature.display.data

import com.android.car.settings.feature.display.domain.DisplayRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DisplayDataModule {
    @Binds
    abstract fun bindDisplayPlatform(implementation: AndroidDisplayPlatform): DisplayPlatform

    @Binds
    abstract fun bindDisplayRepository(implementation: DisplayRepositoryImpl): DisplayRepository
}
