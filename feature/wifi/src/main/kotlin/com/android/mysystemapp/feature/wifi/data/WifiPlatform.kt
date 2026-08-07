package com.android.car.settings.feature.wifi.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.wifi.domain.HotspotConfiguration
import com.android.car.settings.feature.wifi.domain.MeteredOverride
import com.android.car.settings.feature.wifi.domain.WifiCredentials
import com.android.car.settings.feature.wifi.domain.WifiNetwork
import com.android.car.settings.feature.wifi.domain.WifiSecurity
import com.android.car.settings.feature.wifi.domain.WifiState
import kotlinx.coroutines.flow.StateFlow

internal interface WifiPlatform {
    val state: StateFlow<WifiState>

    suspend fun refresh(): ActionResult

    suspend fun setWifiEnabled(enabled: Boolean): ActionResult

    suspend fun startScan(): ActionResult

    suspend fun connect(
        network: WifiNetwork,
        credentials: WifiCredentials,
    ): ActionResult

    suspend fun addNetwork(
        ssid: String,
        security: WifiSecurity,
        credentials: WifiCredentials,
        hidden: Boolean,
    ): ActionResult

    suspend fun disconnect(): ActionResult

    suspend fun forget(networkId: Int): ActionResult

    suspend fun setAutoJoin(
        networkId: Int,
        enabled: Boolean,
    ): ActionResult

    suspend fun setMeteredOverride(
        networkId: Int,
        override: MeteredOverride,
    ): ActionResult

    suspend fun setHotspotEnabled(enabled: Boolean): ActionResult

    suspend fun updateHotspotConfiguration(configuration: HotspotConfiguration): ActionResult

    suspend fun setScanAlwaysAvailable(enabled: Boolean): ActionResult

    suspend fun setWakeupEnabled(enabled: Boolean): ActionResult

    suspend fun setOpenNetworkNotificationEnabled(enabled: Boolean): ActionResult
}
