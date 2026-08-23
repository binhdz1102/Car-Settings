package com.android.car.settings.feature.location.data

import com.android.car.settings.feature.location.domain.LocationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LocationDataModule {
    @Binds
    abstract fun bindPlatform(implementation: AndroidLocationPlatform): LocationPlatform

    @Binds
    abstract fun bindRepository(implementation: LocationRepositoryImpl): LocationRepository
}
