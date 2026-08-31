package com.android.car.settings.feature.wifi.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSwitchRow

@Composable
fun WifiPreferencesRoute(
    viewModel: WifiViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = uiState.wifi.preferences
    SettingsScaffold(title = "Wi‑Fi preferences", onBack = onBack) {
        SettingsSwitchRow(
            title = "Wi‑Fi scanning",
            summary = "Allow apps and services to scan even when Wi‑Fi is off",
            checked = preferences.scanAlwaysAvailable,
            onCheckedChange = viewModel::setScanAlwaysAvailable,
        )
        SettingsSwitchRow(
            title = "Turn on Wi‑Fi automatically",
            summary = "Turn Wi‑Fi back on near high-quality saved networks",
            checked = preferences.wakeupEnabled,
            onCheckedChange = viewModel::setWakeupEnabled,
        )
        SettingsSwitchRow(
            title = "Open network notification",
            summary = "Notify when a high-quality public network is available",
            checked = preferences.openNetworkNotificationEnabled,
            onCheckedChange = viewModel::setOpenNetworkNotificationEnabled,
        )
        SettingsSwitchRow(
            title = "Switch to mobile data automatically",
            summary = "Use mobile data when Wi‑Fi has no internet access. Data charges may apply.",
            checked = preferences.cellularFallbackEnabled,
            onCheckedChange = viewModel::setCellularFallbackEnabled,
        )
    }
}
