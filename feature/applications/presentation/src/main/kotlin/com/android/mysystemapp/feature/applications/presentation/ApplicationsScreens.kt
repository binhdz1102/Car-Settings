package com.android.car.settings.feature.applications.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.KeyValueRow
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.feature.applications.domain.ApplicationDetails
import com.android.car.settings.feature.applications.domain.ApplicationPrimaryAction
import com.android.car.settings.feature.applications.domain.ApplicationSummary
import com.android.car.settings.feature.applications.domain.SpecialAccessApp
import com.android.car.settings.feature.applications.domain.SpecialAccessType
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun ApplicationsRoute(
    viewModel: ApplicationsViewModel,
    onBack: () -> Unit,
    onAllApplications: () -> Unit,
    onApplication: (String) -> Unit,
    onSpecialAccess: () -> Unit,
    onPerformanceImpactingApps: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    ApplicationsScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onAllApplications = onAllApplications,
        onApplication = onApplication,
        onSpecialAccess = onSpecialAccess,
        onPerformanceImpactingApps = onPerformanceImpactingApps,
        onMessageShown = viewModel::clearMessage,
    )
}

@Composable
private fun ApplicationsScreen(
    state: ApplicationsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAllApplications: () -> Unit,
    onApplication: (String) -> Unit,
    onSpecialAccess: () -> Unit,
    onPerformanceImpactingApps: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            onMessageShown()
        }
    }
    SettingsScaffold(
        title = "Apps",
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        SnackbarHost(hostState = snackbarHost)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Applications") {} }
            item {
                SettingsActionRow(
                    title = "All apps",
                    summary = "${state.applications.apps.size} apps installed",
                    leading = { Icon(Icons.Default.Apps, contentDescription = null) },
                    onClick = onAllApplications,
                )
            }
            if (state.applications.recentApps.isNotEmpty()) {
                item { SettingsSection("Recently opened") {} }
                items(state.applications.recentApps, key = { it.packageName }) { application ->
                    ApplicationRow(application = application, onClick = { onApplication(application.packageName) })
                }
            }
            item { SettingsSection("App settings") {} }
            item {
                SettingsActionRow(
                    title = "App permissions",
                    summary = "Control permissions for apps",
                    onClick = { context.startSettingsSurface(Intent(ACTION_MANAGE_PERMISSIONS)) },
                )
            }
            item {
                SettingsActionRow(
                    title = "Default apps",
                    summary = "Choose default apps for common actions",
                    onClick = {
                        context.startSettingsSurface(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                    },
                )
            }
            item {
                SettingsActionRow(
                    title = "Unused apps",
                    summary = unusedAppsSummary(state.applications.unusedAppCount),
                    onClick = { context.startSettingsSurface(Intent(Intent.ACTION_MANAGE_UNUSED_APPS)) },
                )
            }
            item {
                SettingsActionRow(
                    title = "Performance-impacting apps",
                    summary = performanceImpactingAppsSummary(state.applications.performanceImpactingApps.size),
                    onClick = onPerformanceImpactingApps,
                )
            }
            item {
                SettingsActionRow(
                    title = "Special app access",
                    summary = "Permissions for alarms, usage, notifications and system settings",
                    onClick = onSpecialAccess,
                )
            }
        }
    }
}

@Composable
fun AllApplicationsRoute(
    viewModel: ApplicationsViewModel,
    onBack: () -> Unit,
    onApplication: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    SettingsScaffold(
        title = "All apps",
        onBack = onBack,
        actions = {
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        SnackbarHost(hostState = snackbarHost)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSwitchRow(
                    title = "Show system apps",
                    summary = "Include preinstalled system applications",
                    checked = state.applications.showSystemApps,
                    enabled = !state.isWorking,
                    busy = state.isWorking,
                    onCheckedChange = viewModel::setShowSystemApps,
                )
            }
            if (state.applications.apps.isEmpty()) {
                item {
                    LoadingOrEmpty(isWorking = state.isWorking, emptyText = "No applications found")
                }
            }
            items(state.applications.apps, key = { it.packageName }) { application ->
                ApplicationRow(application = application, onClick = { onApplication(application.packageName) })
            }
        }
    }
}

@Composable
fun ApplicationDetailsRoute(
    viewModel: ApplicationDetailsViewModel,
    onBack: () -> Unit,
    onStorage: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    ApplicationDetailsScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::select,
        onStorage = onStorage,
        onNotifications = viewModel::setNotificationsEnabled,
        onUnusedAppOptimization = viewModel::setUnusedAppOptimizationEnabled,
        onPrioritizePerformance = viewModel::setPrioritizePerformanceEnabled,
        onForceStop = viewModel::forceStop,
        onPrimaryAction = viewModel::performPrimaryAction,
        onMessageShown = viewModel::clearMessage,
    )
}

@Composable
private fun ApplicationDetailsScreen(
    state: ApplicationDetailsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStorage: (String) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onUnusedAppOptimization: (Boolean) -> Unit,
    onPrioritizePerformance: (Boolean) -> Unit,
    onForceStop: () -> Unit,
    onPrimaryAction: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var confirmation by remember { mutableStateOf<DetailAction?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            onMessageShown()
        }
    }
    confirmation?.let { action ->
        ConfirmationDialog(
            action = action,
            onDismiss = { confirmation = null },
            onConfirm = {
                val details = state.details
                when (action) {
                    DetailAction.FORCE_STOP -> onForceStop()
                    DetailAction.PRIMARY -> {
                        if (details?.primaryAction == ApplicationPrimaryAction.UNINSTALL) {
                            details.openUninstall(context)
                        } else {
                            onPrimaryAction()
                        }
                    }
                }
                confirmation = null
            },
        )
    }
    SettingsScaffold(
        title = state.details?.label ?: "App info",
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        SnackbarHost(hostState = snackbarHost)
        val details = state.details
        if (details == null) {
            LoadingOrEmpty(isWorking = state.isWorking, emptyText = "Application is unavailable")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        AppIcon(packageName = details.packageName)
                        Text(details.label)
                        Text(details.packageName)
                    }
                }
                item {
                    AppActions(
                        details = details,
                        isWorking = state.isWorking,
                        onPrimary = { confirmation = DetailAction.PRIMARY },
                        onForceStop = { confirmation = DetailAction.FORCE_STOP },
                    )
                }
                item { SettingsSection("Usage") {} }
                details.notificationsEnabled?.let { notificationsEnabled ->
                    item {
                        SettingsSwitchRow(
                            title = "Notifications",
                            summary =
                                if (details.notificationsChangeable) {
                                    "Allow this app to send notifications"
                                } else {
                                    "Notification settings are controlled by the system"
                                },
                            checked = notificationsEnabled,
                            enabled = !state.isWorking && details.notificationsChangeable,
                            busy = state.isWorking,
                            onCheckedChange = onNotifications,
                        )
                    }
                }
                item {
                    SettingsActionRow(
                        title = "Permissions",
                        summary = details.permissionsSummary,
                        onClick = { details.openPermissions(context) },
                    )
                }
                item {
                    SettingsActionRow(
                        title = "Storage & cache",
                        summary = details.storageBytes?.toStorageString() ?: "Storage unavailable",
                        onClick = { onStorage(details.packageName) },
                    )
                }
                details.unusedAppOptimizationEnabled?.let { enabled ->
                    item {
                        SettingsSwitchRow(
                            title = "Pause app activity if unused",
                            summary = "Remove permissions and free space for unused apps",
                            checked = enabled,
                            enabled = !state.isWorking,
                            busy = state.isWorking,
                            onCheckedChange = onUnusedAppOptimization,
                        )
                    }
                }
                if (details.canPrioritizePerformance) {
                    item {
                        SettingsSwitchRow(
                            title = "Prioritize app performance",
                            summary = "Keep this app running in the background",
                            checked = details.prioritizePerformanceEnabled == true,
                            enabled = !state.isWorking,
                            busy = state.isWorking,
                            onCheckedChange = onPrioritizePerformance,
                        )
                    }
                }
                item { SettingsSection("App details") {} }
                item { KeyValueRow(key = "Version", value = details.versionName.ifEmpty { "Unavailable" }) }
                item { KeyValueRow(key = "System app", value = if (details.isSystemApp) "Yes" else "No") }
                item { KeyValueRow(key = "Enabled", value = if (details.isEnabled) "Yes" else "No") }
            }
        }
    }
}

@Composable
fun ApplicationStorageRoute(
    viewModel: ApplicationDetailsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    val snackbarHost = remember { SnackbarHostState() }
    var confirmation by remember { mutableStateOf<StorageAction?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    confirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (action == StorageAction.CLEAR_STORAGE) "Clear storage?" else "Clear cache?") },
            text = {
                Text(
                    if (action == StorageAction.CLEAR_STORAGE) {
                        "This removes this app's data and settings."
                    } else {
                        "This removes temporary files for this app."
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (action == StorageAction.CLEAR_STORAGE) viewModel.clearStorage() else viewModel.clearCache()
                        confirmation = null
                    },
                ) { Text("Clear") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmation = null }) { Text("Cancel") } },
        )
    }
    SettingsScaffold(title = "Storage & cache", onBack = onBack) {
        SnackbarHost(hostState = snackbarHost)
        val details = state.details
        if (details == null) {
            LoadingOrEmpty(isWorking = state.isWorking, emptyText = "Application is unavailable")
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsSection("Storage") {
                    KeyValueRow(
                        key = "Total",
                        value = details.storageBytes?.toStorageString() ?: "Unavailable",
                    )
                    KeyValueRow(
                        key = "Cache",
                        value = details.cacheBytes?.toStorageString() ?: "Unavailable",
                    )
                }
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        enabled = !state.isWorking,
                        onClick = { confirmation = StorageAction.CLEAR_CACHE },
                    ) { Text("Clear cache") }
                    Button(
                        enabled = !state.isWorking,
                        onClick = { confirmation = StorageAction.CLEAR_STORAGE },
                    ) { Text("Clear storage") }
                }
            }
        }
    }
}

@Composable
fun SpecialAppAccessRoute(
    onBack: () -> Unit,
    onSpecialAccessType: (SpecialAccessType) -> Unit,
) {
    val context = LocalContext.current
    SettingsScaffold(title = "Special app access", onBack = onBack) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Special permissions") {} }
            item {
                SettingsActionRow(
                    title = "Alarms & reminders",
                    summary = "Allow apps to schedule exact alarms",
                    onClick = { onSpecialAccessType(SpecialAccessType.ALARMS_AND_REMINDERS) },
                )
            }
            item {
                SettingsActionRow(
                    title = "Modify system settings",
                    summary = "Allow apps to change system settings",
                    onClick = { onSpecialAccessType(SpecialAccessType.MODIFY_SYSTEM_SETTINGS) },
                )
            }
            item {
                SettingsActionRow(
                    title = "Usage access",
                    summary = "Allow apps to access usage information",
                    onClick = { onSpecialAccessType(SpecialAccessType.USAGE_ACCESS) },
                )
            }
            item {
                SettingsActionRow(
                    title = "Wi-Fi control",
                    summary = "Allow apps to control Wi-Fi",
                    onClick = { onSpecialAccessType(SpecialAccessType.WIFI_CONTROL) },
                )
            }
            item {
                SettingsActionRow(
                    title = "Notification access",
                    summary = "Allow apps to read and act on notifications",
                    onClick = {
                        context.startSettingsSurface(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                )
            }
            item {
                SettingsActionRow(
                    title = "More special access",
                    summary = "Additional special permissions",
                    onClick = {
                        context.startSettingsSurface(Intent(ACTION_MANAGE_SPECIAL_APP_ACCESSES))
                    },
                )
            }
        }
    }
}

@Composable
fun SpecialAccessListRoute(
    viewModel: SpecialAccessViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    val access = state.specialAccess
    SettingsScaffold(
        title = access.type?.title ?: "Special app access",
        onBack = onBack,
        actions = {
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        SnackbarHost(hostState = snackbarHost)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSwitchRow(
                    title = "Show system apps",
                    summary = "Include preinstalled system applications",
                    checked = access.showSystemApps,
                    enabled = !state.isWorking,
                    busy = state.isWorking,
                    onCheckedChange = viewModel::setShowSystemApps,
                )
            }
            item { SettingsSection(access.type?.description.orEmpty()) {} }
            if (access.apps.isEmpty()) {
                item {
                    LoadingOrEmpty(
                        isWorking = state.isWorking || access.isLoading,
                        emptyText = "No applications request this access",
                    )
                }
            }
            items(access.apps, key = { it.packageName }) { app ->
                SpecialAccessRow(
                    app = app,
                    enabled = !state.isWorking,
                    onCheckedChange = { viewModel.setAllowed(app.packageName, it) },
                )
            }
        }
    }
}

@Composable
fun PerformanceImpactingApplicationsRoute(
    viewModel: ApplicationsViewModel,
    onBack: () -> Unit,
    onApplication: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    SettingsScaffold(
        title = "Performance-impacting apps",
        onBack = onBack,
        actions = {
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Apps disabled after resource overuse") {} }
            if (state.applications.performanceImpactingApps.isEmpty()) {
                item {
                    LoadingOrEmpty(
                        isWorking = state.isWorking,
                        emptyText = "No apps are affecting vehicle performance",
                    )
                }
            }
            items(state.applications.performanceImpactingApps, key = { it.packageName }) { app ->
                ApplicationRow(application = app, onClick = { onApplication(app.packageName) })
            }
        }
    }
}

@Composable
private fun ApplicationRow(
    application: ApplicationSummary,
    onClick: () -> Unit,
) {
    SettingsActionRow(
        title = application.label,
        summary = buildString {
            if (!application.isEnabled) append("Disabled")
            if (application.versionName.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append(application.versionName)
            }
        }.ifEmpty { application.packageName },
        leading = { AppIcon(packageName = application.packageName) },
        onClick = onClick,
    )
}

@Composable
private fun SpecialAccessRow(
    app: SpecialAccessApp,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsSwitchRow(
        title = app.label,
        summary = if (app.isEnabled) app.packageName else "Disabled",
        checked = app.isAllowed,
        enabled = enabled && app.isEnabled,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap =
        remember(packageName) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(48, 48).asImageBitmap()
            }.getOrNull()
        }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
        )
    } else {
        Icon(Icons.Default.Apps, contentDescription = null)
    }
}

@Composable
private fun AppActions(
    details: ApplicationDetails,
    isWorking: Boolean,
    onPrimary: () -> Unit,
    onForceStop: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (details.primaryAction != ApplicationPrimaryAction.NONE) {
            Button(enabled = !isWorking, onClick = onPrimary) {
                Text(details.primaryAction.title())
            }
        }
        OutlinedButton(enabled = details.canForceStop && !isWorking, onClick = onForceStop) {
            Text("Force stop")
        }
    }
}

@Composable
private fun LoadingOrEmpty(
    isWorking: Boolean,
    emptyText: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isWorking) CircularProgressIndicator()
        Text(if (isWorking) "Loading applications…" else emptyText)
    }
}

@Composable
private fun ConfirmationDialog(
    action: DetailAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isForceStop = action == DetailAction.FORCE_STOP
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isForceStop) "Force stop app?" else "Confirm app action") },
        text = {
            Text(
                if (isForceStop) {
                    "The app may not work correctly after it is stopped."
                } else {
                    "This action changes the application state."
                },
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Confirm") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RefreshOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun ApplicationDetails.openPermissions(context: Context) {
    context.startSettingsSurface(
        Intent(ACTION_MANAGE_APP_PERMISSIONS).putExtra(Intent.EXTRA_PACKAGE_NAME, packageName),
    )
}

private fun ApplicationDetails.openUninstall(context: Context) {
    context.startSettingsSurface(
        Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$packageName"))
            .putExtra(Intent.EXTRA_RETURN_RESULT, true),
    )
}

private fun Context.startSettingsSurface(intent: Intent) {
    runCatching {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// AAOS Settings uses these platform actions. They are available on the target
// image but hidden from the public SDK stubs used by this project.
private const val ACTION_MANAGE_PERMISSIONS = "android.intent.action.MANAGE_PERMISSIONS"
private const val ACTION_MANAGE_APP_PERMISSIONS = "android.intent.action.MANAGE_APP_PERMISSIONS"
private const val ACTION_MANAGE_SPECIAL_APP_ACCESSES = "android.intent.action.MANAGE_SPECIAL_APP_ACCESSES"

private fun ApplicationPrimaryAction.title(): String =
    when (this) {
        ApplicationPrimaryAction.UNINSTALL -> "Uninstall"
        ApplicationPrimaryAction.DISABLE -> "Disable"
        ApplicationPrimaryAction.ENABLE -> "Enable"
        ApplicationPrimaryAction.NONE -> ""
    }

private fun unusedAppsSummary(count: Int?): String =
    when (count) {
        null -> "Manage permissions and space used by unused apps"
        0 -> "No unused apps"
        1 -> "1 unused app"
        else -> "$count unused apps"
    }

private fun performanceImpactingAppsSummary(count: Int): String =
    when (count) {
        0 -> "No apps disabled for resource overuse"
        1 -> "1 app disabled for resource overuse"
        else -> "$count apps disabled for resource overuse"
    }

private fun Long.toStorageString(): String {
    if (this < 1_024L) return "$this B"
    val unit = 1_024.0
    val exponent = (ln(toDouble()) / ln(unit)).toInt().coerceIn(1, 4)
    val prefix = "KMGT"[exponent - 1]
    val value = this / unit.pow(exponent.toDouble())
    return "%.1f %sB".format(Locale.getDefault(), value, prefix)
}

private enum class DetailAction {
    FORCE_STOP,
    PRIMARY,
}

private enum class StorageAction {
    CLEAR_STORAGE,
    CLEAR_CACHE,
}
