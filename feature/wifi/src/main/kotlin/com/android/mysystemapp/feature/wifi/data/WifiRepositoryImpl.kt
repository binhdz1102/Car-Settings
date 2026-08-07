package com.android.car.settings.feature.wifi.data

import com.android.car.settings.feature.wifi.domain.HotspotConfiguration
import com.android.car.settings.feature.wifi.domain.MeteredOverride
import com.android.car.settings.feature.wifi.domain.WifiCredentials
import com.android.car.settings.feature.wifi.domain.WifiNetwork
import com.android.car.settings.feature.wifi.domain.WifiRepository
import com.android.car.settings.feature.wifi.domain.WifiSecurity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class WifiRepositoryImpl
    @Inject
    constructor(
        private val platform: WifiPlatform,
    ) : WifiRepository {
        override val state = platform.state

        override suspend fun refresh() = platform.refresh()

        override suspend fun setWifiEnabled(enabled: Boolean) = platform.setWifiEnabled(enabled)

        override suspend fun startScan() = platform.startScan()

        override suspend fun connect(
            network: WifiNetwork,
            credentials: WifiCredentials,
        ) = platform.connect(network, credentials)

        override suspend fun addNetwork(
            ssid: String,
            security: WifiSecurity,
            credentials: WifiCredentials,
            hidden: Boolean,
        ) = platform.addNetwork(ssid, security, credentials, hidden)

        override suspend fun disconnect() = platform.disconnect()

        override suspend fun forget(networkId: Int) = platform.forget(networkId)

        override suspend fun setAutoJoin(
            networkId: Int,
            enabled: Boolean,
        ) = platform.setAutoJoin(networkId, enabled)

        override suspend fun setMeteredOverride(
            networkId: Int,
            override: MeteredOverride,
        ) = platform.setMeteredOverride(networkId, override)

        override suspend fun setHotspotEnabled(enabled: Boolean) = platform.setHotspotEnabled(enabled)

        override suspend fun updateHotspotConfiguration(configuration: HotspotConfiguration) =
            platform.updateHotspotConfiguration(configuration)

        override suspend fun setScanAlwaysAvailable(enabled: Boolean) = platform.setScanAlwaysAvailable(enabled)

        override suspend fun setWakeupEnabled(enabled: Boolean) = platform.setWakeupEnabled(enabled)

        override suspend fun setOpenNetworkNotificationEnabled(enabled: Boolean) = platform.setOpenNetworkNotificationEnabled(enabled)
    }
