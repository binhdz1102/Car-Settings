package com.android.car.settings.feature.profileaccounts.data

import com.android.car.settings.feature.profileaccounts.domain.ProfileAccountsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ProfileAccountsDataModule {
    @Binds
    abstract fun bindPlatform(implementation: AndroidProfileAccountsPlatform): ProfileAccountsPlatform

    @Binds
    abstract fun bindRepository(implementation: ProfileAccountsRepositoryImpl): ProfileAccountsRepository
}
