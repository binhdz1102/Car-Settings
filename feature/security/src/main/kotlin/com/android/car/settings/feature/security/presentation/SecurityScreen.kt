package com.android.car.settings.feature.security.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.AutomotiveAlertDialog
import com.android.car.settings.core.ui.AutomotiveButton
import com.android.car.settings.core.ui.AutomotiveTextButton
import com.android.car.settings.core.ui.AutomotiveTextField
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsFormButton
import com.android.car.settings.core.ui.SettingsFormTextField
import com.android.car.settings.core.ui.SettingsLazyFocusListSlot
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.settingsLazyFocusListSpec
import com.android.car.settings.feature.security.domain.DeviceAdminApp
import com.android.car.settings.feature.security.domain.SecurityLockType
import com.android.car.settings.core.ui.AutomotiveLazyColumn as LazyColumn

@Composable
fun SecurityRoute(
    viewModel: SecurityViewModel,
    onBack: () -> Unit,
    onLockTypes: () -> Unit,
    onDeviceAdmins: () -> Unit,
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var resetCredentials by remember { mutableStateOf(false) }
    val focusSpec =
        securityRootFocusSpec(
            canManageScreenLock = uiState.security.canManageScreenLock,
            isGuestUser = uiState.security.isGuestUser,
            isWorking = uiState.isWorking,
        )
    SettingsScaffold(
        title = "Security",
        destinationKey = "security",
        isRoot = true,
        onBack = onBack,
        firstContentFocusId = focusSpec.firstContentFocusId,
        lazyListState = listState,
        lazyFocusItemIndexById = focusSpec.itemIndexByFocusId,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            item(key = "security-screen-lock") {
                SettingsActionRow(
                    title = "Screen lock",
                    summary = uiState.security.screenLockUnavailableReason ?: uiState.security.lockType.displayName,
                    focusId = "security-screen-lock",
                    leading = { Icon(Icons.Default.Lock, contentDescription = null) },
                    enabled = uiState.security.canManageScreenLock && !uiState.security.isGuestUser,
                    onClick = onLockTypes,
                )
            }
            item(key = "security-clear-credentials") {
                SettingsActionRow(
                    title = "Clear credentials",
                    summary = "Remove user certificates and reset credential storage",
                    focusId = "security-clear-credentials",
                    leading = { Icon(Icons.Default.Key, contentDescription = null) },
                    enabled = uiState.security.canManageScreenLock && !uiState.security.isGuestUser && !uiState.isWorking,
                    onClick = { resetCredentials = true },
                )
            }
            item(key = "security-device-admin") {
                SettingsActionRow(
                    title = "Device admin apps",
                    summary =
                        if (uiState.security.deviceAdmins.isEmpty()) {
                            "No active device admin apps"
                        } else {
                            "${uiState.security.deviceAdmins.size} active"
                        },
                    focusId = "security-device-admin",
                    leading = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                    onClick = onDeviceAdmins,
                )
            }
        }
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

/** Keeps root Security handoff aligned with the physical rows emitted by its LazyColumn. */
internal fun securityRootFocusSpec(
    canManageScreenLock: Boolean,
    isGuestUser: Boolean,
    isWorking: Boolean,
) =
    settingsLazyFocusListSpec(
        listOf(
            SettingsLazyFocusListSlot(
                focusId = "security-screen-lock",
                isEnabled = canManageScreenLock && !isGuestUser,
            ),
            SettingsLazyFocusListSlot(
                focusId = "security-clear-credentials",
                isEnabled = canManageScreenLock && !isGuestUser && !isWorking,
            ),
            SettingsLazyFocusListSlot(focusId = "security-device-admin"),
        ),
    )

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
                    focusId = "security-lock-type-${type.name}",
                    enabled = uiState.security.canManageScreenLock && !uiState.security.isGuestUser,
                    onClick = { onType(type) },
                )
            }
        }
    }
}

@Composable
fun LockSetupRoute(
    type: SecurityLockType,
    viewModel: SecurityViewModel,
    onBack: () -> Unit,
) {
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
                SettingsFormTextField(
                    focusId = "security-current-credential",
                    value = current,
                    onValueChange = { current = it },
                    label = "Current ${uiState.security.lockType.displayName}",
                    visualTransformation =
                        if (uiState.security.lockType == SecurityLockType.PATTERN) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                )
            }
            if (type != SecurityLockType.NONE) {
                SettingsFormTextField(
                    focusId = "security-new-credential",
                    value = first,
                    onValueChange = { first = it },
                    label = "New ${type.displayName}",
                    visualTransformation =
                        if (isPattern) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                )
                SettingsFormTextField(
                    focusId = "security-confirm-credential",
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = "Confirm ${type.displayName}",
                    visualTransformation =
                        if (isPattern) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                )
                if (isPattern) {
                    Text("Pattern format: choose the numbered 3 × 3 cells in order, e.g. 1,2,5,8.")
                }
            } else {
                Text("Removing the screen lock requires the current screen lock, if one is set.")
            }
            SettingsFormButton(
                focusId = "security-save-lock",
                label = if (type == SecurityLockType.NONE) "Remove screen lock" else "Save screen lock",
                enabled =
                    !uiState.isWorking &&
                        uiState.security.canManageScreenLock &&
                        (type == SecurityLockType.NONE || (first.isNotBlank() && first == confirmation)),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.setLock(type, current, if (type == SecurityLockType.NONE) "" else first)
                    onBack()
                },
            )
        }
    }
}

@Composable
fun DeviceAdminsRoute(
    viewModel: SecurityViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var removeTarget by remember { mutableStateOf<DeviceAdminApp?>(null) }
    SettingsScaffold(title = "Device admin apps", onBack = onBack) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (uiState.security.deviceAdmins.isEmpty()) item { Text("No active device admin apps") }
            items(uiState.security.deviceAdmins, key = DeviceAdminApp::componentName) { admin ->
                SettingsActionRow(
                    title = admin.label,
                    summary = admin.packageName,
                    focusId = "security-admin-${admin.componentName}",
                    onClick = { removeTarget = admin },
                )
            }
        }
    }
    removeTarget?.let { admin ->
        AutomotiveAlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove ${admin.label} as device admin?") },
            text = { Text("This app will no longer be able to enforce device-administration policies.") },
            confirmButton = {
                AutomotiveButton(
                    label = "Remove",
                    onClick = {
                        viewModel.removeDeviceAdmin(admin.componentName)
                        removeTarget = null
                    },
                )
            },
            confirmAction = {
                viewModel.removeDeviceAdmin(admin.componentName)
                removeTarget = null
            },
            dismissAction = { removeTarget = null },
            dismissButton = {
                AutomotiveTextButton(label = "Cancel", onClick = { removeTarget = null })
            },
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
    AutomotiveAlertDialog(
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
        confirmButton = { AutomotiveButton(label = actionLabel, onClick = { onConfirm(current) }) },
        confirmAction = { onConfirm(current) },
        dismissAction = onDismiss,
        dismissButton = {
            AutomotiveTextButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

@Composable
private fun CredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPattern: Boolean,
) {
    AutomotiveTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = true,
        visualTransformation = if (isPattern) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}
