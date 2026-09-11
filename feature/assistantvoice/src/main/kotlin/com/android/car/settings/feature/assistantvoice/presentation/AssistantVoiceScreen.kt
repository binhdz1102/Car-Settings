package com.android.car.settings.feature.assistantvoice.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.AutomotiveAlertDialog
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.feature.assistantvoice.domain.VoiceInputOption
import com.android.car.settings.core.ui.AutomotiveLazyColumn as LazyColumn
import com.android.car.settings.core.ui.AutomotiveTextButton as TextButton

@Composable
fun AssistantVoiceRoute(
    viewModel: AssistantVoiceViewModel,
    onBack: () -> Unit,
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    var voiceDialogVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    SettingsScaffold(title = "Assistant & Voice", destinationKey = "assistant-voice", isRoot = true, onBack = onBack) {
        SnackbarHost(snackbarHost)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            item { SettingsSection("Assistant") {} }
            item {
                SettingsActionRow(
                    title = "Default assistant app",
                    summary = state.assistantVoice.assistantLabel,
                    leading = { Icon(Icons.Default.RecordVoiceOver, null) },
                    onClick = { context.openAssistantRolePicker() },
                )
            }
            state.assistantVoice.assistantSettingsActivity?.let { settingsActivity ->
                item {
                    SettingsActionRow(
                        title = "Assistant settings",
                        summary = settingsActivity,
                        onClick = { context.openComponent(settingsActivity) },
                    )
                }
            }
            item { SettingsSection("Context") {} }
            item {
                SettingsSwitchRow(
                    title = "Use text from screen",
                    summary = "Allow the assistant to read text and content from the current screen",
                    checked = state.assistantVoice.textFromScreenEnabled,
                    enabled = !state.isWorking,
                    busy = state.isWorking,
                    onCheckedChange = viewModel::setTextFromScreenEnabled,
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Use screenshot",
                    summary = "Allow the assistant to use a screenshot of the current screen",
                    checked = state.assistantVoice.screenshotEnabled,
                    enabled = state.assistantVoice.textFromScreenEnabled && !state.isWorking,
                    busy = state.isWorking,
                    onCheckedChange = viewModel::setScreenshotEnabled,
                )
            }
            item { SettingsSection("Voice input") {} }
            item {
                SettingsActionRow(
                    title = "Default voice input",
                    summary = state.assistantVoice.voiceInput?.label ?: "None available",
                    onClick = { voiceDialogVisible = true },
                )
            }
            if (state.assistantVoice.voiceInputs.isEmpty()) {
                item { Text("No voice input service is installed") }
            }
            state.assistantVoice.lastError?.let { error -> item { Text(error) } }
        }
    }

    if (voiceDialogVisible) {
        VoiceInputDialog(
            options = state.assistantVoice.voiceInputs,
            selected = state.assistantVoice.voiceInput,
            onSelect = {
                voiceDialogVisible = false
                viewModel.setDefaultVoiceInput(it)
            },
            onDismiss = { voiceDialogVisible = false },
        )
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
private fun VoiceInputDialog(
    options: List<VoiceInputOption>,
    selected: VoiceInputOption?,
    onSelect: (VoiceInputOption) -> Unit,
    onDismiss: () -> Unit,
) {
    AutomotiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default voice input") },
        text = {
            LazyColumn {
                items(options, key = { "${it.kind}:${it.componentName}" }) { option ->
                    SettingsActionRow(
                        title = option.label,
                        focusId = "assistant-voice-${option.componentName}",
                        summary =
                            if (option.kind.name == "INTERACTION") {
                                "Voice interaction service"
                            } else {
                                "Speech recognition service"
                            },
                        leading = {
                            RadioButton(
                                selected = option.componentName == selected?.componentName,
                                onClick = { onSelect(option) },
                            )
                        },
                        onClick = { onSelect(option) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        dismissAction = onDismiss,
    )
}

private fun Context.openAssistantRolePicker() {
    runCatching {
        startActivity(
            Intent("android.intent.action.MANAGE_DEFAULT_APP")
                .putExtra("android.intent.extra.ROLE_NAME", "android.app.role.ASSISTANT")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun Context.openComponent(flattenedComponent: String) {
    runCatching {
        val component =
            android.content.ComponentName.unflattenFromString(flattenedComponent)
                ?: return@runCatching
        startActivity(
            Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
