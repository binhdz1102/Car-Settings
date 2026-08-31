package com.android.car.settings.feature.accessibility.data

import com.android.car.settings.feature.accessibility.domain.AccessibilityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AccessibilityDataModule {
    @Binds
    abstract fun bindPlatform(implementation: AndroidAccessibilityPlatform): AccessibilityPlatform

    @Binds
    abstract fun bindRepository(implementation: AccessibilityRepositoryImpl): AccessibilityRepository
}
