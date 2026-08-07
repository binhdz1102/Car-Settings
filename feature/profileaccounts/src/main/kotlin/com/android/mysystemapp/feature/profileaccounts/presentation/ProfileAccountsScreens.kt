package com.android.car.settings.feature.profileaccounts.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwitchAccount
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.KeyValueRow
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.feature.profileaccounts.domain.AccountProvider
import com.android.car.settings.feature.profileaccounts.domain.AccountSummary
import com.android.car.settings.feature.profileaccounts.domain.ProfileSummary
import com.android.car.settings.feature.profileaccounts.domain.SyncAuthority

@Composable
fun ProfileAccountsRoute(
    viewModel: ProfileAccountsViewModel,
    onBack: () -> Unit,
    onProfiles: () -> Unit,
    onProfile: (Int) -> Unit,
    onAddAccount: () -> Unit,
    onAccount: (String, String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    ProfileAccountsScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onProfiles = onProfiles,
        onProfile = onProfile,
        onAddAccount = onAddAccount,
        onAccount = onAccount,
        onSetMasterSync = viewModel::setMasterSyncEnabled,
        onMessageShown = viewModel::clearMessage,
    )
}

@Composable
private fun ProfileAccountsScreen(
    state: ProfileAccountsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onProfiles: () -> Unit,
    onProfile: (Int) -> Unit,
    onAddAccount: () -> Unit,
    onAccount: (String, String) -> Unit,
    onSetMasterSync: (Boolean) -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); onMessageShown() }
    }
    val settings = state.settings
    SettingsScaffold(
        title = "Profiles & accounts",
        onBack = onBack,
        actions = { RefreshAction(onRefresh) },
    ) {
        SnackbarHost(hostState = snackbar)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Profile") {} }
            settings.currentProfile?.let { profile ->
                item {
                    SettingsActionRow(
                        title = profile.name,
                        summary = profileDescription(profile),
                        leading = { Icon(Icons.Default.Person, null) },
                        onClick = { onProfile(profile.id) },
                    )
                }
            }
            item {
                SettingsActionRow(
                    title = "Manage other profiles",
                    summary = "${settings.profiles.size} profile${if (settings.profiles.size == 1) "" else "s"} available",
                    leading = { Icon(Icons.Default.SwitchAccount, null) },
                    onClick = onProfiles,
                )
            }
            item { SettingsSection("Accounts") {} }
            item {
                SettingsSwitchRow(
                    title = "Automatically sync app data",
                    summary = "Allow accounts to sync in the background",
                    checked = settings.masterSyncEnabled,
                    enabled = settings.canModifyAccounts && !state.isWorking,
                    busy = state.isWorking,
                    onCheckedChange = onSetMasterSync,
                )
            }
            if (settings.accounts.isEmpty()) {
                item {
                    EmptyRow("No accounts added")
                }
            } else {
                items(settings.accounts, key = { "${it.name}/${it.type}" }) { account ->
                    AccountRow(account, onClick = { onAccount(account.name, account.type) })
                }
            }
            item {
                SettingsActionRow(
                    title = "Add account",
                    summary = if (settings.canModifyAccounts) "Add an account provider" else "Account changes are unavailable for this profile",
                    enabled = settings.canModifyAccounts,
                    leading = { Icon(Icons.Default.Add, null) },
                    onClick = onAddAccount,
                )
            }
        }
    }
}

@Composable
fun ProfilesRoute(
    viewModel: ProfileAccountsViewModel,
    onBack: () -> Unit,
    onProfile: (Int) -> Unit,
    onAddProfile: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    SettingsScaffold(
        title = "Manage profiles",
        onBack = onBack,
        actions = { RefreshAction(viewModel::refresh) },
    ) {
        SnackbarHost(hostState = snackbar)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Profiles") {} }
            items(state.settings.profiles, key = { it.id }) { profile ->
                ProfileRow(profile, onClick = { onProfile(profile.id) })
            }
            item { SettingsSection("Add profile") {} }
            item {
                SettingsActionRow(
                    title = "Add profile",
                    summary = state.settings.profileRestriction ?: "Create another vehicle profile",
                    enabled = state.settings.canAddProfile && !state.isWorking,
                    leading = { Icon(Icons.Default.Add, null) },
                    onClick = onAddProfile,
                )
            }
        }
    }
}

@Composable
fun AddProfileRoute(viewModel: AddProfileViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    SettingsScaffold(title = "Add profile", onBack = onBack) {
        SnackbarHost(hostState = snackbar)
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Create a separate vehicle profile. A profile keeps accounts and app data separate.")
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                enabled = !state.isWorking && state.settings.canAddProfile,
                label = { Text("Profile name") },
                placeholder = { Text("New user") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.addProfile(name) },
                enabled = !state.isWorking && state.settings.canAddProfile,
            ) {
                if (state.isWorking) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                else Text("Add profile")
            }
            state.settings.profileRestriction?.let { Text(it) }
        }
    }
}

@Composable
fun ProfileDetailsRoute(viewModel: ProfileDetailsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var rename by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    if (rename) {
        RenameProfileDialog(
            initialName = state.details?.profile?.name.orEmpty(),
            onDismiss = { rename = false },
            onConfirm = { viewModel.rename(it); rename = false },
        )
    }
    if (remove) {
        ConfirmationDialog(
            title = "Remove profile?",
            text = "This removes the profile and its local data from the vehicle.",
            confirmLabel = "Remove",
            onDismiss = { remove = false },
            onConfirm = { viewModel.removeProfile(); remove = false },
        )
    }
    SettingsScaffold(
        title = state.details?.profile?.name ?: "Profile",
        onBack = onBack,
        actions = { RefreshAction(viewModel::refresh) },
    ) {
        SnackbarHost(hostState = snackbar)
        val details = state.details
        if (details == null) {
            LoadingOrEmpty(state.isWorking, "Profile is unavailable")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { SettingsSection("Profile details") {} }
                item { KeyValueRow("Name", details.profile.name) }
                item { KeyValueRow("Type", profileDescription(details.profile)) }
                item { KeyValueRow("Status", if (details.profile.isRunning) "Running" else "Not running") }
                if (details.canRename) {
                    item {
                        SettingsActionRow(
                            title = "Rename profile",
                            leading = { Icon(Icons.Default.Edit, null) },
                            enabled = !state.isWorking,
                            onClick = { rename = true },
                        )
                    }
                }
                if (details.canSwitch) {
                    item {
                        SettingsActionRow(
                            title = "Switch to this profile",
                            summary = "Change the active vehicle user",
                            leading = { Icon(Icons.Default.SwitchAccount, null) },
                            enabled = !state.isWorking,
                            onClick = viewModel::switchProfile,
                        )
                    }
                }
                if (details.canRemove) {
                    item { SettingsSection("Danger zone") {} }
                    item {
                        SettingsActionRow(
                            title = "Remove profile",
                            summary = "Delete this profile from the vehicle",
                            leading = { Icon(Icons.Default.Delete, null) },
                            enabled = !state.isWorking,
                            onClick = { remove = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddAccountRoute(viewModel: AddAccountViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    SettingsScaffold(title = "Add account", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        SnackbarHost(hostState = snackbar)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Choose account provider") {} }
            if (state.settings.accountProviders.isEmpty()) {
                item { EmptyRow("No account providers are installed") }
            } else {
                items(state.settings.accountProviders, key = { it.type }) { provider ->
                    AccountProviderRow(provider, !state.isWorking, viewModel::addAccount)
                }
            }
        }
    }
}

@Composable
fun AccountDetailsRoute(viewModel: AccountDetailsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    val snackbar = remember { SnackbarHostState() }
    var remove by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    if (remove) {
        ConfirmationDialog(
            title = "Remove account?",
            text = "This removes the account from the current profile. The provider may ask for confirmation.",
            confirmLabel = "Remove",
            onDismiss = { remove = false },
            onConfirm = { viewModel.removeAccount(); remove = false },
        )
    }
    SettingsScaffold(
        title = state.details?.account?.name ?: "Account",
        onBack = onBack,
        actions = { RefreshAction(viewModel::refresh) },
    ) {
        SnackbarHost(hostState = snackbar)
        val details = state.details
        if (details == null) {
            LoadingOrEmpty(state.isWorking, "Account is unavailable")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { SettingsSection("Account") {} }
                item { KeyValueRow("Provider", details.account.providerLabel) }
                item { KeyValueRow("Type", details.account.type) }
                item { SettingsSection("Sync") {} }
                if (details.syncAuthorities.isEmpty()) {
                    item { EmptyRow("This account has no visible sync items") }
                } else {
                    items(details.syncAuthorities, key = { it.authority }) { authority ->
                        SyncAuthorityRow(
                            authority = authority,
                            canModify = details.canModify && !state.isWorking,
                            onSyncEnabled = viewModel::setSyncEnabled,
                            onSyncNow = viewModel::requestSync,
                        )
                    }
                }
                if (details.canModify) {
                    item { SettingsSection("Account actions") {} }
                    item {
                        SettingsActionRow(
                            title = "Remove account",
                            summary = "Remove it from this profile",
                            leading = { Icon(Icons.Default.Delete, null) },
                            enabled = !state.isWorking,
                            onClick = { remove = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: ProfileSummary, onClick: () -> Unit) = SettingsActionRow(
    title = profile.name,
    summary = profileDescription(profile),
    leading = { Icon(Icons.Default.Person, null) },
    onClick = onClick,
)

@Composable
private fun AccountRow(account: AccountSummary, onClick: () -> Unit) = SettingsActionRow(
    title = account.name,
    summary = account.providerLabel,
    leading = { Icon(Icons.Default.AccountCircle, null) },
    onClick = onClick,
)

@Composable
private fun AccountProviderRow(provider: AccountProvider, enabled: Boolean, onClick: (String) -> Unit) = SettingsActionRow(
    title = provider.label,
    summary = provider.type,
    leading = { Icon(Icons.Default.AccountCircle, null) },
    enabled = enabled,
    onClick = { onClick(provider.type) },
)

@Composable
private fun SyncAuthorityRow(
    authority: SyncAuthority,
    canModify: Boolean,
    onSyncEnabled: (String, Boolean) -> Unit,
    onSyncNow: (String) -> Unit,
) = SettingsSwitchRow(
    title = authority.label,
    summary = authority.summary,
    checked = authority.enabled,
    enabled = canModify,
    onCheckedChange = { onSyncEnabled(authority.authority, it) },
)

@Composable
private fun RenameProfileDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename profile") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile name") },
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { onConfirm(name) }, enabled = name.trim().isNotEmpty()) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
    dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
)

@Composable
private fun RefreshAction(onRefresh: () -> Unit) {
    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") }
}

@Composable
private fun EmptyRow(text: String) {
    Text(text, modifier = Modifier.padding(24.dp))
}

@Composable
private fun LoadingOrEmpty(loading: Boolean, emptyText: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) CircularProgressIndicator()
        Text(if (loading) "Loading…" else emptyText)
    }
}

@Composable
private fun RefreshOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) onResume() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun profileDescription(profile: ProfileSummary): String {
    val labels = mutableListOf<String>()
    if (profile.isCurrent) labels += "Current profile"
    if (profile.isAdmin) labels += "Administrator"
    if (profile.isGuest) labels += "Guest"
    if (profile.isDemo) labels += "Demo"
    if (labels.isEmpty()) labels += "Profile"
    return labels.joinToString(" · ")
}

private fun add(value: String): List<String> = listOf(value)

@Suppress("unused")
private fun legacyProfileDescription(profile: ProfileSummary): String = buildList {
    if (profile.isCurrent) add("Current profile")
    if (profile.isAdmin) add("Administrator")
    if (profile.isGuest) add("Guest")
    if (profile.isDemo) add("Demo")
}.ifEmpty { add("Profile") }.joinToString(" · ")
