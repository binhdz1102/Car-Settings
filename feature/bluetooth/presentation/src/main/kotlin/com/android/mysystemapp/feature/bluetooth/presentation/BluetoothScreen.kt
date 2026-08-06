package com.android.car.settings.feature.bluetooth.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.feature.bluetooth.domain.BluetoothBondState
import com.android.car.settings.feature.bluetooth.domain.BluetoothConnectionState
import com.android.car.settings.feature.bluetooth.domain.BluetoothDeviceModel
import com.android.car.settings.feature.bluetooth.domain.BluetoothRadioState

@Composable
fun BluetoothRoute(
    viewModel: BluetoothViewModel,
    onBack: () -> Unit,
    onDeviceDetails: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var renameAdapter by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    BluetoothScreen(
        state = uiState,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onSetEnabled = viewModel::setEnabled,
        onDiscovery = {
            if (uiState.bluetooth.adapter.isDiscovering) {
                viewModel.stopDiscovery()
            } else {
                viewModel.startDiscovery()
            }
        },
        onDiscoverable = viewModel::setDiscoverable,
        onRenameAdapter = { renameAdapter = true },
        onPairedDevice = onDeviceDetails,
        onAvailableDevice = { device ->
            when (device.bondState) {
                BluetoothBondState.BONDING -> viewModel.cancelPairing(device.address)
                BluetoothBondState.BONDED -> onDeviceDetails(device.address)
                BluetoothBondState.NONE -> viewModel.pair(device.address)
            }
        },
    )

    if (renameAdapter) {
        RenameDialog(
            title = "Rename this device",
            initialValue = uiState.bluetooth.adapter.name,
            onDismiss = { renameAdapter = false },
            onConfirm = {
                viewModel.renameAdapter(it)
                renameAdapter = false
            },
        )
    }
}

@Composable
private fun BluetoothScreen(
    state: BluetoothUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onDiscovery: () -> Unit,
    onDiscoverable: (Boolean) -> Unit,
    onRenameAdapter: () -> Unit,
    onPairedDevice: (String) -> Unit,
    onAvailableDevice: (BluetoothDeviceModel) -> Unit,
) {
    val enabled =
        state.bluetooth.adapter.radioState == BluetoothRadioState.ON ||
            state.bluetooth.adapter.radioState == BluetoothRadioState.TURNING_ON
    val transitioning =
        state.bluetooth.adapter.radioState == BluetoothRadioState.TURNING_ON ||
            state.bluetooth.adapter.radioState == BluetoothRadioState.TURNING_OFF
    SettingsScaffold(
        title = "Bluetooth",
        onBack = onBack,
        actions = {
            IconButton(
                enabled = enabled,
                onClick = onDiscovery,
            ) {
                if (state.bluetooth.adapter.isDiscovering) {
                    CircularProgressIndicator()
                } else {
                    Icon(Icons.Default.BluetoothSearching, contentDescription = "Scan")
                }
            }
        },
    ) {
        SnackbarHost(hostState = snackbarHost)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSwitchRow(
                    title = "Use Bluetooth",
                    summary =
                        state.bluetooth.adapter.radioState.name
                            .lowercase()
                            .replaceFirstChar(Char::titlecase),
                    checked = enabled,
                    enabled = !state.isWorking,
                    busy = transitioning,
                    onCheckedChange = onSetEnabled,
                )
            }
            if (enabled) {
                item {
                    SettingsActionRow(
                        title =
                            state.bluetooth.adapter.name.ifBlank {
                                "This vehicle"
                            },
                        summary = state.bluetooth.adapter.address,
                        leading = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                        trailing = { Icon(Icons.Default.Edit, contentDescription = "Rename") },
                        onClick = onRenameAdapter,
                    )
                    SettingsSwitchRow(
                        title = "Discoverable",
                        summary = "Allow nearby devices to find this vehicle for 2 minutes",
                        checked = state.bluetooth.adapter.isDiscoverable,
                        onCheckedChange = onDiscoverable,
                    )
                }
                if (state.bluetooth.pairedDevices.isNotEmpty()) {
                    item { SettingsSection("Paired devices") {} }
                    items(
                        items = state.bluetooth.pairedDevices,
                        key = BluetoothDeviceModel::address,
                    ) { device ->
                        BluetoothDeviceRow(
                            device = device,
                            onClick = { onPairedDevice(device.address) },
                        )
                    }
                }
                item { SettingsSection("Available devices") {} }
                items(
                    items = state.bluetooth.availableDevices,
                    key = BluetoothDeviceModel::address,
                ) { device ->
                    BluetoothDeviceRow(
                        device = device,
                        onClick = { onAvailableDevice(device) },
                    )
                }
                item {
                    SettingsActionRow(
                        title =
                            if (state.bluetooth.adapter.isDiscovering) {
                                "Stop scanning"
                            } else {
                                "Pair new device"
                            },
                        summary = "Search for nearby Bluetooth devices",
                        leading = {
                            Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                        },
                        onClick = onDiscovery,
                    )
                }
            }
        }
    }
}

@Composable
private fun BluetoothDeviceRow(
    device: BluetoothDeviceModel,
    onClick: () -> Unit,
) {
    val summary =
        buildList {
            when (device.bondState) {
                BluetoothBondState.BONDING -> add("Pairing…")
                BluetoothBondState.BONDED -> add("Paired")
                BluetoothBondState.NONE -> Unit
            }
            when (device.connectionState) {
                BluetoothConnectionState.CONNECTED -> add("Connected")
                BluetoothConnectionState.CONNECTING -> add("Connecting…")
                BluetoothConnectionState.DISCONNECTING -> add("Disconnecting…")
                BluetoothConnectionState.DISCONNECTED -> Unit
            }
            device.batteryLevel?.let { add("$it% battery") }
            device.rssi?.let { add("$it dBm") }
            device.bluetoothClass?.let(::add)
        }.joinToString(" · ")
    SettingsActionRow(
        title = device.alias,
        summary = summary.ifBlank { device.address },
        leading = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
internal fun RenameDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(248) },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                enabled = value.isNotBlank(),
                onClick = { onConfirm(value.trim()) },
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
