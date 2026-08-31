package com.android.car.settings.feature.wifi.domain

import com.android.car.settings.core.common.ActionResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WifiUseCasesTest {
    private val repository = RecordingWifiRepository()
    private val useCases = WifiUseCases(repository)

    @Test
    fun `all commands are delegated with their arguments`() =
        runTest {
            val network =
                WifiNetwork(
                    key = "Garage|WPA2",
                    networkId = 7,
                    ssid = "Garage",
                    security = WifiSecurity.WPA2_PERSONAL,
                    signalLevel = 4,
                    rssi = -42,
                    isSaved = true,
                    isConnected = false,
                )
            val credentials = WifiCredentials(password = "correct horse")
            val hotspot = HotspotConfiguration(ssid = "Vehicle AP", password = "12345678")

            useCases.refresh()
            useCases.setEnabled(true)
            useCases.scan()
            useCases.connect(network, credentials)
            useCases.addNetwork("Hidden", WifiSecurity.WPA3_PERSONAL, credentials, true)
            useCases.disconnect()
            useCases.forget(7)
            useCases.setAutoJoin(7, false)
            useCases.setMeteredOverride(7, MeteredOverride.METERED)
            useCases.setHotspotEnabled(true)
            useCases.updateHotspotConfiguration(hotspot)
            useCases.setScanAlwaysAvailable(true)
            useCases.setWakeupEnabled(true)
            useCases.setOpenNetworkNotificationEnabled(false)
            useCases.setCellularFallbackEnabled(true)
            useCases.setPersistentTetheringEnabled(true)
            useCases.setMobileDataEnabled(true)
            useCases.setDataRoamingEnabled(false)
            useCases.setDefaultDataSubscription(1)

            assertThat(repository.calls)
                .containsExactly(
                    "refresh",
                    "wifi:true",
                    "scan",
                    "connect:Garage:correct horse",
                    "add:Hidden:WPA3_PERSONAL:true",
                    "disconnect",
                    "forget:7",
                    "autojoin:7:false",
                    "metered:7:METERED",
                    "hotspot:true",
                    "hotspotConfig:Vehicle AP",
                    "scanAlways:true",
                    "wakeup:true",
                    "openNotification:false",
                    "cellularFallback:true",
                    "persistentTethering:true",
                    "mobileData:true",
                    "roaming:false",
                    "defaultData:1",
                ).inOrder()
        }

    @Test
    fun `observe state exposes repository flow`() =
        runTest {
            val enabled = WifiState(radioState = WifiRadioState.ENABLED)
            repository.mutableState.value = enabled

            assertThat(useCases.observeState().first()).isEqualTo(enabled)
        }
}

private class RecordingWifiRepository : WifiRepository {
    val calls = mutableListOf<String>()
    val mutableState = MutableStateFlow(WifiState())
    override val state = mutableState

    override suspend fun refresh() = success("refresh")

    override suspend fun setWifiEnabled(enabled: Boolean) = success("wifi:$enabled")

    override suspend fun startScan() = success("scan")

    override suspend fun connect(
        network: WifiNetwork,
        credentials: WifiCredentials,
    ) = success("connect:${network.ssid}:${credentials.password}")

    override suspend fun addNetwork(
        ssid: String,
        security: WifiSecurity,
        credentials: WifiCredentials,
        hidden: Boolean,
    ) = success("add:$ssid:$security:$hidden")

    override suspend fun disconnect() = success("disconnect")

    override suspend fun forget(networkId: Int) = success("forget:$networkId")

    override suspend fun setAutoJoin(
        networkId: Int,
        enabled: Boolean,
    ) = success("autojoin:$networkId:$enabled")

    override suspend fun setMeteredOverride(
        networkId: Int,
        override: MeteredOverride,
    ) = success("metered:$networkId:$override")

    override suspend fun setHotspotEnabled(enabled: Boolean) = success("hotspot:$enabled")

    override suspend fun updateHotspotConfiguration(configuration: HotspotConfiguration) = success("hotspotConfig:${configuration.ssid}")

    override suspend fun setScanAlwaysAvailable(enabled: Boolean) = success("scanAlways:$enabled")

    override suspend fun setWakeupEnabled(enabled: Boolean) = success("wakeup:$enabled")

    override suspend fun setOpenNetworkNotificationEnabled(enabled: Boolean) = success("openNotification:$enabled")

    override suspend fun setCellularFallbackEnabled(enabled: Boolean) = success("cellularFallback:$enabled")

    override suspend fun setPersistentTetheringEnabled(enabled: Boolean) = success("persistentTethering:$enabled")

    override suspend fun setMobileDataEnabled(enabled: Boolean) = success("mobileData:$enabled")

    override suspend fun setDataRoamingEnabled(enabled: Boolean) = success("roaming:$enabled")

    override suspend fun setDefaultDataSubscription(subscriptionId: Int) = success("defaultData:$subscriptionId")

    private fun success(call: String): ActionResult {
        calls += call
        return ActionResult.Success
    }
}
