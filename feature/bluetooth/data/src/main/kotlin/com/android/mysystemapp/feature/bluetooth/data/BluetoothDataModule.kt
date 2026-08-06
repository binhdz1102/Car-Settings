package com.android.car.settings.feature.bluetooth.data

import com.android.car.settings.feature.bluetooth.domain.BluetoothRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BluetoothDataModule {
    @Binds
    @Singleton
    abstract fun bindBluetoothPlatform(implementation: AndroidBluetoothPlatform): BluetoothPlatform

    @Binds
    @Singleton
    abstract fun bindBluetoothRepository(implementation: BluetoothRepositoryImpl): BluetoothRepository
}
