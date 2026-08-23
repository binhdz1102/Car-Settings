package com.android.car.settings.feature.applications.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.AutomotiveAlertDialog
import com.android.car.settings.core.ui.KeyValueRow
import com.android.car.settings.core.ui.PackageAppIcon
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsAppBarAction
import com.android.car.settings.core.ui.SettingsFormButton
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
import com.android.car.settings.core.ui.AutomotiveButton as Button
import com.android.car.settings.core.ui.AutomotiveOutlinedButton as OutlinedButton
import com.android.car.settings.core.ui.BMaterialLazyColumn as LazyColumn

@Composable
fun ApplicationsRoute(
    viewModel: ApplicationsViewModel,
    onBack: () -> Unit,
    onAllApplications: () -> Unit,
    onApplication: (String) -> Unit,
    onSpecialAccess: () -> Unit,
    onPerformanceImpactingApps: () -> Unit,
    onPermissions: () -> Unit,
    onUnusedApplications: () -> Unit,
    onDefaultApps: () -> Unit,
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    ApplicationsScreen(
        state = state,
        listState = listState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onAllApplications = onAllApplications,
        onApplication = onApplication,
        onSpecialAccess = onSpecialAccess,
        onPerformanceImpactingApps = onPerformanceImpactingApps,
        onPermissions = onPermissions,
        onUnusedApplications = onUnusedApplications,
        onDefaultApps = onDefaultApps,
        onMessageShown = viewModel::clearMessage,
    )
}

@Composable
private fun ApplicationsScreen(
    state: ApplicationsUiState,
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAllApplications: () -> Unit,
    onApplication: (String) -> Unit,
    onSpecialAccess: () -> Unit,
    onPerformanceImpactingApps: () -> Unit,
    onPermissions: () -> Unit,
    onUnusedApplications: () -> Unit,
    onDefaultApps: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            onMessageShown()
        }
    }
    SettingsScaffold(
        title = "Apps",
        destinationKey = "applications",
        isRoot = true,
        onBack = onBack,
        actions = {
            SettingsAppBarAction(
                id = "applications-refresh",
                contentDescription = "Refresh",
                onClick = onRefresh,
            ) { Icon(Icons.Default.Refresh, contentDescription = null) }
        },
    ) {
        SnackbarHost(hostState = snackbarHost)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            item { SettingsSection("Applications") {} }
            item {
                SettingsActionRow(
                    navigates = true,
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
                    navigates = true,
                    title = "App permissions",
                    summary = "Control permissions for apps",
                    onClick = onPermissions,
                )
            }
            item {
                SettingsActionRow(
                    navigates = true,
                    title = "Default apps",
                    summary = "Choose default apps for common actions",
                    onClick = onDefaultApps,
                )
            }
            item {
                SettingsActionRow(
                    navigates = true,
                    title = "Unused apps",
                    summary = unusedAppsSummary(state.applications.unusedAppCount),
                    onClick = onUnusedApplications,
                )
            }
            item {
                SettingsActionRow(
                    navigates = true,
                    title = "Performance-impacting apps",
                    summary = performanceImpactingAppsSummary(state.applications.performanceImpactingApps.size),
                    onClick = onPerformanceImpactingApps,
                )
            }
            item {
                SettingsActionRow(
                    navigates = true,
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
            SettingsAppBarAction(
                id = "all-apps-refresh",
                contentDescription = "Refresh",
                onClick = viewModel::refresh,
            ) { Icon(Icons.Default.Refresh, contentDescription = null) }
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
    onPermissions: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    ApplicationDetailsScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::select,
        onStorage = onStorage,
        onPermissions = onPermissions,
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
    onPermissions: (String) -> Unit,
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
            SettingsAppBarAction(
                id = "application-details-refresh",
                contentDescription = "Refresh",
                onClick = onRefresh,
            ) { Icon(Icons.Default.Refresh, contentDescription = null) }
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
                        PackageAppIcon(packageName = details.packageName)
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
                        onClick = { onPermissions(details.packageName) },
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
        AutomotiveAlertDialog(
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
            confirmAction = {
                if (action == StorageAction.CLEAR_STORAGE) viewModel.clearStorage() else viewModel.clearCache()
                confirmation = null
            },
            dismissAction = { confirmation = null },
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
                    SettingsFormButton(
                        focusId = "application-clear-cache",
                        label = "Clear cache",
                        enabled = !state.isWorking,
                        onClick = { confirmation = StorageAction.CLEAR_CACHE },
                    )
                    SettingsFormButton(
                        focusId = "application-clear-storage",
                        label = "Clear storage",
                        enabled = !state.isWorking,
                        onClick = { confirmation = StorageAction.CLEAR_STORAGE },
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionGroupsRoute(
    viewModel: PermissionGroupsViewModel,
    onBack: () -> Unit,
    onGroup: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    SettingsScaffold(
        title = "App permissions",
        onBack = onBack,
        actions = {
            SettingsAppBarAction(
                id = "permissions-refresh",
                contentDescription = "Refresh",
                onClick = viewModel::refresh,
            ) { Icon(Icons.Default.Refresh, contentDescription = null) }
        },
    ) {
        PermissionMessageHost(state.message, viewModel::clearMessage)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Permission groups") {} }
            if (state.groups.isEmpty()) {
                item { LoadingOrEmpty(state.isWorking, "No runtime permission groups found") }
            }
            items(state.groups, key = { it.groupName }) { group ->
                SettingsActionRow(
                    title = group.label,
                    summary = "${group.grantedApplicationCount} of ${group.applicationCount} apps allowed",
                    leading = { Icon(Icons.Default.Apps, contentDescription = null) },
                    focusId = "permission-group-${group.groupName}",
                    onClick = { onGroup(group.groupName) },
                )
            }
        }
    }
}

@Composable
fun PermissionGroupApplicationsRoute(
    viewModel: PermissionGroupsViewModel,
    onBack: () -> Unit,
    onApplication: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    val title =
        state.selectedGroup
            ?.substringAfterLast('.')
            ?.replace('_', ' ')
            ?.replaceFirstChar(Char::uppercaseChar)
            ?: "Permission group"
    SettingsScaffold(title = title, onBack = onBack) {
        PermissionMessageHost(state.message, viewModel::clearMessage)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Apps with access") {} }
            if (state.applications.isEmpty()) {
                item { LoadingOrEmpty(state.isWorking, "No applications request this permission") }
            }
            items(state.applications, key = { it.packageName }) { application ->
                ApplicationRow(application = application, onClick = { onApplication(application.packageName) })
            }
        }
    }
}

@Composable
fun ApplicationPermissionsRoute(
    viewModel: ApplicationPermissionsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    SettingsScaffold(title = state.permissions.firstOrNull()?.applicationLabel ?: "App permissions", onBack = onBack) {
        PermissionMessageHost(state.message, viewModel::clearMessage)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Permissions") {} }
            if (state.permissions.isEmpty()) {
                item { LoadingOrEmpty(state.isWorking, "This app has no runtime permissions") }
            }
            items(state.permissions, key = { it.permissionName }) { permission ->
                SettingsSwitchRow(
                    title = permission.label,
                    summary = permission.grantMode.displayLabel() + if (permission.isFixed) " · Locked by policy" else "",
                    checked = permission.isGranted,
                    enabled = permission.canChange && !state.isWorking,
                    busy = state.isWorking,
                    onCheckedChange = { viewModel.setPermission(permission.permissionName, it) },
                )
            }
        }
    }
}

@Composable
fun UnusedApplicationsRoute(
    viewModel: ApplicationsViewModel,
    onBack: () -> Unit,
    onApplication: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    SettingsScaffold(title = "Unused apps", onBack = onBack) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSection("Apps not used recently") {
                    Text("Unused apps can have permissions removed and may be hibernated by the system.")
                }
            }
            if (state.applications.unusedApps.isEmpty()) {
                item { LoadingOrEmpty(state.isWorking, "No unused apps") }
            }
            items(state.applications.unusedApps, key = { it.packageName }) { application ->
                ApplicationRow(application = application, onClick = { onApplication(application.packageName) })
            }
        }
    }
}

@Composable
fun DefaultApplicationsRoute(
    viewModel: DefaultAppsViewModel,
    onBack: () -> Unit,
    onOpeningLinks: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    SettingsScaffold(
        title = "Default apps",
        onBack = onBack,
        actions = {
            SettingsAppBarAction(
                id = "default-apps-refresh",
                contentDescription = "Refresh",
                onClick = viewModel::refresh,
            ) { Icon(Icons.Default.Refresh, contentDescription = null) }
        },
    ) {
        PermissionMessageHost(state.message, viewModel::clearMessage)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Choose default apps") {} }
            if (state.roles.isEmpty()) {
                item { LoadingOrEmpty(state.isWorking, "No default app roles are available") }
            }
            state.roles.forEach { role ->
                item(key = role.roleName) {
                    SettingsSection(role.title) {
                        Text(
                            role.defaultPackageName?.let { current ->
                                role.candidates.firstOrNull { it.packageName == current }?.label
                                    ?: current
                            } ?: "Not selected",
                        )
                    }
                }
                items(role.candidates, key = { "${role.roleName}-${it.packageName}" }) { candidate ->
                    SettingsActionRow(
                        title = candidate.label,
                        summary = if (candidate.packageName == role.defaultPackageName) "Current default" else candidate.packageName,
                        leading = { PackageAppIcon(packageName = candidate.packageName) },
                        focusId = "default-${role.roleName}-${candidate.packageName}",
                        onClick = { viewModel.setDefaultApp(role.roleName, candidate.packageName) },
                    )
                }
            }
            item {
                SettingsActionRow(
                    title = "Opening links",
                    summary = "Choose which apps open supported web links",
                    onClick = onOpeningLinks,
                )
            }
        }
    }
}

@Composable
fun OpeningLinksRoute(
    viewModel: OpeningLinksViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    SettingsScaffold(title = "Opening links", onBack = onBack) {
        PermissionMessageHost(state.message, viewModel::clearMessage)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Installed apps that can open links") {} }
            if (state.links.isEmpty()) {
                item { LoadingOrEmpty(state.isWorking, "No verified link handlers found") }
            }
            items(state.links, key = { it.packageName }) { link ->
                SettingsSwitchRow(
                    title = link.label,
                    summary = link.domains.joinToString(", "),
                    checked = link.handlesLinks,
                    enabled = !state.isWorking,
                    busy = state.isWorking,
                    onCheckedChange = { viewModel.setEnabled(link.packageName, it) },
                )
            }
        }
    }
}

@Composable
private fun PermissionMessageHost(
    message: String?,
    onShown: () -> Unit,
) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            host.showSnackbar(it)
            onShown()
        }
    }
    SnackbarHost(hostState = host)
}

private fun com.android.car.settings.feature.applications.domain.PermissionGrantMode.displayLabel(): String =
    when (this) {
        com.android.car.settings.feature.applications.domain.PermissionGrantMode.ALLOW -> "Allowed"
        com.android.car.settings.feature.applications.domain.PermissionGrantMode.WHILE_IN_USE -> "While in use"
        com.android.car.settings.feature.applications.domain.PermissionGrantMode.ALWAYS -> "Always allowed"
        com.android.car.settings.feature.applications.domain.PermissionGrantMode.ASK_EVERY_TIME -> "Ask every time"
        com.android.car.settings.feature.applications.domain.PermissionGrantMode.DENY -> "Not allowed"
    }

@Composable
fun SpecialAppAccessRoute(
    onBack: () -> Unit,
    onSpecialAccessType: (SpecialAccessType) -> Unit,
) {
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
                    onClick = { onSpecialAccessType(SpecialAccessType.NOTIFICATION_ACCESS) },
                )
            }
            item {
                SettingsActionRow(
                    title = "Premium SMS",
                    summary = "Allow apps to send premium SMS messages",
                    onClick = { onSpecialAccessType(SpecialAccessType.PREMIUM_SMS) },
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
            SettingsAppBarAction(
                id = "special-access-refresh",
                contentDescription = "Refresh",
                onClick = viewModel::refresh,
            ) { Icon(Icons.Default.Refresh, contentDescription = null) }
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
            SettingsAppBarAction(
                id = "performance-apps-refresh",
                contentDescription = "Refresh",
                onClick = viewModel::refresh,
            ) { Icon(Icons.Default.Refresh, contentDescription = null) }
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
        navigates = true,
        title = application.label,
        summary =
            buildString {
                if (!application.isEnabled) append("Disabled")
                if (application.versionName.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(application.versionName)
                }
            }.ifEmpty { application.packageName },
        leading = { PackageAppIcon(packageName = application.packageName) },
        focusId = "application-${application.packageName}",
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
            SettingsFormButton(
                focusId = "application-primary-action",
                label = details.primaryAction.title(),
                enabled = !isWorking,
                onClick = onPrimary,
            )
        }
        SettingsFormButton(
            focusId = "application-force-stop",
            label = "Force stop",
            enabled = details.canForceStop && !isWorking,
            onClick = onForceStop,
        )
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
    AutomotiveAlertDialog(
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
        confirmAction = onConfirm,
        dismissAction = onDismiss,
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
