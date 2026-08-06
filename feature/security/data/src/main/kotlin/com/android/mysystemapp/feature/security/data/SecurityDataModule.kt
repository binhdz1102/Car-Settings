package com.android.car.settings.feature.security.data

import com.android.car.settings.feature.security.domain.SecurityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SecurityDataModule {
    @Binds
    abstract fun bindPlatform(implementation: AndroidSecurityPlatform): SecurityPlatform

    @Binds
    abstract fun bindRepository(implementation: SecurityRepositoryImpl): SecurityRepository
}
