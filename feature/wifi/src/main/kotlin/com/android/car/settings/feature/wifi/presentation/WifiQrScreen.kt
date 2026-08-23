package com.android.car.settings.feature.wifi.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.feature.wifi.domain.HotspotSecurity
import com.android.car.settings.feature.wifi.domain.WifiSecurity
import com.android.car.settings.feature.wifi.domain.wifiQrPayload
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

private const val QR_SIZE = 640

@Composable
fun WifiQrRoute(
    viewModel: WifiViewModel,
    kind: String,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hotspot = kind == "hotspot"
    val details = state.wifi.connectionDetails
    val payload =
        if (hotspot) {
            state.wifi.hotspot.configuration.let { config ->
                wifiQrPayload(
                    ssid = config.ssid,
                    security = config.security.toWifiSecurity(),
                    password = config.password,
                )
            }
        } else {
            details?.qrPayload
        }
    val title = if (hotspot) "Share hotspot" else "Share Wi‑Fi network"
    SettingsScaffold(title = title, onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Top),
        ) {
            if (payload == null) {
                SettingsSection("QR sharing unavailable") {
                    Text(
                        if (hotspot) {
                            "Turn on the hotspot and save a network name before sharing."
                        } else {
                            "The connected network does not expose a shareable password."
                        },
                    )
                }
            } else {
                val bitmap = remember(payload) { encodeQr(payload) }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR code for ${if (hotspot) "hotspot" else "Wi‑Fi network"}",
                    modifier = Modifier.size(420.dp),
                )
                Text(
                    if (hotspot) {
                        "Scan this QR code to join \"${state.wifi.hotspot.configuration.ssid}\""
                    } else {
                        "Scan this QR code to connect to Wi‑Fi"
                    },
                )
            }
        }
    }
}

private fun encodeQr(payload: String): Bitmap {
    val matrix: BitMatrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE)
    val bitmap = Bitmap.createBitmap(QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888)
    for (x in 0 until QR_SIZE) {
        for (y in 0 until QR_SIZE) {
            bitmap.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
    }
    return bitmap
}

private fun HotspotSecurity.toWifiSecurity() =
    when (this) {
        HotspotSecurity.OPEN -> WifiSecurity.OPEN
        HotspotSecurity.WPA2_PERSONAL -> WifiSecurity.WPA2_PERSONAL
        HotspotSecurity.WPA3_PERSONAL -> WifiSecurity.WPA3_PERSONAL
        HotspotSecurity.WPA2_WPA3_PERSONAL -> WifiSecurity.WPA2_WPA3_PERSONAL
    }
