package com.android.car.settings.feature.bluetooth.presentation

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
import com.android.car.settings.feature.bluetooth.domain.BluetoothConnectionState
import com.android.car.settings.core.ui.AutomotiveButton as Button
import com.android.car.settings.core.ui.AutomotiveTextButton as TextButton
import com.android.car.settings.core.ui.BMaterialLazyColumn as LazyColumn

@Composable
fun BluetoothDeviceDetailsRoute(
    viewModel: BluetoothViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val device = uiState.bluetooth.selectedDevice
    var confirmForget by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    SettingsScaffold(
        title = device?.alias ?: "Bluetooth device",
        onBack = onBack,
    ) {
        if (device == null) {
            Text("Bluetooth device is no longer available")
            return@SettingsScaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSection("Actions") {
                    val connected =
                        device.connectionState == BluetoothConnectionState.CONNECTED
                    SettingsActionRow(
                        title = if (connected) "Disconnect" else "Connect",
                        enabled = !uiState.isWorking,
                        onClick = {
                            if (connected) {
                                viewModel.disconnect(device.address)
                            } else {
                                viewModel.connect(device.address)
                            }
                        },
                    )
                    SettingsActionRow(
                        title = "Rename",
                        enabled = !uiState.isWorking,
                        onClick = { rename = true },
                    )
                    SettingsActionRow(
                        title = "Forget",
                        enabled = !uiState.isWorking,
                        onClick = { confirmForget = true },
                    )
                }
            }
            if (device.profiles.isNotEmpty()) {
                item {
                    SettingsSection("Profiles") {
                        device.profiles.forEach { profile ->
                            SettingsSwitchRow(
                                title = profile.profile.displayName,
                                focusId = "bluetooth-profile-${profile.profile.name}",
                                summary =
                                    when (profile.connectionState) {
                                        BluetoothConnectionState.CONNECTED -> "Connected"
                                        BluetoothConnectionState.CONNECTING -> "Connecting"
                                        BluetoothConnectionState.DISCONNECTING -> "Disconnecting"
                                        BluetoothConnectionState.DISCONNECTED -> null
                                    },
                                checked = profile.isEnabled,
                                onCheckedChange = {
                                    viewModel.setProfileEnabled(
                                        device.address,
                                        profile.profile,
                                        it,
                                    )
                                },
                            )
                        }
                    }
                }
            }
            item {
                SettingsSection("Device information") {
                    KeyValueRow("Address", device.address)
                    KeyValueRow("Bond state", device.bondState.name)
                    KeyValueRow("Connection", device.connectionState.name)
                    KeyValueRow("Type", device.deviceType.name)
                    device.bluetoothClass?.let { KeyValueRow("Class", it) }
                    device.batteryLevel?.let { KeyValueRow("Battery", "$it%") }
                    device.rssi?.let { KeyValueRow("Signal", "$it dBm") }
                    if (device.uuids.isNotEmpty()) {
                        KeyValueRow("Services", device.uuids.joinToString("\n"))
                    }
                }
            }
        }
    }
    if (device != null && rename) {
        RenameDialog(
            title = "Rename ${device.name}",
            initialValue = device.alias,
            onDismiss = { rename = false },
            onConfirm = {
                viewModel.renameDevice(device.address, it)
                rename = false
            },
        )
    }
    if (device != null && confirmForget) {
        AutomotiveAlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget ${device.alias}?") },
            text = { Text("Pairing information and profile permissions will be removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unpair(device.address)
                        confirmForget = false
                        onBack()
                    },
                ) {
                    Text("Forget")
                }
            },
            confirmAction = {
                viewModel.unpair(device.address)
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
