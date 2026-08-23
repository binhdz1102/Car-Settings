package com.android.car.settings.feature.sound.presentation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsAppBarAction
import com.android.car.settings.core.ui.SettingsFormButton
import com.android.car.settings.core.ui.SettingsFormSlider
import com.android.car.settings.core.ui.SettingsLeadingIcon
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.feature.sound.domain.InterruptionMode
import com.android.car.settings.feature.sound.domain.RingerMode
import com.android.car.settings.feature.sound.domain.RingtoneKind
import com.android.car.settings.feature.sound.domain.RingtoneOption
import com.android.car.settings.feature.sound.domain.SoundVolume
import kotlin.math.roundToInt
import com.android.car.settings.core.ui.BMaterialLazyColumn as LazyColumn

@Composable
fun SoundRoute(
    viewModel: SoundViewModel,
    onBack: () -> Unit,
    onRingtone: (RingtoneKind) -> Unit,
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.preloadRingtoneOptions() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    SoundScreen(
        state = state,
        listState = listState,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onVolume = viewModel::setVolume,
        onRingerMode = viewModel::setRingerMode,
        onVibrate = viewModel::setVibrateWhenRinging,
        onInterruption = viewModel::setInterruptionMode,
        onRingtone = onRingtone,
    )
}

@Composable
private fun SoundScreen(
    state: SoundUiState,
    listState: LazyListState = rememberLazyListState(),
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onVolume: (Int, Int) -> Unit,
    onRingerMode: (RingerMode) -> Unit,
    onVibrate: (Boolean) -> Unit,
    onInterruption: (InterruptionMode) -> Unit,
    onRingtone: (RingtoneKind) -> Unit,
) {
    val context = LocalContext.current
    SettingsScaffold(
        title = "Sound & vibration",
        destinationKey = "sound",
        isRoot = true,
        onBack = onBack,
        actions = {
            SettingsAppBarAction(
                id = "sound-refresh",
                contentDescription = "Refresh sound settings",
                onClick = onRefresh,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        SnackbarHost(hostState = snackbarHost)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            item(key = "sound-section-volume") { SettingsSection("Volume") {} }
            items(state.sound.volumes, key = { it.id }) { volume ->
                VolumeRow(volume = volume, onVolume = onVolume)
            }
            if (state.sound.isRingerModeSupported) {
                item(key = "sound-section-calls") { SettingsSection("Calls and notifications") {} }
                item(key = "sound-section-ringer-mode") { SettingsSection("Ringer mode") {} }
                RingerMode.entries.forEach { mode ->
                    item(key = "sound-ringer-mode-${mode.name}") {
                        RingerModeOptionRow(
                            mode = mode,
                            selected = state.sound.ringerMode,
                            onSelected = onRingerMode,
                        )
                    }
                }
                item(key = "sound-vibrate-calls") {
                    SettingsSwitchRow(
                        title = "Vibrate for calls",
                        summary = "Vibrate when a call is received",
                        checked = state.sound.vibrateWhenRinging,
                        enabled = !state.isWorking,
                        retainFocusWhenDisabled = state.isWorking,
                        onCheckedChange = onVibrate,
                    )
                }
            }
            item(key = "sound-section-notifications") { SettingsSection("Notifications") {} }
            item(key = "sound-do-not-disturb") {
                val dnd = state.sound.doNotDisturb
                SettingsActionRow(
                    title = "Do Not Disturb",
                    summary =
                        if (dnd.hasPolicyAccess) {
                            dnd.mode.readableName()
                        } else {
                            "Tap to grant access, then choose an interruption mode"
                        },
                    leading = {
                        SettingsLeadingIcon(Icons.Default.Notifications)
                    },
                    onClick = {
                        if (dnd.hasPolicyAccess) {
                            onInterruption(dnd.mode.next())
                        } else {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        }
                    },
                )
            }
            item(key = "sound-section-selection") { SettingsSection("Sound selection") {} }
            items(state.sound.ringtones, key = { "sound-ringtone-${it.kind}" }) { selection ->
                SettingsActionRow(
                    title = selection.kind.title(),
                    summary = selection.title,
                    leading = { SettingsLeadingIcon(Icons.Default.VolumeUp) },
                    trailing = { SettingsLeadingIcon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { onRingtone(selection.kind) },
                )
            }
        }
    }
}

@Composable
private fun VolumeRow(
    volume: SoundVolume,
    onVolume: (Int, Int) -> Unit,
) {
    var isDragging by remember(volume.id) { mutableStateOf(false) }
    var pendingValue by remember(volume.id) { mutableFloatStateOf(volume.current.toFloat()) }

    // CarAudio callbacks can arrive while a drag is in progress. Keep the thumb controlled by
    // local gesture state until release; otherwise a stale framework value snaps it backwards.
    LaunchedEffect(volume.current) {
        if (!isDragging) pendingValue = volume.current.toFloat()
    }
    val displayedValue = pendingValue.roundToInt()

    SettingsFormSlider(
        focusId = "sound-volume-${volume.id}",
        label = "${volume.label}: $displayedValue/${volume.maximum}" + if (volume.isMuted) " · Muted" else "",
        value = pendingValue,
        valueRange = volume.minimum.toFloat()..volume.maximum.toFloat(),
        steps = (volume.maximum - volume.minimum - 1).coerceAtLeast(0),
        onValueChange = {
            isDragging = true
            pendingValue = it
        },
        onValueChangeFinished = {
            isDragging = false
            if (displayedValue != volume.current) {
                onVolume(volume.id, displayedValue)
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun RingerModeOptionRow(
    mode: RingerMode,
    selected: RingerMode,
    onSelected: (RingerMode) -> Unit,
) {
    SettingsActionRow(
        title = mode.readableName(),
        summary = if (selected == mode) "Current" else null,
        focusId = "ringer-mode-${mode.name}",
        leading =
            if (selected == mode) {
                { SettingsLeadingIcon(Icons.Default.Check, contentDescription = "Current") }
            } else {
                null
            },
        onClick = { onSelected(mode) },
    )
}

@Composable
fun RingtonePickerRoute(
    kind: RingtoneKind,
    viewModel: SoundViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(kind) { viewModel.loadRingtoneOptions(kind) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val selected =
        state.sound.ringtones
            .firstOrNull { it.kind == kind }
            ?.uri
    SettingsScaffold(title = kind.title(), onBack = onBack) {
        SnackbarHost(hostState = snackbarHost)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                RingtoneOptionRow(
                    option = RingtoneOption("Silent", null),
                    selected = selected == null,
                    onPreview = viewModel::previewRingtone,
                    onSelect = { viewModel.setRingtone(kind, it) },
                )
            }
            items(state.ringtoneOptions[kind].orEmpty(), key = { it.uri.orEmpty() }) { option ->
                RingtoneOptionRow(
                    option = option,
                    selected = selected == option.uri,
                    onPreview = viewModel::previewRingtone,
                    onSelect = { viewModel.setRingtone(kind, it) },
                )
            }
            if (state.isWorking && state.ringtoneOptions[kind] == null) {
                item { CircularProgressIndicator(modifier = Modifier.padding(24.dp)) }
            }
        }
    }
}

@Composable
private fun RingtoneOptionRow(
    option: RingtoneOption,
    selected: Boolean,
    onPreview: (String?) -> Unit,
    onSelect: (String?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsActionRow(
            title = option.title,
            summary = if (selected) "Current" else null,
            focusId = "ringtone-select-${option.uri ?: "silent"}",
            onClick = { onSelect(option.uri) },
        )
        option.uri?.let { uri ->
            SettingsFormButton(
                focusId = "ringtone-preview-$uri",
                label = "Preview",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                onClick = { onPreview(uri) },
            )
        }
    }
}

private fun RingtoneKind.title(): String =
    when (this) {
        RingtoneKind.PHONE -> "Phone ringtone"
        RingtoneKind.NOTIFICATION -> "Notification sound"
        RingtoneKind.ALARM -> "Alarm sound"
    }

private fun RingerMode.readableName(): String = name.lowercase().replaceFirstChar(Char::titlecase)

private fun InterruptionMode.readableName(): String =
    when (this) {
        InterruptionMode.ALL -> "All interruptions allowed"
        InterruptionMode.PRIORITY -> "Priority only"
        InterruptionMode.ALARMS -> "Alarms only"
        InterruptionMode.NONE -> "Total silence"
    }

private fun InterruptionMode.next(): InterruptionMode =
    when (this) {
        InterruptionMode.ALL -> InterruptionMode.PRIORITY
        InterruptionMode.PRIORITY -> InterruptionMode.ALARMS
        InterruptionMode.ALARMS -> InterruptionMode.NONE
        InterruptionMode.NONE -> InterruptionMode.ALL
    }
