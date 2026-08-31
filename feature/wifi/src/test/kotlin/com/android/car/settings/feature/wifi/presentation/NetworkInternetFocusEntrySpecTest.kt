package com.android.car.settings.feature.wifi.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkInternetFocusEntrySpecTest {
    @Test
    fun enabledConnectionControls_followTheRenderedHotspotMobileWifiOrder() {
        val spec =
            networkInternetRootFocusSpec(
                hotspotEnabled = true,
                mobileNetworkEnabled = true,
                wifiEnabled = true,
                connectedNetworkFocusId = null,
            )

        assertEquals("network-hotspot", spec.firstContentFocusId)
        assertEquals(
            mapOf(
                "network-hotspot" to 1,
                "network-mobile" to 2,
                "network-wifi" to 3,
                "network-join-other" to 5,
                "network-wifi-preferences" to 6,
            ),
            spec.itemIndexByFocusId,
        )
    }

    @Test
    fun disabledConnectionControls_fallBackToTheRenderedConnectedNetworkAction() {
        val spec =
            networkInternetRootFocusSpec(
                hotspotEnabled = false,
                mobileNetworkEnabled = false,
                wifiEnabled = false,
                connectedNetworkFocusId = "network-connected-home",
            )

        assertEquals("network-connected-home", spec.firstContentFocusId)
        assertEquals(
            mapOf(
                "network-connected-home" to 4,
                "network-join-other" to 6,
                "network-wifi-preferences" to 7,
            ),
            spec.itemIndexByFocusId,
        )
    }
}
