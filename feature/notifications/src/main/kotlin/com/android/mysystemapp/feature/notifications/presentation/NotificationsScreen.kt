package com.android.car.settings.feature.notifications.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.android.car.settings.feature.notifications.domain.NotificationApp
import java.text.DateFormat
import java.util.Date

@Composable
fun NotificationsRoute(
    viewModel: NotificationsViewModel,
    onBack: () -> Unit,
    onApp: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    SettingsScaffold(
        title = "Notifications",
        onBack = onBack,
        actions = {
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        SnackbarHost(hostState = snackbarHost)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (uiState.notifications.recentlySent.isNotEmpty()) {
                item { SettingsSection("Recently sent") {} }
                items(uiState.notifications.recentlySent, key = NotificationApp::packageName) { app ->
                    NotificationAppRow(
                        app = app,
                        showRecentTime = true,
                        enabled = !uiState.isWorking,
                        onToggle = { viewModel.setNotificationsEnabled(app.packageName, it) },
                        onClick = {
                            viewModel.selectApp(app.packageName)
                            onApp(app.packageName)
                        },
                    )
                }
            }
            item { SettingsSection("All apps") {} }
            if (uiState.notifications.allApps.isEmpty()) {
                item { Text("No apps can send notifications") }
            }
            items(uiState.notifications.allApps, key = NotificationApp::packageName) { app ->
                NotificationAppRow(
                    app = app,
                    enabled = !uiState.isWorking,
                    onToggle = { viewModel.setNotificationsEnabled(app.packageName, it) },
                    onClick = {
                        viewModel.selectApp(app.packageName)
                        onApp(app.packageName)
                    },
                )
            }
        }
    }
}

@Composable
private fun NotificationAppRow(
    app: NotificationApp,
    showRecentTime: Boolean = false,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val summary = when {
        showRecentTime && app.lastNotifiedMillis != null -> {
            val timestamp = requireNotNull(app.lastNotifiedMillis)
            "Last notification: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))}"
        }
        !app.isEnabled -> "App is disabled"
        app.notificationsEnabled -> "Allowed"
        else -> "Blocked"
    }
    SettingsActionRow(
        title = app.label,
        summary = summary,
        enabled = enabled,
        leading = { Icon(Icons.Default.Notifications, contentDescription = null) },
        trailing = {
            androidx.compose.material3.Switch(
                checked = app.notificationsEnabled,
                enabled = enabled && app.notificationsChangeable,
                onCheckedChange = onToggle,
            )
        },
        onClick = onClick,
    )
}

@Composable
fun NotificationAppDetailsRoute(viewModel: NotificationsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val app = uiState.notifications.selectedApp
    SettingsScaffold(title = app?.label ?: "App notifications", onBack = onBack) {
        if (app == null) {
            Text("This app is no longer available")
        } else {
            SettingsSection("Notifications") {
                SettingsSwitchRow(
                    title = "All ${app.label} notifications",
                    summary = if (app.notificationsChangeable) {
                        "Allow this app to send notifications"
                    } else {
                        "This setting is managed by the system or administrator"
                    },
                    checked = app.notificationsEnabled,
                    enabled = app.notificationsChangeable && !uiState.isWorking,
                    onCheckedChange = { viewModel.setNotificationsEnabled(app.packageName, it) },
                )
            }
        }
    }
}
