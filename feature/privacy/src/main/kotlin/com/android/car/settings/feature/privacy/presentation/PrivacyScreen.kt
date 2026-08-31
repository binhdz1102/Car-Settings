package com.android.car.settings.feature.privacy.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.feature.privacy.R
import com.android.car.settings.feature.privacy.domain.PrivacyAccessApp
import com.android.car.settings.feature.privacy.domain.PrivacyPermissionType
import com.android.car.settings.feature.privacy.domain.PrivacySensorState
import com.android.car.settings.feature.privacy.domain.PrivacySensorWriteBlock
import java.text.DateFormat
import java.util.Date
import com.android.car.settings.core.ui.AutomotiveLazyColumn as LazyColumn

@Composable
fun PrivacyRoute(
    viewModel: PrivacyViewModel,
    onBack: () -> Unit,
    onMicrophone: () -> Unit,
    onCamera: () -> Unit,
    onLocation: () -> Unit,
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScaffold(
        title = stringResource(R.string.privacy_title),
        destinationKey = "privacy",
        isRoot = true,
        onBack = onBack,
        firstContentFocusId = "privacy-microphone",
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            item(key = "privacy-microphone") {
                SettingsActionRow(
                    title = stringResource(R.string.privacy_microphone),
                    summary = accessSummary(uiState.privacy.microphone),
                    focusId = "privacy-microphone",
                    navigates = true,
                    leading = { androidx.compose.material3.Icon(Icons.Default.Mic, null) },
                    onClick = onMicrophone,
                )
            }
            item(key = "privacy-camera") {
                SettingsActionRow(
                    title = stringResource(R.string.privacy_camera),
                    summary = accessSummary(uiState.privacy.camera),
                    focusId = "privacy-camera",
                    navigates = true,
                    leading = { androidx.compose.material3.Icon(Icons.Default.PhotoCamera, null) },
                    onClick = onCamera,
                )
            }
            item(key = "privacy-location") {
                SettingsActionRow(
                    title = stringResource(R.string.privacy_location),
                    summary =
                        stringResource(
                            if (uiState.privacy.locationEnabled) R.string.privacy_on else R.string.privacy_off,
                        ),
                    focusId = "privacy-location",
                    navigates = true,
                    leading = { androidx.compose.material3.Icon(Icons.Default.LocationOn, null) },
                    onClick = onLocation,
                )
            }
        }
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
        uiState.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    val sensor = if (type == PrivacyPermissionType.MICROPHONE) uiState.privacy.microphone else uiState.privacy.camera
    val recent =
        if (type == PrivacyPermissionType.MICROPHONE) {
            uiState.privacy.recentMicrophoneAccess
        } else {
            uiState.privacy.recentCameraAccess
        }
    val typeTitle = stringResource(type.titleRes)
    val typeSubject = stringResource(type.subjectRes)
    SettingsScaffold(title = typeTitle, onBack = onBack) {
        SnackbarHost(snackbarHost)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSwitchRow(
                    title = stringResource(R.string.privacy_sensor_access_title, typeTitle),
                    summary = sensorWriteSummary(sensor, typeSubject),
                    checked = sensor.accessEnabled,
                    enabled = sensor.supported && sensor.writable && !uiState.isWorking,
                    retainFocusWhenDisabled = uiState.isWorking,
                    onCheckedChange = {
                        if (type == PrivacyPermissionType.MICROPHONE) {
                            viewModel.setMicrophoneAccessEnabled(it)
                        } else {
                            viewModel.setCameraAccessEnabled(it)
                        }
                    },
                )
            }
            item { SettingsSection(stringResource(R.string.privacy_recently_accessed)) {} }
            if (recent.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.privacy_no_recent_access),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
            items(recent, key = PrivacyAccessApp::packageName) { RecentAccessRow(it) }
            item {
                SettingsActionRow(
                    title = stringResource(R.string.privacy_manage_permissions_title, typeSubject),
                    summary = stringResource(R.string.privacy_choose_permission_apps),
                    navigates = true,
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
    SettingsScaffold(title = stringResource(R.string.privacy_location), onBack = onBack) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSwitchRow(
                    title = stringResource(R.string.privacy_use_location),
                    summary = stringResource(R.string.privacy_location_summary),
                    checked = uiState.privacy.locationEnabled,
                    enabled = !uiState.isWorking,
                    retainFocusWhenDisabled = uiState.isWorking,
                    onCheckedChange = viewModel::setLocationEnabled,
                )
            }
            item {
                SettingsActionRow(
                    title = stringResource(R.string.privacy_app_location_permissions),
                    summary = stringResource(R.string.privacy_choose_location_apps),
                    navigates = true,
                    onClick = {
                        viewModel.selectPermissionType(PrivacyPermissionType.LOCATION)
                        onManagePermissions(PrivacyPermissionType.LOCATION)
                    },
                )
            }
            item { SettingsSection(stringResource(R.string.privacy_recently_accessed)) {} }
            if (uiState.privacy.recentLocationAccess.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.privacy_no_recent_access),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
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
    val typeTitle = stringResource(type.titleRes)
    val typeSubject = stringResource(type.subjectRes)
    SettingsScaffold(title = stringResource(R.string.privacy_permissions_title, typeTitle), onBack = onBack) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (uiState.privacy.permissionApps.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.privacy_no_permission_apps),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
            items(uiState.privacy.permissionApps, key = { it.packageName }) { app ->
                SettingsSwitchRow(
                    title = app.label,
                    summary =
                        if (app.isEnabled) {
                            stringResource(R.string.privacy_allow_subject_access, typeSubject)
                        } else {
                            stringResource(R.string.privacy_app_disabled)
                        },
                    checked = app.granted,
                    focusId = "privacy-permission-${app.packageName}",
                    enabled = app.isEnabled && !uiState.isWorking,
                    retainFocusWhenDisabled = uiState.isWorking,
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
        focusId = "privacy-recent-${app.packageName}",
        onClick = {},
    )
}

@Composable
private fun accessSummary(sensor: PrivacySensorState): String =
    when {
        sensor.unavailableReason != null -> sensor.unavailableReason.orEmpty()
        !sensor.supported -> stringResource(R.string.privacy_not_supported)
        sensor.accessEnabled -> stringResource(R.string.privacy_on)
        else -> stringResource(R.string.privacy_off)
    }

@Composable
private fun sensorWriteSummary(
    sensor: PrivacySensorState,
    subject: String,
): String =
    when (sensor.writeBlock) {
        PrivacySensorWriteBlock.MANAGE_PERMISSION_REQUIRED -> stringResource(R.string.privacy_sensor_read_only)
        null -> stringResource(R.string.privacy_allow_apps_sensor, subject)
    }
