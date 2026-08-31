package com.android.car.settings.feature.wifi.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsFormButton
import com.android.car.settings.core.ui.SettingsFormTextField
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.core.ui.automotiveScrollbar
import com.android.car.settings.feature.wifi.domain.HotspotBand
import com.android.car.settings.feature.wifi.domain.HotspotSecurity

@Composable
fun WifiHotspotRoute(
    viewModel: WifiViewModel,
    onBack: () -> Unit,
    onShareQr: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val platformConfig = uiState.wifi.hotspot.configuration
    var config by remember(platformConfig) { mutableStateOf(platformConfig) }
    val hotspotScrollState = rememberScrollState()
    SettingsScaffold(
        title = "Wi‑Fi hotspot",
        destinationKey = "wifi-hotspot",
        onBack = onBack,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .automotiveScrollbar(hotspotScrollState)
                    .verticalScroll(hotspotScrollState),
        ) {
            SettingsSwitchRow(
                title = "Use Wi‑Fi hotspot",
                summary = "${uiState.wifi.hotspot.clients} connected devices",
                checked = uiState.wifi.hotspot.isEnabled,
                busy = uiState.wifi.hotspot.isTransitioning,
                onCheckedChange = viewModel::setHotspotEnabled,
            )
            if (uiState.wifi.hotspot.isEnabled && config.ssid.isNotBlank()) {
                SettingsActionRow(
                    title = "Share hotspot",
                    summary = "Show a QR code for nearby devices",
                    onClick = onShareQr,
                )
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                // Every field is a real rotary item. The CCP item reserves the inter-control
                // gap, so do not add a second Compose gap here (it was the source of the large
                // empty bands visible between hotspot fields).
            ) {
                SettingsFormTextField(
                    focusId = "wifi-hotspot-ssid",
                    value = config.ssid,
                    onValueChange = { config = config.copy(ssid = it) },
                    label = "Hotspot name",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                SelectionField(
                    label = "Security",
                    value = config.security.name.replace('_', ' '),
                    options = HotspotSecurity.entries,
                    onSelected = { config = config.copy(security = it) },
                )
                if (config.security != HotspotSecurity.OPEN) {
                    SettingsFormTextField(
                        focusId = "wifi-hotspot-password",
                        value = config.password,
                        onValueChange = { config = config.copy(password = it) },
                        label = "Password",
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
                SelectionField(
                    label = "AP band",
                    value = config.band.name.replace('_', ' '),
                    options =
                        HotspotBand.entries.filter {
                            (it != HotspotBand.GHZ_5 || uiState.wifi.hotspot.supports5Ghz) &&
                                (
                                    it != HotspotBand.DUAL ||
                                        uiState.wifi.hotspot.supportsDualBand
                                )
                        },
                    onSelected = { config = config.copy(band = it) },
                )
                SettingsSwitchRow(
                    title = "Turn off automatically",
                    summary = "Turn hotspot off when no device is connected",
                    checked = config.autoShutdownEnabled,
                    onCheckedChange = { config = config.copy(autoShutdownEnabled = it) },
                )
                if (uiState.wifi.preferences.persistentTetheringSupported) {
                    SettingsSwitchRow(
                        title = "Keep hotspot on",
                        summary = "Keep Wi‑Fi hotspot available while the vehicle is parked",
                        checked = uiState.wifi.preferences.persistentTetheringEnabled,
                        onCheckedChange = viewModel::setPersistentTetheringEnabled,
                    )
                }
                SettingsFormButton(
                    focusId = "wifi-hotspot-save",
                    label = "Save configuration",
                    enabled =
                        config.ssid.isNotBlank() &&
                            (
                                config.security == HotspotSecurity.OPEN ||
                                    config.password.length >= 8
                            ),
                    onClick = { viewModel.updateHotspotConfiguration(config) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun <T> SelectionField(
    label: String,
    value: String,
    options: List<T>,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        SettingsFormButton(
            focusId = "wifi-hotspot-selection-$label",
            label = "$label: $value",
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.toString().replace('_', ' ')) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
