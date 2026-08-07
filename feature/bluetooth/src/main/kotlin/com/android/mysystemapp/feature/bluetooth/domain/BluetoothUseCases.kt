package com.android.car.settings.feature.bluetooth.domain

import javax.inject.Inject

class BluetoothUseCases
    @Inject
    constructor(
        private val repository: BluetoothRepository,
    ) {
        fun observeState() = repository.state

        suspend fun refresh() = repository.refresh()

        suspend fun setEnabled(enabled: Boolean) = repository.setBluetoothEnabled(enabled)

        suspend fun startDiscovery() = repository.startDiscovery()

        suspend fun stopDiscovery() = repository.stopDiscovery()

        suspend fun pair(address: String) = repository.pair(address)

        suspend fun cancelPairing(address: String) = repository.cancelPairing(address)

        suspend fun unpair(address: String) = repository.unpair(address)

        suspend fun connect(address: String) = repository.connect(address)

        suspend fun disconnect(address: String) = repository.disconnect(address)

        suspend fun selectDevice(address: String) = repository.selectDevice(address)

        suspend fun renameDevice(
            address: String,
            alias: String,
        ) = repository.renameDevice(address, alias)

        suspend fun renameAdapter(name: String) = repository.renameAdapter(name)

        suspend fun setDiscoverable(
            enabled: Boolean,
            durationSeconds: Int = 120,
        ) = repository.setDiscoverable(enabled, durationSeconds)

        suspend fun setProfileEnabled(
            address: String,
            profile: BluetoothProfileType,
            enabled: Boolean,
        ) = repository.setProfileEnabled(address, profile, enabled)
    }
