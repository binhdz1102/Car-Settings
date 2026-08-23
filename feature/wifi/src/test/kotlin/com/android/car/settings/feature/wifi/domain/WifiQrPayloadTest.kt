package com.android.car.settings.feature.wifi.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WifiQrPayloadTest {
    @Test
    fun `escapes fields and emits WPA payload`() {
        assertThat(
            wifiQrPayload(
                ssid = "Car;WiFi",
                security = WifiSecurity.WPA2_PERSONAL,
                password = "p:a\\ss",
            ),
        ).isEqualTo("WIFI:T:WPA;S:Car\\;WiFi;P:p\\:a\\\\ss;;")
    }

    @Test
    fun `open network omits password`() {
        assertThat(wifiQrPayload("Open", WifiSecurity.OPEN, null)).isEqualTo("WIFI:T:nopass;S:Open;;")
    }

    @Test
    fun `secured network without password is not shareable`() {
        assertThat(wifiQrPayload("Secured", WifiSecurity.WPA3_PERSONAL, null)).isNull()
    }
}
