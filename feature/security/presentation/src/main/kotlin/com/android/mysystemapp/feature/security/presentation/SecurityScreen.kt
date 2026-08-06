package com.android.car.settings.feature.security.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.feature.security.domain.DeviceAdminApp
import com.android.car.settings.feature.security.domain.SecurityLockType

@Composable
fun SecurityRoute(
    viewModel: SecurityViewModel,
    onBack: () -> Unit,
    onLockTypes: () -> Unit,
    onDeviceAdmins: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var resetCredentials by remember { mutableStateOf(false) }
    SettingsScaffold(title = "Security", onBack = onBack) {
        SettingsActionRow(
            title = "Screen lock",
            summary = uiState.security.screenLockUnavailableReason ?: uiState.security.lockType.displayName,
            enabled = uiState.security.canManageScreenLock && !uiState.security.isGuestUser,
            onClick = onLockTypes,
        )
        SettingsActionRow(
            title = "Clear credentials",
            summary = "Remove user certificates and reset credential storage",
            enabled = uiState.security.canManageScreenLock && !uiState.security.isGuestUser && !uiState.isWorking,
            onClick = { resetCredentials = true },
        )
        SettingsActionRow(
            title = "Device admin apps",
            summary = if (uiState.security.deviceAdmins.isEmpty()) "No active device admin apps" else "${uiState.security.deviceAdmins.size} active",
            onClick = onDeviceAdmins,
        )
    }
    if (resetCredentials) {
        CredentialDialog(
            title = "Clear credentials?",
            currentType = uiState.security.lockType,
            actionLabel = "Clear",
            message = "User certificates and credential storage will be removed.",
            onDismiss = { resetCredentials = false },
            onConfirm = {
                viewModel.resetCredentials(it)
                resetCredentials = false
            },
        )
    }
}

@Composable
fun LockTypesRoute(
    viewModel: SecurityViewModel,
    onBack: () -> Unit,
    onType: (SecurityLockType) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScaffold(title = "Choose screen lock", onBack = onBack) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(listOf(SecurityLockType.NONE, SecurityLockType.PATTERN, SecurityLockType.PIN, SecurityLockType.PASSWORD)) { type ->
                SettingsActionRow(
                    title = type.displayName,
                    summary = if (type == uiState.security.lockType) "Current screen lock" else null,
                    enabled = uiState.security.canManageScreenLock && !uiState.security.isGuestUser,
                    onClick = { onType(type) },
                )
            }
        }
    }
}

@Composable
fun LockSetupRoute(type: SecurityLockType, viewModel: SecurityViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var current by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val isPattern = type == SecurityLockType.PATTERN
    val currentIsSecure = uiState.security.lockType != SecurityLockType.NONE && uiState.security.lockType != SecurityLockType.UNKNOWN
    SettingsScaffold(title = "Set ${type.displayName}", onBack = onBack) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (currentIsSecure) {
                CredentialField(
                    value = current,
                    onValueChange = { current = it },
                    label = "Current ${uiState.security.lockType.displayName}",
                    isPattern = uiState.security.lockType == SecurityLockType.PATTERN,
                )
            }
            if (type != SecurityLockType.NONE) {
                CredentialField(
                    value = first,
                    onValueChange = { first = it },
                    label = "New ${type.displayName}",
                    isPattern = isPattern,
                )
                CredentialField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = "Confirm ${type.displayName}",
                    isPattern = isPattern,
                )
                if (isPattern) {
                    Text("Pattern format: choose the numbered 3 × 3 cells in order, e.g. 1,2,5,8.")
                }
            } else {
                Text("Removing the screen lock requires the current screen lock, if one is set.")
            }
            Button(
                enabled =
                    !uiState.isWorking &&
                        uiState.security.canManageScreenLock &&
                            (type == SecurityLockType.NONE || (first.isNotBlank() && first == confirmation)),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.setLock(type, current, if (type == SecurityLockType.NONE) "" else first)
                    onBack()
                },
            ) {
                Text(if (type == SecurityLockType.NONE) "Remove screen lock" else "Save screen lock")
            }
        }
    }
}

@Composable
fun DeviceAdminsRoute(viewModel: SecurityViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var removeTarget by remember { mutableStateOf<DeviceAdminApp?>(null) }
    SettingsScaffold(title = "Device admin apps", onBack = onBack) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (uiState.security.deviceAdmins.isEmpty()) item { Text("No active device admin apps") }
            items(uiState.security.deviceAdmins, key = DeviceAdminApp::componentName) { admin ->
                SettingsActionRow(
                    title = admin.label,
                    summary = admin.packageName,
                    onClick = { removeTarget = admin },
                )
            }
        }
    }
    removeTarget?.let { admin ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove ${admin.label} as device admin?") },
            text = { Text("This app will no longer be able to enforce device-administration policies.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.removeDeviceAdmin(admin.componentName)
                    removeTarget = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CredentialDialog(
    title: String,
    currentType: SecurityLockType,
    actionLabel: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(message)
                if (currentType != SecurityLockType.NONE) {
                    CredentialField(
                        value = current,
                        onValueChange = { current = it },
                        label = "Current ${currentType.displayName}",
                        isPattern = currentType == SecurityLockType.PATTERN,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(current) }) { Text(actionLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPattern: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPattern) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}
