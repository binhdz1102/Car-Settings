package com.android.car.settings.feature.location.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.feature.location.domain.LocationPermissionApp
import com.android.car.settings.feature.location.domain.LocationRecentAccess
import java.text.DateFormat
import java.util.Date
import com.android.car.settings.core.ui.BMaterialLazyColumn as LazyColumn

@Composable
fun LocationRoute(
    viewModel: LocationViewModel,
    onBack: () -> Unit,
    onAppPermissions: () -> Unit,
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    SettingsScaffold(title = "Location", destinationKey = "location", isRoot = true, onBack = onBack) {
        SnackbarHost(snackbarHost)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            item {
                SettingsSwitchRow(
                    title = "Use location",
                    summary =
                        if (uiState.location.locationSupported) {
                            "Allow apps and vehicle services to use location"
                        } else {
                            "Location is not supported on this vehicle"
                        },
                    checked = uiState.location.locationEnabled,
                    enabled = uiState.location.locationSupported && !uiState.isWorking,
                    busy = uiState.isWorking,
                    onCheckedChange = viewModel::setLocationEnabled,
                )
            }
            if (uiState.location.adasLocationSupported) {
                item {
                    SettingsSwitchRow(
                        title = "ADAS location",
                        summary = "Allow driver-assistance systems to use GNSS location",
                        checked = uiState.location.adasLocationEnabled,
                        enabled = !uiState.isWorking,
                        busy = uiState.isWorking,
                        onCheckedChange = viewModel::setAdasLocationEnabled,
                    )
                }
            }
            item {
                SettingsActionRow(
                    title = "App location permissions",
                    summary = "Choose which apps can access location",
                    navigates = true,
                    onClick = onAppPermissions,
                )
            }
            if (uiState.location.providers.isNotEmpty()) {
                item { SettingsSection("Location providers") {} }
                items(uiState.location.providers, key = { it.name }) { provider ->
                    SettingsActionRow(
                        title = provider.name,
                        focusId = "location-provider-${provider.name}",
                        summary = if (provider.enabled) "Available" else "Disabled",
                        enabled = false,
                        leading = { Icon(Icons.Default.LocationOn, null) },
                        onClick = {},
                    )
                }
            }
            item { SettingsSection("Recently accessed") {} }
            if (uiState.location.recentAccesses.isEmpty()) {
                item {
                    Text(
                        "No recent location access",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
            items(uiState.location.recentAccesses, key = LocationRecentAccess::packageName) {
                RecentLocationAccessRow(it)
            }
            item {
                Text(
                    uiState.location.accessDisclaimer,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            uiState.location.lastError?.let { error ->
                item {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun LocationAppsRoute(
    viewModel: LocationViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    SettingsScaffold(title = "App location permissions", onBack = onBack) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (uiState.location.permissionApps.isEmpty()) {
                item {
                    Text(
                        "No apps request location permission",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
            items(uiState.location.permissionApps, key = LocationPermissionApp::packageName) { app ->
                SettingsSwitchRow(
                    title = app.label,
                    focusId = "location-permission-${app.packageName}",
                    summary = if (app.enabled) app.packageName else "App is disabled",
                    checked = app.granted,
                    enabled = app.enabled && !uiState.isWorking,
                    busy = uiState.isWorking,
                    onCheckedChange = { viewModel.setAppPermission(app.packageName, it) },
                )
            }
        }
    }
}

@Composable
private fun RefreshOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) onResume()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun RecentLocationAccessRow(access: LocationRecentAccess) {
    SettingsActionRow(
        title = access.label,
        focusId = "location-recent-${access.packageName}",
        summary =
            DateFormat
                .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(access.lastAccessMillis)),
        onClick = {},
    )
}
