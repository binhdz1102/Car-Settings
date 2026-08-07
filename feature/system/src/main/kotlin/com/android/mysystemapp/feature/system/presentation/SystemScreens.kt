package com.android.car.settings.feature.system.presentation

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.android.car.settings.core.ui.KeyValueRow
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.feature.system.domain.NetworkSubscription
import com.android.car.settings.feature.system.domain.AutofillServiceOption
import com.android.car.settings.feature.system.domain.SystemExternalAction
import com.android.car.settings.feature.system.domain.SystemExternalActionId

@Composable
fun SystemRoute(
    viewModel: SystemViewModel,
    onBack: () -> Unit,
    onAbout: () -> Unit,
    onLegal: () -> Unit,
    onResetOptions: () -> Unit,
    onDateTime: () -> Unit,
    onLanguageInput: () -> Unit,
    onUnits: () -> Unit,
    onStorage: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    SystemHomeScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onAbout = onAbout,
        onLegal = onLegal,
        onResetOptions = onResetOptions,
        onDateTime = onDateTime,
        onLanguageInput = onLanguageInput,
        onUnits = onUnits,
        onStorage = onStorage,
        onExternal = viewModel::launchExternal,
        onMessageShown = viewModel::clearMessage,
    )
}

@Composable
private fun SystemHomeScreen(
    state: SystemUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAbout: () -> Unit,
    onLegal: () -> Unit,
    onResetOptions: () -> Unit,
    onDateTime: () -> Unit,
    onLanguageInput: () -> Unit,
    onUnits: () -> Unit,
    onStorage: () -> Unit,
    onExternal: (SystemExternalActionId) -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = messageSnackbar(state.message, onMessageShown)
    SettingsScaffold(title = "System", onBack = onBack, actions = { RefreshAction(onRefresh) }) {
        SnackbarHost(hostState = snackbar)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("System") {} }
            state.settings.systemUpdate?.let { action ->
                item { ExternalActionRow(action, !state.isWorking, onExternal) }
            }
            item {
                SettingsActionRow(
                    title = "Languages & input",
                    summary = state.settings.languageInput.currentLocaleName,
                    leading = { Icon(Icons.Default.Language, null) },
                    onClick = onLanguageInput,
                )
            }
            item {
                SettingsActionRow(
                    title = "Units",
                    summary = if (state.settings.units.settings.isEmpty()) {
                        "Vehicle display units"
                    } else {
                        "Speed, distance, temperature and other vehicle units"
                    },
                    leading = { Icon(Icons.Default.Settings, null) },
                    onClick = onUnits,
                )
            }
            item {
                SettingsActionRow(
                    title = "Date & time",
                    summary = "Set time, time zone and 24-hour format",
                    leading = { Icon(Icons.Default.DateRange, null) },
                    onClick = onDateTime,
                )
            }
            item {
                SettingsActionRow(
                    title = "Storage",
                    summary = "${state.settings.storage.usedBytes.fileSize()} used of ${state.settings.storage.totalBytes.fileSize()}",
                    leading = { Icon(Icons.Default.Storage, null) },
                    onClick = onStorage,
                )
            }
            item { SettingsSection("About") {} }
            item {
                SettingsActionRow(
                    title = "About",
                    summary = state.settings.about.hardware.model,
                    leading = { Icon(Icons.Default.Info, null) },
                    onClick = onAbout,
                )
            }
            item {
                SettingsActionRow(
                    title = "Legal information",
                    summary = "Terms, licenses and regulatory labels",
                    leading = { Icon(Icons.Default.Gavel, null) },
                    onClick = onLegal,
                )
            }
            item { SettingsSection("Reset") {} }
            item {
                SettingsActionRow(
                    title = "Reset options",
                    summary = "Restart, network reset, app preferences and factory reset",
                    leading = { Icon(Icons.Default.RestartAlt, null) },
                    onClick = onResetOptions,
                )
            }
            state.settings.developerOptions?.let { action ->
                item { SettingsSection("Advanced") {} }
                item { ExternalActionRow(action, !state.isWorking, onExternal) }
            }
            if (state.settings.systemExtras.isNotEmpty()) {
                item { SettingsSection("Additional system settings") {} }
                items(state.settings.systemExtras, key = { "${it.packageName}/${it.className}" }) { action ->
                    ExternalActionRow(action, !state.isWorking, onExternal)
                }
            }
        }
    }
}

@Composable
fun AboutRoute(
    viewModel: SystemViewModel,
    onBack: () -> Unit,
    onHardware: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    val snackbar = messageSnackbar(state.message, viewModel::clearMessage)
    SettingsScaffold(title = "About", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        SnackbarHost(hostState = snackbar)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Device information") {} }
            item {
                SettingsActionRow(
                    title = "Hardware info",
                    summary = state.settings.about.hardware.model,
                    leading = { Icon(Icons.Default.Info, null) },
                    onClick = onHardware,
                )
            }
            item { KeyValueRow("Firmware version", state.settings.about.firmwareVersion) }
            item { KeyValueRow("Security patch", state.settings.about.securityPatch) }
            item { KeyValueRow("Kernel version", state.settings.about.kernelVersion) }
            item {
                SettingsActionRow(
                    title = "Build number",
                    summary = state.settings.about.buildNumber,
                    leading = { Icon(Icons.Default.Build, null) },
                    enabled = !state.isWorking,
                    onClick = viewModel::tapBuildNumber,
                )
            }
            state.settings.developerTapsRemaining.takeIf { it in 1 until 7 }?.let { remaining ->
                item { HintRow("Tap build number $remaining more time${if (remaining == 1) "" else "s"} to enable developer options") }
            }
            if (state.settings.developerOptionsEnabled) {
                item { HintRow("Developer options are enabled") }
            }
            state.settings.about.bluetoothMacAddress?.let { address ->
                item { KeyValueRow("Bluetooth MAC address", address) }
            }
            state.settings.about.regulatoryInfo?.let { action ->
                item { SettingsSection("Regulatory") {} }
                item { ExternalActionRow(action, !state.isWorking, viewModel::launchExternal) }
            }
        }
    }
}

@Composable
fun HardwareInfoRoute(viewModel: SystemViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    SettingsScaffold(title = "Hardware info", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Hardware") {} }
            item { KeyValueRow("Model", state.settings.about.hardware.model) }
            item { KeyValueRow("Serial number", state.settings.about.hardware.serialNumber) }
            item { KeyValueRow("Hardware revision", state.settings.about.hardware.revision.ifBlank { "Unavailable" }) }
        }
    }
}

@Composable
fun LanguageInputRoute(
    viewModel: SystemViewModel,
    onBack: () -> Unit,
    onLanguage: () -> Unit,
    onAutofill: () -> Unit,
    onKeyboard: () -> Unit,
    onTextToSpeech: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    val snackbar = messageSnackbar(state.message, viewModel::clearMessage)
    SettingsScaffold(title = "Languages & input", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        SnackbarHost(hostState = snackbar)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("System language") {} }
            item {
                SettingsActionRow(
                    title = "System language",
                    summary = state.settings.languageInput.currentLocaleName,
                    leading = { Icon(Icons.Default.Language, null) },
                    enabled = state.settings.languageInput.canConfigureLocale,
                    onClick = onLanguage,
                )
            }
            if (!state.settings.languageInput.canConfigureLocale) {
                item { HintRow("Changing the system language is restricted for this profile") }
            }
            state.settings.languageInput.autofill.takeIf { it.supported }?.let { autofill ->
                item { SettingsSection("Autofill") {} }
                item {
                    SettingsActionRow(
                        title = "Autofill service",
                        summary = autofill.currentService?.label ?: "None",
                        leading = { Icon(Icons.Default.Security, null) },
                        onClick = onAutofill,
                    )
                }
            }
            item { SettingsSection("Keyboard") {} }
            item {
                SettingsActionRow(
                    title = "Keyboard",
                    summary = state.settings.languageInput.keyboards.count { it.enabled }
                        .let { if (it == 0) "No enabled keyboards" else "$it enabled" },
                    leading = { Icon(Icons.Default.Language, null) },
                    onClick = onKeyboard,
                )
            }
            item { SettingsSection("Text to speech") {} }
            item {
                SettingsActionRow(
                    title = "Text-to-speech output",
                    summary = state.settings.languageInput.textToSpeech.currentEngine?.label
                        ?: "No engine installed",
                    leading = { Icon(Icons.Default.Settings, null) },
                    onClick = onTextToSpeech,
                )
            }
        }
    }
}

@Composable
fun SystemLanguageRoute(viewModel: SystemViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    var query by remember { mutableStateOf("") }
    var pendingLanguageTag by remember { mutableStateOf<String?>(null) }
    val pendingLanguage = state.settings.languageInput.availableLocales
        .firstOrNull { it.languageTag == pendingLanguageTag }
    val snackbar = messageSnackbar(state.message, viewModel::clearMessage)
    if (pendingLanguage != null) {
        ConfirmationDialog(
            title = "Change system language?",
            text = "Switch the vehicle language to ${pendingLanguage.name}?",
            confirmLabel = "Change language",
            onDismiss = { pendingLanguageTag = null },
            onConfirm = {
                viewModel.setSystemLocale(pendingLanguage.languageTag)
                pendingLanguageTag = null
            },
        )
    }
    val visibleLocales = remember(state.settings.languageInput.availableLocales, query) {
        state.settings.languageInput.availableLocales.filter { locale ->
            query.isBlank() ||
                locale.name.contains(query, ignoreCase = true) ||
                locale.languageTag.contains(query, ignoreCase = true)
        }
    }
    SettingsScaffold(title = "System language", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        SnackbarHost(hostState = snackbar)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Current language") {} }
            item { KeyValueRow("Current language", state.settings.languageInput.currentLocaleName) }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    label = { Text("Search languages") },
                    singleLine = true,
                )
            }
            items(visibleLocales, key = { it.languageTag }) { locale ->
                SettingsActionRow(
                    title = locale.name,
                    summary = locale.languageTag,
                    leading = { Icon(Icons.Default.Language, null) },
                    enabled = !state.isWorking,
                    onClick = { pendingLanguageTag = locale.languageTag },
                )
            }
        }
    }
}

@Composable
fun AutofillRoute(viewModel: SystemViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    val autofill = state.settings.languageInput.autofill
    var pending by remember { mutableStateOf<AutofillServiceOption?>(null) }
    var clearPending by remember { mutableStateOf(false) }
    val snackbar = messageSnackbar(state.message, viewModel::clearMessage)
    if (pending != null || clearPending) {
        val label = pending?.label ?: "None"
        ConfirmationDialog(
            title = "Change autofill service?",
            text = if (pending == null) {
                "Turn off the current autofill service?"
            } else {
                "$label will be able to save and fill information in supported forms."
            },
            confirmLabel = "Use $label",
            onDismiss = { pending = null; clearPending = false },
            onConfirm = {
                viewModel.setAutofillService(pending?.componentName)
                pending = null
                clearPending = false
            },
        )
    }
    SettingsScaffold(title = "Autofill service", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        SnackbarHost(hostState = snackbar)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Choose an autofill service") {} }
            item {
                SettingsActionRow(
                    title = "None",
                    summary = if (autofill.currentService == null) "Current selection" else "Do not autofill forms",
                    leading = { Icon(Icons.Default.Security, null) },
                    enabled = !state.isWorking,
                    onClick = { clearPending = true },
                )
            }
            items(autofill.candidates, key = { it.componentName }) { candidate ->
                SettingsActionRow(
                    title = candidate.label,
                    summary = if (candidate.componentName == autofill.currentService?.componentName) "Current selection" else candidate.componentName,
                    leading = { Icon(Icons.Default.Security, null) },
                    enabled = !state.isWorking,
                    onClick = { pending = candidate },
                )
            }
            if (autofill.candidates.isEmpty()) {
                item { HintRow("No installed autofill service is available for this profile.") }
            }
        }
    }
}

@Composable
fun KeyboardRoute(viewModel: SystemViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    var pendingKeyboardId by remember { mutableStateOf<String?>(null) }
    val pendingKeyboard = state.settings.languageInput.keyboards.firstOrNull { it.id == pendingKeyboardId }
    val snackbar = messageSnackbar(state.message, viewModel::clearMessage)
    if (pendingKeyboard != null) {
        ConfirmationDialog(
            title = "Enable ${pendingKeyboard.label}?",
            text = "This keyboard may collect everything you type, including passwords and personal information.",
            confirmLabel = "Enable",
            onDismiss = { pendingKeyboardId = null },
            onConfirm = {
                viewModel.setKeyboardEnabled(pendingKeyboard.id, true)
                pendingKeyboardId = null
            },
        )
    }
    SettingsScaffold(title = "Keyboard", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        SnackbarHost(hostState = snackbar)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Enabled keyboards") {} }
            if (state.settings.languageInput.keyboards.isEmpty()) {
                item { HintRow("No input methods are installed for this profile.") }
            } else {
                items(state.settings.languageInput.keyboards, key = { it.id }) { keyboard ->
                    SettingsSwitchRow(
                        title = keyboard.label,
                        summary = keyboard.summary.ifBlank {
                            if (keyboard.enabled) "Enabled keyboard" else "Disabled keyboard"
                        },
                        checked = keyboard.enabled,
                        enabled = !state.isWorking && (!keyboard.enabled || keyboard.canDisable),
                        onCheckedChange = { shouldEnable ->
                            if (shouldEnable && !keyboard.isSystem) {
                                pendingKeyboardId = keyboard.id
                            } else {
                                viewModel.setKeyboardEnabled(keyboard.id, shouldEnable)
                            }
                        },
                    )
                }
            }
            item { HintRow("At least one default keyboard remains enabled, matching AAOS Settings safeguards.") }
        }
    }
}

@Composable
fun TextToSpeechRoute(viewModel: SystemViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    val tts = state.settings.languageInput.textToSpeech
    var chooseEngine by remember { mutableStateOf(false) }
    var localRate by remember(tts.speechRate) { mutableStateOf(tts.speechRate.toFloat()) }
    var localPitch by remember(tts.pitch) { mutableStateOf(tts.pitch.toFloat()) }
    val snackbar = messageSnackbar(state.message, viewModel::clearMessage)
    if (chooseEngine) {
        AlertDialog(
            onDismissRequest = { chooseEngine = false },
            title = { Text("Preferred engine") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tts.engines.forEach { engine ->
                        OutlinedButton(
                            onClick = { viewModel.setTextToSpeechEngine(engine.packageName); chooseEngine = false },
                            enabled = !state.isWorking,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (engine.packageName == tts.currentEngine?.packageName) "${engine.label} (current)" else engine.label)
                        }
                    }
                }
            },
            confirmButton = { OutlinedButton(onClick = { chooseEngine = false }) { Text("Cancel") } },
        )
    }
    SettingsScaffold(title = "Text-to-speech output", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        SnackbarHost(hostState = snackbar)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Engine") {} }
            item {
                SettingsActionRow(
                    title = "Preferred engine",
                    summary = tts.currentEngine?.label ?: "No engine installed",
                    leading = { Icon(Icons.Default.Settings, null) },
                    enabled = tts.engines.isNotEmpty() && !state.isWorking,
                    onClick = { chooseEngine = true },
                )
            }
            item { SettingsSection("Playback") {} }
            item {
                SliderSetting(
                    title = "Speech rate",
                    value = localRate,
                    range = 10f..600f,
                    enabled = tts.currentEngine != null && !state.isWorking,
                    onValueChange = { localRate = it },
                    onValueChangeFinished = {
                        viewModel.setTextToSpeechPlayback(localRate.toInt(), localPitch.toInt())
                    },
                )
            }
            item {
                SliderSetting(
                    title = "Voice pitch",
                    value = localPitch,
                    range = 25f..400f,
                    enabled = tts.currentEngine != null && !state.isWorking,
                    onValueChange = { localPitch = it },
                    onValueChangeFinished = {
                        viewModel.setTextToSpeechPlayback(localRate.toInt(), localPitch.toInt())
                    },
                )
            }
            item {
                Button(
                    onClick = viewModel::speakTextToSpeechSample,
                    enabled = tts.currentEngine != null && !state.isWorking,
                    modifier = Modifier.padding(24.dp),
                ) { Text("Play sample") }
            }
            item {
                OutlinedButton(
                    onClick = { viewModel.setTextToSpeechPlayback(100, 100) },
                    enabled = tts.currentEngine != null && !state.isWorking,
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) { Text("Reset speech rate and pitch") }
            }
        }
    }
}

@Composable
fun UnitsRoute(viewModel: SystemViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    var selectedPropertyId by remember { mutableStateOf<Int?>(null) }
    val selected = state.settings.units.settings.firstOrNull { it.propertyId == selectedPropertyId }
    val snackbar = messageSnackbar(state.message, viewModel::clearMessage)
    if (selected != null) {
        AlertDialog(
            onDismissRequest = { selectedPropertyId = null },
            title = { Text(selected.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selected.supported.forEach { option ->
                        OutlinedButton(
                            onClick = {
                                viewModel.setVehicleUnit(selected.propertyId, option.id)
                                selectedPropertyId = null
                            },
                            enabled = !state.isWorking,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (option.id == selected.current.id) "${option.label} (current)" else option.label)
                        }
                    }
                }
            },
            confirmButton = { OutlinedButton(onClick = { selectedPropertyId = null }) { Text("Cancel") } },
        )
    }
    SettingsScaffold(title = "Units", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        SnackbarHost(hostState = snackbar)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Vehicle units") {} }
            if (state.settings.units.settings.isEmpty()) {
                item { HintRow("This vehicle does not expose configurable display units.") }
            } else {
                items(state.settings.units.settings, key = { it.propertyId }) { setting ->
                    SettingsActionRow(
                        title = setting.title,
                        summary = setting.current.label,
                        leading = { Icon(Icons.Default.Settings, null) },
                        enabled = !state.isWorking,
                        onClick = { selectedPropertyId = setting.propertyId },
                    )
                }
            }
        }
    }
}

@Composable
fun StorageRoute(viewModel: SystemViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    val storage = state.settings.storage
    SettingsScaffold(title = "Storage", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Internal storage") {} }
            item { KeyValueRow("Used", storage.usedBytes.fileSize()) }
            item { KeyValueRow("Available", storage.freeBytes.fileSize()) }
            item { KeyValueRow("Total", storage.totalBytes.fileSize()) }
            item { SettingsSection("Storage usage") {} }
            if (storage.categories.isEmpty()) {
                item { HintRow("Calculating storage categories…") }
            } else {
                items(storage.categories, key = { it.id }) { category ->
                    KeyValueRow(category.title, category.usedBytes.fileSize())
                }
            }
            item { HintRow("Music & audio, Other apps, Files and System use the same per-user volume accounting categories as AAOS Settings.") }
        }
    }
}

@Composable
fun LegalRoute(viewModel: SystemViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    val snackbar = messageSnackbar(state.message, viewModel::clearMessage)
    SettingsScaffold(title = "Legal information", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        SnackbarHost(hostState = snackbar)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Legal") {} }
            val legal = state.settings.legal
            listOfNotNull(legal.terms, legal.webViewLicenses, legal.thirdPartyLicenses).forEach { action ->
                item(key = action.id) { ExternalActionRow(action, !state.isWorking, viewModel::launchExternal) }
            }
            if (listOfNotNull(legal.terms, legal.webViewLicenses, legal.thirdPartyLicenses).isEmpty()) {
                item { HintRow("No system legal-information provider is available") }
            }
        }
    }
}

@Composable
fun ResetOptionsRoute(
    viewModel: SystemViewModel,
    onBack: () -> Unit,
    onNetworkReset: () -> Unit,
    onResetAppPreferences: () -> Unit,
    onFactoryReset: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    val snackbar = messageSnackbar(state.message, viewModel::clearMessage)
    var confirmRestart by remember { mutableStateOf(false) }
    if (confirmRestart) {
        ConfirmationDialog(
            title = "Restart infotainment system?",
            text = "The vehicle system will restart. Open work may be interrupted.",
            confirmLabel = "Restart",
            onDismiss = { confirmRestart = false },
            onConfirm = { viewModel.restartSystem(); confirmRestart = false },
        )
    }
    SettingsScaffold(title = "Reset options", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        SnackbarHost(hostState = snackbar)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("System") {} }
            item {
                SettingsActionRow(
                    title = "Restart infotainment system",
                    summary = if (state.settings.reset.canRestartSystem) "Restart the vehicle system" else "Not available on this device",
                    leading = { Icon(Icons.Default.RestartAlt, null) },
                    enabled = state.settings.reset.canRestartSystem && !state.isWorking,
                    onClick = { confirmRestart = true },
                )
            }
            item { SettingsSection("Reset") {} }
            item {
                SettingsActionRow(
                    title = "Reset network settings",
                    summary = "Wi‑Fi, mobile network and Bluetooth settings",
                    leading = { Icon(Icons.Default.Wifi, null) },
                    enabled = state.settings.reset.canResetNetwork,
                    onClick = onNetworkReset,
                )
            }
            item {
                SettingsActionRow(
                    title = "Reset app preferences",
                    summary = "Re-enable apps, notifications and default app actions",
                    leading = { Icon(Icons.Default.Apps, null) },
                    enabled = state.settings.reset.canResetAppPreferences,
                    onClick = onResetAppPreferences,
                )
            }
            item {
                SettingsActionRow(
                    title = "Factory reset",
                    summary = "Erase all data from this vehicle",
                    leading = { Icon(Icons.Default.Warning, null) },
                    enabled = state.settings.reset.canFactoryReset,
                    onClick = onFactoryReset,
                )
            }
        }
    }
}

@Composable
fun ResetNetworkRoute(viewModel: SystemViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    var selectedSubscriptionId by remember(state.settings.reset.subscriptions) {
        mutableStateOf(state.settings.reset.subscriptions.firstOrNull()?.id)
    }
    var eraseEsim by remember(state.settings.reset.eraseEsimAvailable) { mutableStateOf(false) }
    var firstConfirm by remember { mutableStateOf(false) }
    var finalConfirm by remember { mutableStateOf(false) }
    val credentialVerifier = rememberCredentialVerifier()
    val snackbar = messageSnackbar(state.message, viewModel::clearMessage)
    if (firstConfirm) {
        ConfirmationDialog(
            title = "Reset network settings?",
            text = "This removes saved network and Bluetooth settings. You will need to reconnect devices.",
            confirmLabel = "Continue",
            onDismiss = { firstConfirm = false },
            onConfirm = {
                firstConfirm = false
                credentialVerifier { finalConfirm = true }
            },
        )
    }
    if (finalConfirm) {
        ConfirmationDialog(
            title = "Reset network settings",
            text = "This action cannot be undone.",
            confirmLabel = "Reset settings",
            onDismiss = { finalConfirm = false },
            onConfirm = {
                viewModel.resetNetwork(selectedSubscriptionId, eraseEsim)
                finalConfirm = false
            },
        )
    }
    SettingsScaffold(title = "Reset network settings", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        SnackbarHost(hostState = snackbar)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("The following settings will be reset") {} }
            items(state.settings.reset.resettableNetworks) { network -> HintRow("• $network") }
            if (state.settings.reset.subscriptions.size > 1) {
                item { SettingsSection("Mobile network") {} }
                items(state.settings.reset.subscriptions, key = { it.id }) { subscription ->
                    SubscriptionRow(
                        subscription = subscription,
                        selected = subscription.id == selectedSubscriptionId,
                        onSelected = { selectedSubscriptionId = subscription.id },
                    )
                }
            }
            if (state.settings.reset.eraseEsimAvailable) {
                item { SettingsSection("eSIM") {} }
                item {
                    SettingsSwitchRow(
                        title = "Erase downloaded eSIMs",
                        summary = "Also remove downloaded carrier profiles",
                        checked = eraseEsim,
                        enabled = !state.isWorking,
                        onCheckedChange = { eraseEsim = it },
                    )
                }
            }
            item {
                Button(
                    onClick = { firstConfirm = true },
                    enabled = state.settings.reset.canResetNetwork && !state.isWorking,
                    modifier = Modifier.padding(24.dp),
                ) {
                    if (state.isWorking) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    else Text("Reset settings")
                }
            }
        }
    }
}

@Composable
fun ResetAppPreferencesRoute(viewModel: SystemViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    var confirm by remember { mutableStateOf(false) }
    val snackbar = messageSnackbar(state.message, viewModel::clearMessage)
    if (confirm) {
        ConfirmationDialog(
            title = "Reset app preferences?",
            text = "This re-enables disabled apps, notifications, default-app actions and app operation modes. App data will not be deleted.",
            confirmLabel = "Reset apps",
            onDismiss = { confirm = false },
            onConfirm = { viewModel.resetAppPreferences(); confirm = false },
        )
    }
    SettingsScaffold(title = "Reset app preferences", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        SnackbarHost(hostState = snackbar)
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("No app data will be deleted. Disabled apps, notifications, default applications and background restrictions will be restored to their defaults.")
            Button(
                onClick = { confirm = true },
                enabled = state.settings.reset.canResetAppPreferences && !state.isWorking,
            ) {
                if (state.isWorking) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                else Text("Reset app preferences")
            }
        }
    }
}

@Composable
fun FactoryResetRoute(viewModel: SystemViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::onForeground)
    var eraseEsim by remember(state.settings.reset.eraseEsimAvailable) { mutableStateOf(true) }
    var firstConfirm by remember { mutableStateOf(false) }
    var finalConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val hasReadWarning by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            layout.totalItemsCount > 0 &&
                layout.visibleItemsInfo.lastOrNull()?.index == layout.totalItemsCount - 1
        }
    }
    val credentialVerifier = rememberCredentialVerifier()
    val snackbar = messageSnackbar(state.message, viewModel::clearMessage)
    if (firstConfirm) {
        ConfirmationDialog(
            title = "Erase all data?",
            text = "All accounts, apps and local vehicle data will be permanently removed.",
            confirmLabel = "Continue",
            onDismiss = { firstConfirm = false },
            onConfirm = {
                firstConfirm = false
                credentialVerifier { finalConfirm = true }
            },
        )
    }
    if (finalConfirm) {
        ConfirmationDialog(
            title = "Factory reset",
            text = "This is the final confirmation. The vehicle system will restart and erase its data.",
            confirmLabel = "Erase everything",
            onDismiss = { finalConfirm = false },
            onConfirm = {
                viewModel.factoryReset(eraseEsim)
                finalConfirm = false
            },
        )
    }
    SettingsScaffold(title = "Factory reset", onBack = onBack, actions = { RefreshAction(viewModel::refresh) }) {
        SnackbarHost(hostState = snackbar)
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            item { SettingsSection("Warning") {} }
            item { HintRow("Factory reset removes all accounts, downloaded apps, settings and local data from the vehicle.") }
            if (state.settings.reset.accountsAffected.isNotEmpty()) {
                item { SettingsSection("Accounts that will be removed") {} }
                items(state.settings.reset.accountsAffected) { account ->
                    SettingsActionRow(
                        title = account,
                        summary = "Will be removed during factory reset",
                        leading = { Icon(Icons.Default.AccountCircle, null) },
                        enabled = false,
                        onClick = {},
                    )
                }
            }
            if (state.settings.reset.otherProfilesPresent) {
                item { HintRow("Other vehicle profiles are present and will also be removed.") }
            }
            if (state.settings.reset.eraseEsimAvailable) {
                item { SettingsSection("eSIM") {} }
                item {
                    SettingsSwitchRow(
                        title = "Erase downloaded eSIMs",
                        summary = "Remove downloaded carrier profiles as part of reset",
                        checked = eraseEsim,
                        enabled = !state.isWorking,
                        onCheckedChange = { eraseEsim = it },
                    )
                }
            }
            item {
                Button(
                    onClick = { firstConfirm = true },
                    enabled = state.settings.reset.canFactoryReset && !state.isWorking && hasReadWarning,
                    modifier = Modifier.padding(24.dp),
                ) {
                    if (state.isWorking) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    else Text("Erase all data")
                }
            }
            if (!hasReadWarning) {
                item { HintRow("Scroll to the end to acknowledge the factory-reset warning.") }
            }
        }
    }
}

@Composable
private fun ExternalActionRow(
    action: SystemExternalAction,
    enabled: Boolean,
    onExternal: (SystemExternalActionId) -> Unit,
) = SettingsActionRow(
    title = action.title,
    summary = "Provided by the system image",
    leading = { Icon(Icons.Default.Settings, null) },
    enabled = enabled,
    onClick = { onExternal(action.id) },
)

@Composable
private fun SubscriptionRow(
    subscription: NetworkSubscription,
    selected: Boolean,
    onSelected: () -> Unit,
) = SettingsActionRow(
    title = subscription.label,
    summary = if (selected) "Selected for network reset" else "Use this subscription",
    leading = { Icon(Icons.Default.Wifi, null) },
    onClick = onSelected,
)

@Composable
private fun RefreshAction(onRefresh: () -> Unit) {
    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text("$title: ${value.toInt()}")
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            enabled = enabled,
        )
    }
}

@Composable
private fun HintRow(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
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
private fun messageSnackbar(message: String?, onMessageShown: () -> Unit): SnackbarHostState {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    return snackbar
}

@Composable
private fun rememberCredentialVerifier(): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) pendingAction?.invoke()
            pendingAction = null
        }
    return remember(context, launcher) {
        { continueAction ->
            val keyguardManager = context.getSystemService(KeyguardManager::class.java)
            val credentialIntent =
                keyguardManager?.takeIf { it.isDeviceSecure }
                    ?.createConfirmDeviceCredentialIntent("Confirm your identity", "Authenticate to continue")
            if (credentialIntent == null) {
                continueAction()
            } else {
                pendingAction = continueAction
                launcher.launch(credentialIntent)
            }
        }
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

private fun Long.fileSize(): String =
    when {
        this < 1_024L -> "$this B"
        this < 1_024L * 1_024L -> "%.1f KB".format(this / 1_024.0)
        this < 1_024L * 1_024L * 1_024L -> "%.1f MB".format(this / (1_024.0 * 1_024.0))
        else -> "%.1f GB".format(this / (1_024.0 * 1_024.0 * 1_024.0))
    }
