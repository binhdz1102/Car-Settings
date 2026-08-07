package com.android.car.settings.feature.wifi.domain

import javax.inject.Inject

class WifiUseCases
    @Inject
    constructor(
        private val repository: WifiRepository,
    ) {
        fun observeState() = repository.state

        suspend fun refresh() = repository.refresh()

        suspend fun setEnabled(enabled: Boolean) = repository.setWifiEnabled(enabled)

        suspend fun scan() = repository.startScan()

        suspend fun connect(
            network: WifiNetwork,
            credentials: WifiCredentials = WifiCredentials(),
        ) = repository.connect(network, credentials)

        suspend fun addNetwork(
            ssid: String,
            security: WifiSecurity,
            credentials: WifiCredentials,
            hidden: Boolean,
        ) = repository.addNetwork(ssid, security, credentials, hidden)

        suspend fun disconnect() = repository.disconnect()

        suspend fun forget(networkId: Int) = repository.forget(networkId)

        suspend fun setAutoJoin(
            networkId: Int,
            enabled: Boolean,
        ) = repository.setAutoJoin(networkId, enabled)

        suspend fun setMeteredOverride(
            networkId: Int,
            override: MeteredOverride,
        ) = repository.setMeteredOverride(networkId, override)

        suspend fun setHotspotEnabled(enabled: Boolean) = repository.setHotspotEnabled(enabled)

        suspend fun updateHotspotConfiguration(configuration: HotspotConfiguration) = repository.updateHotspotConfiguration(configuration)

        suspend fun setScanAlwaysAvailable(enabled: Boolean) = repository.setScanAlwaysAvailable(enabled)

        suspend fun setWakeupEnabled(enabled: Boolean) = repository.setWakeupEnabled(enabled)

        suspend fun setOpenNetworkNotificationEnabled(enabled: Boolean) = repository.setOpenNetworkNotificationEnabled(enabled)
    }
