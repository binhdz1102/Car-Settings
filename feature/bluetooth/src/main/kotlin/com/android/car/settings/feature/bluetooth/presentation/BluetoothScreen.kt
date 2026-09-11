package com.android.car.settings.feature.bluetooth.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.AutomotiveAlertDialog
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsAppBarAction
import com.android.car.settings.core.ui.SettingsFormTextField
import com.android.car.settings.core.ui.SettingsLeadingIcon
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.feature.bluetooth.domain.BluetoothBondState
import com.android.car.settings.feature.bluetooth.domain.BluetoothConnectionState
import com.android.car.settings.feature.bluetooth.domain.BluetoothDeviceModel
import com.android.car.settings.feature.bluetooth.domain.BluetoothRadioState
import com.android.car.settings.core.ui.AutomotiveButton as Button
import com.android.car.settings.core.ui.AutomotiveLazyColumn as LazyColumn
import com.android.car.settings.core.ui.AutomotiveOutlinedButton as OutlinedButton

@Composable
fun BluetoothRoute(
    viewModel: BluetoothViewModel,
    onBack: () -> Unit,
    onDeviceDetails: (String) -> Unit,
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
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
        listState = listState,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onSetEnabled = viewModel::setEnabled,
        onSetUwbEnabled = viewModel::setUwbEnabled,
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
    listState: LazyListState = rememberLazyListState(),
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetUwbEnabled: (Boolean) -> Unit,
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
        destinationKey = "bluetooth",
        isRoot = true,
        onBack = onBack,
        actions = {
            SettingsAppBarAction(
                id = "bluetooth-scan",
                contentDescription = "Scan for Bluetooth devices",
                enabled = enabled,
                onClick = onDiscovery,
            ) {
                if (state.bluetooth.adapter.isDiscovering) {
                    CircularProgressIndicator()
                } else {
                    SettingsLeadingIcon(Icons.Default.BluetoothSearching)
                }
            }
        },
    ) {
        SnackbarHost(hostState = snackbarHost)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
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
                    retainFocusWhenDisabled = state.isWorking || transitioning,
                    onCheckedChange = onSetEnabled,
                )
            }
            if (state.bluetooth.uwb.isSupported) {
                item {
                    SettingsSwitchRow(
                        title = "Ultra-Wideband (UWB)",
                        summary = state.bluetooth.uwb.error ?: "Helps your car identify the position of UWB devices",
                        checked = state.bluetooth.uwb.isEnabled,
                        enabled = !state.isWorking,
                        busy = state.bluetooth.uwb.isLoading || state.isWorking,
                        onCheckedChange = onSetUwbEnabled,
                    )
                }
            }
            if (enabled) {
                item {
                    SettingsActionRow(
                        title =
                            state.bluetooth.adapter.name.ifBlank {
                                "This vehicle"
                            },
                        summary = state.bluetooth.adapter.address,
                        focusId = "bluetooth-adapter",
                        leading = { SettingsLeadingIcon(Icons.Default.Bluetooth) },
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
                        navigates = true,
                        title =
                            if (state.bluetooth.adapter.isDiscovering) {
                                "Stop scanning"
                            } else {
                                "Pair new device"
                            },
                        summary = "Search for nearby Bluetooth devices",
                        leading = { SettingsLeadingIcon(Icons.Default.BluetoothSearching) },
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
        focusId = "bluetooth-device-${device.address}",
        summary = summary.ifBlank { device.address },
        leading = { SettingsLeadingIcon(Icons.Default.Bluetooth) },
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
    AutomotiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            SettingsFormTextField(
                focusId = "bluetooth-rename",
                value = value,
                onValueChange = { value = it.take(248) },
                label = "Name",
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                enabled = value.isNotBlank(),
                onClick = { onConfirm(value.trim()) },
            ) {
                Text("Save", style = MaterialTheme.typography.titleMedium)
            }
        },
        confirmAction = { onConfirm(value.trim()) },
        confirmEnabled = value.isNotBlank(),
        dismissAction = onDismiss,
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium)
            }
        },
    )
}
