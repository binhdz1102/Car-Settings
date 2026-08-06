package com.android.car.settings.feature.bluetooth.data

import com.android.car.settings.feature.bluetooth.domain.BluetoothProfileType
import com.android.car.settings.feature.bluetooth.domain.BluetoothRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BluetoothRepositoryImpl
    @Inject
    constructor(
        private val platform: BluetoothPlatform,
    ) : BluetoothRepository {
        override val state = platform.state

        override suspend fun refresh() = platform.refresh()

        override suspend fun setBluetoothEnabled(enabled: Boolean) = platform.setEnabled(enabled)

        override suspend fun startDiscovery() = platform.startDiscovery()

        override suspend fun stopDiscovery() = platform.stopDiscovery()

        override suspend fun pair(address: String) = platform.pair(address)

        override suspend fun cancelPairing(address: String) = platform.cancelPairing(address)

        override suspend fun unpair(address: String) = platform.unpair(address)

        override suspend fun connect(address: String) = platform.connect(address)

        override suspend fun disconnect(address: String) = platform.disconnect(address)

        override suspend fun selectDevice(address: String) = platform.selectDevice(address)

        override suspend fun renameDevice(
            address: String,
            alias: String,
        ) = platform.renameDevice(address, alias)

        override suspend fun renameAdapter(name: String) = platform.renameAdapter(name)

        override suspend fun setDiscoverable(
            enabled: Boolean,
            durationSeconds: Int,
        ) = platform.setDiscoverable(enabled, durationSeconds)

        override suspend fun setProfileEnabled(
            address: String,
            profile: BluetoothProfileType,
            enabled: Boolean,
        ) = platform.setProfileEnabled(address, profile, enabled)
    }
