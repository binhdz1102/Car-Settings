package com.android.car.settings.feature.wifi.domain

/**
 * Creates the standard Wi-Fi Easy Connect payload used by Android's QR sharing UI.
 *
 * The value is deliberately produced without logging or exposing the password anywhere except
 * the QR screen.  Each field is escaped according to the ZXing/AOSP Wi-Fi barcode format.
 */
internal fun wifiQrPayload(
    ssid: String,
    security: WifiSecurity,
    password: String? = null,
): String? {
    val type =
        when (security) {
            WifiSecurity.OPEN, WifiSecurity.OWE -> "nopass"
            WifiSecurity.WEP -> "WEP"
            WifiSecurity.WPA2_PERSONAL,
            WifiSecurity.WPA3_PERSONAL,
            WifiSecurity.WPA2_WPA3_PERSONAL,
            WifiSecurity.EAP,
            -> "WPA"
            WifiSecurity.UNKNOWN -> null
        }

    if (ssid.isNotBlank() && type != null) {
        val canEncode = type == "nopass" || !password.isNullOrBlank()
        if (canEncode) {
            return buildString {
                append("WIFI:T:")
                append(type)
                append(";S:")
                append(escapeWifiQrField(ssid))
                if (type != "nopass") {
                    append(";P:")
                    append(escapeWifiQrField(password.orEmpty()))
                }
                append(";;")
            }
        }
    }
    return null
}

private fun escapeWifiQrField(value: String): String =
    buildString(value.length) {
        value.forEach { character ->
            if (character in WIFI_QR_RESERVED_CHARACTERS) {
                append('\\')
            }
            append(character)
        }
    }

private val WIFI_QR_RESERVED_CHARACTERS = setOf('\\', ';', ',', ':')
