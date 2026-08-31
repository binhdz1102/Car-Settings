package com.android.car.settings.feature.display.presentation

import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsAppBarAction
import com.android.car.settings.core.ui.SettingsFormSlider
import com.android.car.settings.core.ui.SettingsLeadingIcon
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.feature.display.domain.GAMMA_SPACE_MAX
import com.android.car.settings.feature.display.domain.ThemeMode
import kotlin.math.roundToInt
import com.android.car.settings.core.ui.AutomotiveLazyColumn as LazyColumn

@Composable
fun DisplayRoute(
    viewModel: DisplayViewModel,
    onBack: () -> Unit,
    onDateTime: () -> Unit,
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
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
        listState = listState,
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
    listState: LazyListState = rememberLazyListState(),
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
        destinationKey = "display",
        isRoot = true,
        onBack = onBack,
        actions = {
            SettingsAppBarAction(
                id = "display-refresh",
                contentDescription = "Refresh display settings",
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
            item { SettingsSection("Brightness") {} }
            item { BrightnessRow(gamma = state.display.brightnessGamma, onBrightness = onBrightness) }
            if (state.display.adaptiveBrightnessAvailable) {
                item {
                    SettingsSwitchRow(
                        title = "Adaptive brightness",
                        summary = "Automatically adjust brightness using ambient light",
                        checked = state.display.adaptiveBrightnessEnabled,
                        enabled = !state.isWorking,
                        retainFocusWhenDisabled = state.isWorking,
                        leading = { SettingsLeadingIcon(Icons.Default.Brightness6) },
                        onCheckedChange = onAdaptiveBrightness,
                    )
                }
            }
            if (state.display.themeModeAvailable) {
                item { SettingsSection("Theme") {} }
                item {
                    SettingsSwitchRow(
                        title = "Dark mode",
                        summary = state.display.themeMode.themeSummary(),
                        checked = state.display.themeMode == ThemeMode.NIGHT,
                        enabled = !state.isWorking,
                        retainFocusWhenDisabled = state.isWorking,
                        focusId = "display-dark-mode",
                        leading = { SettingsLeadingIcon(Icons.Default.DarkMode) },
                        onCheckedChange = { enabled ->
                            onTheme(if (enabled) ThemeMode.NIGHT else ThemeMode.DAY)
                        },
                    )
                }
                item {
                    SettingsActionRow(
                        title = "Use system theme",
                        summary = "Follow the vehicle's automatic day/night setting",
                        focusId = "display-theme-auto",
                        enabled = !state.isWorking,
                        leading = { SettingsLeadingIcon(Icons.Default.Brightness6) },
                        trailing = {
                            RadioButton(
                                selected = state.display.themeMode == ThemeMode.AUTO,
                                onClick = { if (!state.isWorking) onTheme(ThemeMode.AUTO) },
                            )
                        },
                        onClick = { onTheme(ThemeMode.AUTO) },
                    )
                }
            }
            item { SettingsSection("More display settings") {} }
            item {
                SettingsActionRow(
                    navigates = true,
                    title = "Date & time",
                    summary = "Date, time, time zone and formats",
                    leading = { SettingsLeadingIcon(Icons.Default.Settings) },
                    onClick = onDateTime,
                )
            }
            item {
                SettingsActionRow(
                    title = "Calm mode",
                    summary = "Show a distraction-free clock screen",
                    leading = { SettingsLeadingIcon(Icons.Default.Brightness6) },
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

    SettingsFormSlider(
        focusId = "display-brightness",
        label = "Brightness ($displayedPercent%)",
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
        // Gamma is a continuous 16-bit range; passing 65,534 discrete steps to BSlider is
        // both unnecessary and can make its internal tick/semantics work explode on AAOS.
        steps = 0,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun ThemeMode.themeSummary(): String =
    when (this) {
        ThemeMode.AUTO -> "Following the vehicle's automatic day/night setting"
        ThemeMode.DAY -> "Light colors are enabled"
        ThemeMode.NIGHT -> "Dark colors are enabled"
    }

private val CALM_MODE_COMPONENT =
    ComponentName(
        "com.android.car.carlauncher",
        "com.android.car.carlauncher.calmmode.CalmModeActivity",
    )
