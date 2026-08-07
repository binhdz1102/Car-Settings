package com.android.car.settings.feature.privacy.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.feature.privacy.domain.PrivacyAccessApp
import com.android.car.settings.feature.privacy.domain.PrivacyPermissionType
import java.text.DateFormat
import java.util.Date

@Composable
fun PrivacyRoute(
    viewModel: PrivacyViewModel,
    onBack: () -> Unit,
    onMicrophone: () -> Unit,
    onCamera: () -> Unit,
    onLocation: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScaffold(title = "Privacy", onBack = onBack) {
        SettingsActionRow(
            title = "Microphone",
            summary = accessSummary(uiState.privacy.microphone),
            leading = { androidx.compose.material3.Icon(Icons.Default.Mic, null) },
            onClick = onMicrophone,
        )
        SettingsActionRow(
            title = "Camera",
            summary = accessSummary(uiState.privacy.camera),
            leading = { androidx.compose.material3.Icon(Icons.Default.PhotoCamera, null) },
            onClick = onCamera,
        )
        SettingsActionRow(
            title = "Location",
            summary = if (uiState.privacy.locationEnabled) "On" else "Off",
            leading = { androidx.compose.material3.Icon(Icons.Default.LocationOn, null) },
            onClick = onLocation,
        )
    }
}

@Composable
fun SensorPrivacyRoute(
    type: PrivacyPermissionType,
    viewModel: PrivacyViewModel,
    onBack: () -> Unit,
    onManagePermissions: (PrivacyPermissionType) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbarHost.showSnackbar(it); viewModel.clearMessage() }
    }
    val sensor = if (type == PrivacyPermissionType.MICROPHONE) uiState.privacy.microphone else uiState.privacy.camera
    val recent = if (type == PrivacyPermissionType.MICROPHONE) {
        uiState.privacy.recentMicrophoneAccess
    } else {
        uiState.privacy.recentCameraAccess
    }
    SettingsScaffold(title = type.title, onBack = onBack) {
        SnackbarHost(snackbarHost)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSwitchRow(
                    title = "${type.title} access",
                    summary = "Allow apps to access the ${type.title.lowercase()}",
                    checked = sensor.accessEnabled,
                    enabled = sensor.supported && !uiState.isWorking,
                    onCheckedChange = {
                        if (type == PrivacyPermissionType.MICROPHONE) {
                            viewModel.setMicrophoneAccessEnabled(it)
                        } else {
                            viewModel.setCameraAccessEnabled(it)
                        }
                    },
                )
            }
            item { SettingsSection("Recently accessed") {} }
            if (recent.isEmpty()) item { Text("No recent access") }
            items(recent, key = PrivacyAccessApp::packageName) { RecentAccessRow(it) }
            item {
                SettingsActionRow(
                    title = "Manage ${type.title.lowercase()} permissions",
                    summary = "Choose which apps can access this permission",
                    onClick = {
                        viewModel.selectPermissionType(type)
                        onManagePermissions(type)
                    },
                )
            }
        }
    }
}

@Composable
fun LocationPrivacyRoute(
    viewModel: PrivacyViewModel,
    onBack: () -> Unit,
    onManagePermissions: (PrivacyPermissionType) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScaffold(title = "Location", onBack = onBack) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSwitchRow(
                    title = "Use location",
                    summary = "Allow apps and services to use this vehicle's location",
                    checked = uiState.privacy.locationEnabled,
                    enabled = !uiState.isWorking,
                    onCheckedChange = viewModel::setLocationEnabled,
                )
            }
            item {
                SettingsActionRow(
                    title = "App location permissions",
                    summary = "Choose which apps can access location",
                    onClick = {
                        viewModel.selectPermissionType(PrivacyPermissionType.LOCATION)
                        onManagePermissions(PrivacyPermissionType.LOCATION)
                    },
                )
            }
            item { SettingsSection("Recently accessed") {} }
            if (uiState.privacy.recentLocationAccess.isEmpty()) item { Text("No recent access") }
            items(uiState.privacy.recentLocationAccess, key = PrivacyAccessApp::packageName) {
                RecentAccessRow(it)
            }
        }
    }
}

@Composable
fun PermissionAppsRoute(
    type: PrivacyPermissionType,
    viewModel: PrivacyViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(type) { viewModel.selectPermissionType(type) }
    SettingsScaffold(title = "${type.title} permissions", onBack = onBack) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (uiState.privacy.permissionApps.isEmpty()) item { Text("No apps request this permission") }
            items(uiState.privacy.permissionApps, key = { it.packageName }) { app ->
                SettingsSwitchRow(
                    title = app.label,
                    summary = if (app.isEnabled) "Allow ${type.title.lowercase()} access" else "App is disabled",
                    checked = app.granted,
                    enabled = app.isEnabled && !uiState.isWorking,
                    onCheckedChange = { viewModel.setAppPermission(app.packageName, type, it) },
                )
            }
        }
    }
}

@Composable
private fun RecentAccessRow(app: PrivacyAccessApp) {
    SettingsActionRow(
        title = app.label,
        summary = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(app.lastAccessMillis)),
        onClick = {},
    )
}

private fun accessSummary(sensor: com.android.car.settings.feature.privacy.domain.PrivacySensorState): String = when {
    sensor.unavailableReason != null -> sensor.unavailableReason.orEmpty()
    !sensor.supported -> "Not supported on this vehicle"
    sensor.accessEnabled -> "On"
    else -> "Off"
}
