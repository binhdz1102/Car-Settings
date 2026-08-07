package com.android.car.settings.feature.wifi.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.wifi.domain.HotspotConfiguration
import com.android.car.settings.feature.wifi.domain.MeteredOverride
import com.android.car.settings.feature.wifi.domain.WifiCredentials
import com.android.car.settings.feature.wifi.domain.WifiNetwork
import com.android.car.settings.feature.wifi.domain.WifiSecurity
import com.android.car.settings.feature.wifi.domain.WifiState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WifiRepositoryImplTest {
    @Test
    fun `repository delegates every platform operation`() =
        runTest {
            val platform = FakeWifiPlatform()
            val repository = WifiRepositoryImpl(platform)
            val network =
                WifiNetwork(
                    key = "key",
                    networkId = 2,
                    ssid = "ssid",
                    security = WifiSecurity.OPEN,
                    signalLevel = 3,
                    rssi = -50,
                    isSaved = true,
                    isConnected = false,
                )

            repository.refresh()
            repository.setWifiEnabled(true)
            repository.startScan()
            repository.connect(network, WifiCredentials())
            repository.addNetwork("ssid", WifiSecurity.OPEN, WifiCredentials(), false)
            repository.disconnect()
            repository.forget(2)
            repository.setAutoJoin(2, true)
            repository.setMeteredOverride(2, MeteredOverride.NOT_METERED)
            repository.setHotspotEnabled(true)
            repository.updateHotspotConfiguration(HotspotConfiguration(ssid = "AP"))
            repository.setScanAlwaysAvailable(true)
            repository.setWakeupEnabled(true)
            repository.setOpenNetworkNotificationEnabled(true)

            assertThat(platform.calls).hasSize(14)
            assertThat(repository.state).isSameInstanceAs(platform.state)
        }

    @Test
    fun `security capability parsing covers supported modes`() {
        assertThat(securityFromCapabilities("[ESS]")).isEqualTo(WifiSecurity.OPEN)
        assertThat(securityFromCapabilities("[OWE][ESS]")).isEqualTo(WifiSecurity.OWE)
        assertThat(securityFromCapabilities("[WEP][ESS]")).isEqualTo(WifiSecurity.WEP)
        assertThat(securityFromCapabilities("[WPA2-PSK-CCMP][ESS]"))
            .isEqualTo(WifiSecurity.WPA2_PERSONAL)
        assertThat(securityFromCapabilities("[RSN-PSK+SAE-CCMP][ESS]"))
            .isEqualTo(WifiSecurity.WPA2_WPA3_PERSONAL)
        assertThat(securityFromCapabilities("[RSN-SAE-CCMP][ESS]"))
            .isEqualTo(WifiSecurity.WPA3_PERSONAL)
        assertThat(securityFromCapabilities("[RSN-EAP-CCMP][ESS]"))
            .isEqualTo(WifiSecurity.EAP)
    }
}

private class FakeWifiPlatform : WifiPlatform {
    val calls = mutableListOf<String>()
    override val state = MutableStateFlow(WifiState())

    override suspend fun refresh() = result("refresh")

    override suspend fun setWifiEnabled(enabled: Boolean) = result("wifi")

    override suspend fun startScan() = result("scan")

    override suspend fun connect(
        network: WifiNetwork,
        credentials: WifiCredentials,
    ) = result("connect")

    override suspend fun addNetwork(
        ssid: String,
        security: WifiSecurity,
        credentials: WifiCredentials,
        hidden: Boolean,
    ) = result("add")

    override suspend fun disconnect() = result("disconnect")

    override suspend fun forget(networkId: Int) = result("forget")

    override suspend fun setAutoJoin(
        networkId: Int,
        enabled: Boolean,
    ) = result("autojoin")

    override suspend fun setMeteredOverride(
        networkId: Int,
        override: MeteredOverride,
    ) = result("metered")

    override suspend fun setHotspotEnabled(enabled: Boolean) = result("hotspot")

    override suspend fun updateHotspotConfiguration(configuration: HotspotConfiguration) = result("hotspotConfig")

    override suspend fun setScanAlwaysAvailable(enabled: Boolean) = result("scanAlways")

    override suspend fun setWakeupEnabled(enabled: Boolean) = result("wakeup")

    override suspend fun setOpenNetworkNotificationEnabled(enabled: Boolean) = result("notification")

    private fun result(call: String): ActionResult {
        calls += call
        return ActionResult.Success
    }
}
