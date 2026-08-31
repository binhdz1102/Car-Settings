package com.android.car.settings.feature.wifi.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.AutomotiveAlertDialog
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsAppBarAction
import com.android.car.settings.core.ui.SettingsFormTextField
import com.android.car.settings.core.ui.SettingsLeadingIcon
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.feature.wifi.domain.WifiCredentials
import com.android.car.settings.feature.wifi.domain.WifiNetwork
import com.android.car.settings.feature.wifi.domain.WifiRadioState
import com.android.car.settings.feature.wifi.domain.WifiSecurity
import com.android.car.settings.core.ui.AutomotiveButton as Button
import com.android.car.settings.core.ui.AutomotiveTextButton as TextButton
import com.android.car.settings.core.ui.AutomotiveLazyColumn as LazyColumn

@Composable
fun WifiRoute(
    viewModel: WifiViewModel,
    onBack: () -> Unit,
    onDetails: () -> Unit,
    onHotspot: () -> Unit,
    onPreferences: () -> Unit,
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var credentialTarget by remember { mutableStateOf<WifiNetwork?>(null) }
    var showAddNetwork by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    WifiScreen(
        state = uiState,
        listState = listState,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onSetEnabled = viewModel::setWifiEnabled,
        onScan = viewModel::scan,
        onNetwork = { network ->
            when {
                network.isConnected -> onDetails()
                network.isSaved ||
                    network.security in
                    setOf(WifiSecurity.OPEN, WifiSecurity.OWE) -> viewModel.connect(network)

                else -> credentialTarget = network
            }
        },
        onAddNetwork = { showAddNetwork = true },
        onHotspot = onHotspot,
        onPreferences = onPreferences,
    )

    credentialTarget?.let { network ->
        WifiCredentialsDialog(
            title = "Connect to ${network.ssid}",
            security = network.security,
            onDismiss = { credentialTarget = null },
            onConfirm = { credentials ->
                viewModel.connect(network, credentials)
                credentialTarget = null
            },
        )
    }

    if (showAddNetwork) {
        AddWifiNetworkDialog(
            onDismiss = { showAddNetwork = false },
            onConfirm = { ssid, security, credentials, hidden ->
                viewModel.addNetwork(ssid, security, credentials, hidden)
                showAddNetwork = false
            },
        )
    }
}

@Composable
private fun WifiScreen(
    state: WifiUiState,
    listState: LazyListState = rememberLazyListState(),
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onScan: () -> Unit,
    onNetwork: (WifiNetwork) -> Unit,
    onAddNetwork: () -> Unit,
    onHotspot: () -> Unit,
    onPreferences: () -> Unit,
) {
    val wifiEnabled =
        state.wifi.radioState == WifiRadioState.ENABLED ||
            state.wifi.radioState == WifiRadioState.ENABLING
    val transitioning =
        state.wifi.radioState == WifiRadioState.ENABLING ||
            state.wifi.radioState == WifiRadioState.DISABLING

    SettingsScaffold(
        title = "Wi‑Fi",
        destinationKey = "wifi",
        isRoot = true,
        onBack = onBack,
        actions = {
            SettingsAppBarAction(
                id = "wifi-scan",
                contentDescription = "Scan for Wi-Fi networks",
                enabled = wifiEnabled && !state.wifi.isScanning,
                onClick = onScan,
            ) {
                if (state.wifi.isScanning) {
                    CircularProgressIndicator()
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan")
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
                    title = "Use Wi‑Fi",
                    summary =
                        state.wifi.radioState.name
                            .lowercase()
                            .replaceFirstChar(Char::titlecase),
                    checked = wifiEnabled,
                    enabled = !state.isWorking,
                    busy = transitioning,
                    retainFocusWhenDisabled = state.isWorking || transitioning,
                    onCheckedChange = onSetEnabled,
                )
            }

            state.wifi.connectedNetwork?.let { connected ->
                item {
                    SettingsSection("Connected") {
                        WifiNetworkRow(connected, onNetwork)
                    }
                }
            }

            if (wifiEnabled) {
                item { SettingsSection("Available networks") {} }
                items(
                    items = state.wifi.availableNetworks,
                    key = WifiNetwork::key,
                ) { network ->
                    WifiNetworkRow(network, onNetwork)
                }
                item {
                    SettingsActionRow(
                        title = "Add network",
                        leading = { SettingsLeadingIcon(Icons.Default.Add) },
                        trailing = { SettingsLeadingIcon(Icons.Default.Edit, contentDescription = null) },
                        onClick = onAddNetwork,
                    )
                    SettingsActionRow(
                        navigates = true,
                        title = "Hotspot",
                        summary =
                            if (state.wifi.hotspot.isEnabled) {
                                "On · ${state.wifi.hotspot.clients} connected"
                            } else {
                                "Off"
                            },
                        leading = { SettingsLeadingIcon(Icons.Default.WifiTethering) },
                        onClick = onHotspot,
                    )
                    SettingsActionRow(
                        navigates = true,
                        title = "Wi‑Fi preferences",
                        leading = { SettingsLeadingIcon(Icons.Default.Settings) },
                        onClick = onPreferences,
                    )
                }
            }
        }
    }
}

@Composable
private fun WifiNetworkRow(
    network: WifiNetwork,
    onClick: (WifiNetwork) -> Unit,
) {
    val summary =
        buildList {
            if (network.isConnected) add("Connected")
            if (network.isSaved && !network.isConnected) add("Saved")
            add(network.security.readableName())
            if (network.rssi != Int.MIN_VALUE) add("${network.rssi} dBm")
        }.joinToString(" · ")
    SettingsActionRow(
        navigates = true,
        title = network.ssid,
        focusId = "wifi-network-${network.networkId ?: network.ssid}",
        summary = summary,
        leading = { SettingsLeadingIcon(Icons.Default.NetworkWifi) },
        trailing =
            if (network.security !in setOf(WifiSecurity.OPEN, WifiSecurity.OWE)) {
                { SettingsLeadingIcon(Icons.Default.Lock, contentDescription = "Secured") }
            } else {
                null
            },
        onClick = { onClick(network) },
    )
}

@Composable
private fun WifiCredentialsDialog(
    title: String,
    security: WifiSecurity,
    onDismiss: () -> Unit,
    onConfirm: (WifiCredentials) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var identity by remember { mutableStateOf("") }
    var anonymousIdentity by remember { mutableStateOf("") }
    val enterprise = security == WifiSecurity.EAP
    val passwordValid =
        when (security) {
            WifiSecurity.WEP -> password.length in setOf(5, 10, 13, 26, 16, 32, 29, 58)
            WifiSecurity.OPEN,
            WifiSecurity.OWE,
            -> true

            else -> password.length >= 8
        }
    AutomotiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Security: ${security.readableName()}")
                if (enterprise) {
                    SettingsFormTextField(
                        focusId = "wifi-credentials-identity",
                        label = "Identity",
                        value = identity,
                        onValueChange = { identity = it },
                        singleLine = true,
                    )
                    SettingsFormTextField(
                        focusId = "wifi-credentials-anonymous-identity",
                        label = "Anonymous identity (optional)",
                        value = anonymousIdentity,
                        onValueChange = { anonymousIdentity = it },
                        singleLine = true,
                    )
                }
                if (security !in setOf(WifiSecurity.OPEN, WifiSecurity.OWE)) {
                    SettingsFormTextField(
                        focusId = "wifi-credentials-password",
                        label = "Password",
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = passwordValid && (!enterprise || identity.isNotBlank()),
                onClick = {
                    onConfirm(
                        WifiCredentials(
                            password = password,
                            identity = identity,
                            anonymousIdentity = anonymousIdentity,
                        ),
                    )
                },
            ) {
                Text("Connect")
            }
        },
        confirmAction = {
            onConfirm(
                WifiCredentials(
                    password = password,
                    identity = identity,
                    anonymousIdentity = anonymousIdentity,
                ),
            )
        },
        confirmEnabled = passwordValid && (!enterprise || identity.isNotBlank()),
        dismissAction = onDismiss,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWifiNetworkDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, WifiSecurity, WifiCredentials, Boolean) -> Unit,
) {
    var ssid by remember { mutableStateOf("") }
    var security by remember { mutableStateOf(WifiSecurity.WPA2_PERSONAL) }
    var securityMenu by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    var credentials by remember { mutableStateOf(WifiCredentials()) }
    AutomotiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add network") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsFormTextField(
                    focusId = "wifi-add-ssid",
                    label = "Network name (SSID)",
                    value = ssid,
                    onValueChange = { ssid = it },
                    singleLine = true,
                )
                ExposedDropdownMenuBox(
                    expanded = securityMenu,
                    onExpandedChange = { securityMenu = it },
                ) {
                    SettingsFormTextField(
                        focusId = "wifi-add-security",
                        label = "Security",
                        value = security.readableName(),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = securityMenu)
                        },
                        onClick = { securityMenu = !securityMenu },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = securityMenu,
                        onDismissRequest = { securityMenu = false },
                    ) {
                        WifiSecurity.entries
                            .filterNot { it == WifiSecurity.UNKNOWN }
                            .forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.readableName()) },
                                    onClick = {
                                        security = option
                                        securityMenu = false
                                    },
                                )
                            }
                    }
                }
                if (security == WifiSecurity.EAP) {
                    SettingsFormTextField(
                        focusId = "wifi-add-identity",
                        label = "Identity",
                        value = credentials.identity,
                        onValueChange = { credentials = credentials.copy(identity = it) },
                        singleLine = true,
                    )
                }
                if (security !in setOf(WifiSecurity.OPEN, WifiSecurity.OWE)) {
                    SettingsFormTextField(
                        focusId = "wifi-add-password",
                        label = "Password",
                        value = credentials.password,
                        onValueChange = { credentials = credentials.copy(password = it) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                SettingsSwitchRow(
                    title = "Hidden network",
                    checked = hidden,
                    onCheckedChange = { hidden = it },
                )
            }
        },
        confirmButton = {
            Button(
                enabled =
                    ssid.isNotBlank() &&
                        (
                            security in setOf(WifiSecurity.OPEN, WifiSecurity.OWE) ||
                                credentials.password.length >= 8
                        ),
                onClick = { onConfirm(ssid.trim(), security, credentials, hidden) },
            ) {
                Text("Save")
            }
        },
        confirmAction = { onConfirm(ssid.trim(), security, credentials, hidden) },
        confirmEnabled =
            ssid.isNotBlank() &&
                (security in setOf(WifiSecurity.OPEN, WifiSecurity.OWE) || credentials.password.length >= 8),
        dismissAction = onDismiss,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun WifiSecurity.readableName(): String =
    when (this) {
        WifiSecurity.OPEN -> "None"
        WifiSecurity.OWE -> "Enhanced Open"
        WifiSecurity.WEP -> "WEP"
        WifiSecurity.WPA2_PERSONAL -> "WPA2-Personal"
        WifiSecurity.WPA3_PERSONAL -> "WPA3-Personal"
        WifiSecurity.WPA2_WPA3_PERSONAL -> "WPA2/WPA3-Personal"
        WifiSecurity.EAP -> "Enterprise"
        WifiSecurity.UNKNOWN -> "Unknown"
    }
