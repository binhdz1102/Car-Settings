package com.android.car.settings.feature.wifi.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.KeyValueRow
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsActionToggleRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.feature.wifi.domain.WifiRadioState
import com.android.car.settings.core.ui.BMaterialLazyColumn as LazyColumn

@Composable
fun NetworkInternetRoute(
    viewModel: WifiViewModel,
    onBack: () -> Unit,
    onWifi: () -> Unit,
    onHotspot: () -> Unit,
    onMobileNetwork: () -> Unit,
    onPreferences: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wifiEnabled = state.wifi.radioState == WifiRadioState.ENABLED || state.wifi.radioState == WifiRadioState.ENABLING
    val mobile = state.wifi.mobileNetwork
    SettingsScaffold(
        title = "Network & internet",
        destinationKey = "network-internet",
        isRoot = true,
        onBack = onBack,
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Connections") {} }
            item {
                SettingsActionToggleRow(
                    title = "Hotspot",
                    summary = if (state.wifi.hotspot.isEnabled) "${state.wifi.hotspot.clients} connected devices" else "Off",
                    checked = state.wifi.hotspot.isEnabled,
                    enabled = !state.isWorking,
                    busy = state.wifi.hotspot.isTransitioning,
                    retainFocusWhenDisabled = state.isWorking || state.wifi.hotspot.isTransitioning,
                    leading = { Icon(Icons.Default.WifiTethering, contentDescription = null) },
                    onCheckedChange = viewModel::setHotspotEnabled,
                    onRowClick = onHotspot,
                    focusId = "network-hotspot",
                )
            }
            item {
                SettingsActionToggleRow(
                    title = "Mobile network",
                    summary =
                        mobile.subscriptions
                            .firstOrNull { it.isDefaultData }
                            ?.carrierName
                            ?.ifBlank { "Mobile data" } ?: if (mobile.isSupported) "No SIM selected" else "Not supported",
                    checked = mobile.mobileDataEnabled,
                    enabled = mobile.isSupported && mobile.subscriptions.isNotEmpty() && !state.isWorking,
                    retainFocusWhenDisabled = state.isWorking,
                    leading = { Icon(Icons.Default.NetworkCell, contentDescription = null) },
                    onCheckedChange = viewModel::setMobileDataEnabled,
                    onRowClick = onMobileNetwork,
                    focusId = "network-mobile",
                )
            }
            item {
                SettingsActionToggleRow(
                    title = "Wi‑Fi",
                    summary = state.wifi.connectedNetwork?.ssid ?: if (wifiEnabled) "Not connected" else "Off",
                    checked = wifiEnabled,
                    enabled = !state.isWorking,
                    busy = state.wifi.radioState == WifiRadioState.ENABLING || state.wifi.radioState == WifiRadioState.DISABLING,
                    retainFocusWhenDisabled = state.isWorking ||
                        state.wifi.radioState == WifiRadioState.ENABLING ||
                        state.wifi.radioState == WifiRadioState.DISABLING,
                    leading = { Icon(Icons.Default.Wifi, contentDescription = null) },
                    onCheckedChange = viewModel::setWifiEnabled,
                    onRowClick = onWifi,
                    focusId = "network-wifi",
                )
            }
            state.wifi.connectedNetwork?.let { connected ->
                item {
                    SettingsActionRow(
                        title = connected.ssid,
                        summary = "Connected network",
                        onClick = onWifi,
                        focusId = "network-connected-${connected.key}",
                    )
                }
            }
            item { SettingsSection("Network options") {} }
            item {
                SettingsActionRow(
                    title = "Join other network",
                    summary = "Connect to a Wi‑Fi network manually",
                    onClick = onWifi,
                    focusId = "network-join-other",
                )
            }
            item {
                SettingsActionRow(
                    title = "Wi‑Fi preferences",
                    summary = "Scanning, wakeup and public-network notifications",
                    onClick = onPreferences,
                    focusId = "network-wifi-preferences",
                )
            }
            if (mobile.isSupported) {
                item {
                    SettingsSection("Data usage") {
                        KeyValueRow(key = "Last 30 days", value = mobile.dataUsageBytes.toDataUsageString())
                    }
                }
            }
        }
    }
}

internal fun Long.toDataUsageString(): String =
    when {
        this < 1_000_000L -> "$this B"
        this < 1_000_000_000L -> "%.1f MB".format(this / 1_000_000.0)
        else -> "%.1f GB".format(this / 1_000_000_000.0)
    }
