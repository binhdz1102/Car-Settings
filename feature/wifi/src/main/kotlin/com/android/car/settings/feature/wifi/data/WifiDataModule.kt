package com.android.car.settings.feature.wifi.data

import com.android.car.settings.feature.wifi.domain.WifiRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WifiDataModule {
    @Binds
    @Singleton
    abstract fun bindWifiPlatform(implementation: AndroidWifiPlatform): WifiPlatform

    @Binds
    @Singleton
    abstract fun bindWifiRepository(implementation: WifiRepositoryImpl): WifiRepository
}
