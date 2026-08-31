package com.android.car.settings.feature.applications.data

import com.android.car.settings.feature.applications.domain.ApplicationsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ApplicationsDataModule {
    @Binds
    abstract fun bindApplicationsPlatform(implementation: AndroidApplicationsPlatform): ApplicationsPlatform

    @Binds
    abstract fun bindApplicationsRepository(implementation: ApplicationsRepositoryImpl): ApplicationsRepository
}
