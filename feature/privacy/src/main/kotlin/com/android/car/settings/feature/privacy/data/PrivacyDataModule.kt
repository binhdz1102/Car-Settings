package com.android.car.settings.feature.privacy.data

import com.android.car.settings.feature.privacy.domain.PrivacyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PrivacyDataModule {
    @Binds
    abstract fun bindPlatform(implementation: AndroidPrivacyPlatform): PrivacyPlatform

    @Binds
    abstract fun bindRepository(implementation: PrivacyRepositoryImpl): PrivacyRepository
}
