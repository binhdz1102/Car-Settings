package com.android.car.settings.feature.hvac.data

import com.android.car.settings.feature.hvac.domain.HvacRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HvacDataModule {
    @Binds
    @Singleton
    abstract fun bindPlatform(implementation: AndroidHvacPlatform): HvacPlatform

    @Binds
    @Singleton
    abstract fun bindRepository(implementation: HvacRepositoryImpl): HvacRepository
}
