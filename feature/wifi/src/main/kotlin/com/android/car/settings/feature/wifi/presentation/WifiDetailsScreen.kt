package com.android.car.settings.feature.wifi.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.AutomotiveAlertDialog
import com.android.car.settings.core.ui.KeyValueRow
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.feature.wifi.domain.MeteredOverride
import com.android.car.settings.core.ui.AutomotiveButton as Button
import com.android.car.settings.core.ui.AutomotiveTextButton as TextButton
import com.android.car.settings.core.ui.BMaterialLazyColumn as LazyColumn

@Composable
fun WifiDetailsRoute(
    viewModel: WifiViewModel,
    onBack: () -> Unit,
    onShareQr: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val details =
        uiState.wifi.connectionDetails ?: run {
            SettingsScaffold(
                title = "Network details",
                onBack = onBack,
            ) {
                Text("No connected Wi‑Fi network")
            }
            return
        }
    var confirmForget by remember { mutableStateOf(false) }
    SettingsScaffold(
        title = details?.network?.ssid ?: "Network details",
        onBack = onBack,
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSection("Actions") {
                    SettingsActionRow(
                        title = "Disconnect",
                        enabled = !uiState.isWorking,
                        onClick = viewModel::disconnect,
                    )
                    if (details.network.networkId != null) {
                        SettingsActionRow(
                            title = "Forget",
                            enabled = !uiState.isWorking,
                            onClick = { confirmForget = true },
                        )
                    }
                    if (details.qrPayload != null) {
                        SettingsActionRow(
                            title = "Share network",
                            summary = "Show a QR code for another device to connect",
                            onClick = onShareQr,
                        )
                    }
                    details.network.networkId?.let { networkId ->
                        SettingsSwitchRow(
                            title = "Auto-connect",
                            checked = details.isAutoJoinEnabled,
                            onCheckedChange = { viewModel.setAutoJoin(networkId, it) },
                        )
                        SettingsActionRow(
                            title = "Metered network",
                            summary = details.meteredOverride.readableName(),
                            onClick = {
                                viewModel.setMeteredOverride(
                                    networkId,
                                    details.meteredOverride.next(),
                                )
                            },
                        )
                    }
                }
            }
            item {
                SettingsSection("Network information") {
                    KeyValueRow("Security", details.securityLabel)
                    KeyValueRow("Signal", "${details.network.rssi} dBm")
                    details.frequencyMhz?.let { KeyValueRow("Frequency", "$it MHz") }
                    details.linkSpeedMbps?.let { KeyValueRow("Link speed", "$it Mbps") }
                    details.macAddress?.let { KeyValueRow("Device MAC", it) }
                    details.ipv4Address?.let { KeyValueRow("IPv4 address", it) }
                    details.subnetMask?.let { KeyValueRow("Subnet mask", it) }
                    details.gateway?.let { KeyValueRow("Gateway", it) }
                    if (details.dnsServers.isNotEmpty()) {
                        KeyValueRow("DNS", details.dnsServers.joinToString("\n"))
                    }
                    if (details.ipv6Addresses.isNotEmpty()) {
                        KeyValueRow("IPv6", details.ipv6Addresses.joinToString("\n"))
                    }
                }
            }
        }
    }
    val networkId = details.network.networkId
    if (confirmForget && networkId != null) {
        AutomotiveAlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget ${details.network.ssid}?") },
            text = { Text("The saved password and network settings will be removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.forget(networkId)
                        confirmForget = false
                        onBack()
                    },
                ) {
                    Text("Forget")
                }
            },
            confirmAction = {
                viewModel.forget(networkId)
                confirmForget = false
                onBack()
            },
            dismissAction = { confirmForget = false },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun MeteredOverride.readableName(): String =
    when (this) {
        MeteredOverride.AUTOMATIC -> "Detect automatically"
        MeteredOverride.METERED -> "Treat as metered"
        MeteredOverride.NOT_METERED -> "Treat as unmetered"
    }

private fun MeteredOverride.next(): MeteredOverride =
    when (this) {
        MeteredOverride.AUTOMATIC -> MeteredOverride.METERED
        MeteredOverride.METERED -> MeteredOverride.NOT_METERED
        MeteredOverride.NOT_METERED -> MeteredOverride.AUTOMATIC
    }
