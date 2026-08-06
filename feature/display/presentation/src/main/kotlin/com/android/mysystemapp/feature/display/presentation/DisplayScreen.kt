package com.android.car.settings.feature.display.presentation

import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.feature.display.domain.GAMMA_SPACE_MAX
import com.android.car.settings.feature.display.domain.ThemeMode
import kotlin.math.roundToInt

@Composable
fun DisplayRoute(
    viewModel: DisplayViewModel,
    onBack: () -> Unit,
    onDateTime: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.onForeground()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    DisplayScreen(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onBrightness = viewModel::setBrightness,
        onAdaptiveBrightness = viewModel::setAdaptiveBrightness,
        onTheme = viewModel::setThemeMode,
        onDateTime = onDateTime,
    )
}

@Composable
private fun DisplayScreen(
    state: DisplayUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onBrightness: (Int) -> Unit,
    onAdaptiveBrightness: (Boolean) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onDateTime: () -> Unit,
) {
    val context = LocalContext.current
    SettingsScaffold(
        title = "Display",
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        SnackbarHost(hostState = snackbarHost)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Brightness") {} }
            item { BrightnessRow(gamma = state.display.brightnessGamma, onBrightness = onBrightness) }
            if (state.display.adaptiveBrightnessAvailable) {
                item {
                    SettingsSwitchRow(
                        title = "Adaptive brightness",
                        summary = "Automatically adjust brightness using ambient light",
                        checked = state.display.adaptiveBrightnessEnabled,
                        enabled = !state.isWorking,
                        onCheckedChange = onAdaptiveBrightness,
                    )
                }
            }
            if (state.display.themeModeAvailable) {
                item { SettingsSection("Theme") {} }
                item {
                    ThemeMode.entries.forEach { mode ->
                        ListItem(
                            modifier = Modifier.clickable(enabled = !state.isWorking) { onTheme(mode) },
                            headlineContent = { Text(mode.title()) },
                            leadingContent = {
                                RadioButton(
                                    selected = state.display.themeMode == mode,
                                    onClick = if (state.isWorking) null else { { onTheme(mode) } },
                                )
                            },
                        )
                    }
                }
            }
            item { SettingsSection("More display settings") {} }
            item {
                SettingsActionRow(
                    title = "Date & time",
                    summary = "Date, time, time zone and formats",
                    onClick = onDateTime,
                )
            }
            item {
                SettingsActionRow(
                    title = "Calm mode",
                    summary = "Show a distraction-free clock screen",
                    onClick = {
                        context.startActivity(
                            Intent().setComponent(CALM_MODE_COMPONENT),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun BrightnessRow(
    gamma: Int,
    onBrightness: (Int) -> Unit,
) {
    var isDragging by remember { mutableStateOf(false) }
    var pendingGamma by remember { mutableFloatStateOf(gamma.toFloat()) }
    LaunchedEffect(gamma) {
        if (!isDragging) pendingGamma = gamma.toFloat()
    }
    val displayedPercent = (pendingGamma * 100 / GAMMA_SPACE_MAX).roundToInt()

    ListItem(
        headlineContent = { Text("Brightness") },
        leadingContent = { Icon(Icons.Default.Brightness6, contentDescription = null) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Slider(
                    value = pendingGamma,
                    valueRange = 0f..GAMMA_SPACE_MAX.toFloat(),
                    onValueChange = {
                        isDragging = true
                        pendingGamma = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        onBrightness(pendingGamma.roundToInt())
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("$displayedPercent%")
            }
        },
    )
}

private fun ThemeMode.title(): String =
    when (this) {
        ThemeMode.AUTO -> "Automatic"
        ThemeMode.DAY -> "Day"
        ThemeMode.NIGHT -> "Night"
    }

private val CALM_MODE_COMPONENT =
    ComponentName(
        "com.android.car.carlauncher",
        "com.android.car.carlauncher.calmmode.CalmModeActivity",
    )
