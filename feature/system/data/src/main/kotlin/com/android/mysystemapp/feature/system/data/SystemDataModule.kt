package com.android.car.settings.feature.system.data

import com.android.car.settings.feature.system.domain.SystemRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SystemDataModule {
    @Binds
    abstract fun bindPlatform(implementation: AndroidSystemPlatform): SystemPlatform

    @Binds
    abstract fun bindRepository(implementation: SystemRepositoryImpl): SystemRepository
}
