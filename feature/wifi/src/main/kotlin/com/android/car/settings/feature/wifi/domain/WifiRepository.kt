package com.android.car.settings.feature.wifi.domain

import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.flow.Flow

interface WifiRepository {
    val state: Flow<WifiState>

    suspend fun refresh(): ActionResult

    suspend fun setWifiEnabled(enabled: Boolean): ActionResult

    suspend fun startScan(): ActionResult

    suspend fun connect(
        network: WifiNetwork,
        credentials: WifiCredentials = WifiCredentials(),
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

    suspend fun setCellularFallbackEnabled(enabled: Boolean): ActionResult

    suspend fun setPersistentTetheringEnabled(enabled: Boolean): ActionResult

    suspend fun setMobileDataEnabled(enabled: Boolean): ActionResult

    suspend fun setDataRoamingEnabled(enabled: Boolean): ActionResult

    suspend fun setDefaultDataSubscription(subscriptionId: Int): ActionResult
}
