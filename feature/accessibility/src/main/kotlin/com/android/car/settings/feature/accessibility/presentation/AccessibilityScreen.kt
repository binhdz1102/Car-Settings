package com.android.car.settings.feature.accessibility.presentation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import com.android.car.settings.core.ui.SettingsLazyFocusListSlot
import com.android.car.settings.core.ui.SettingsLazyFocusListSpec
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.core.ui.settingsLazyFocusListSpec
import com.android.car.settings.feature.accessibility.domain.AccessibilityServiceEntry
import com.android.car.settings.feature.accessibility.domain.AccessibilityState
import com.android.car.settings.feature.accessibility.domain.CaptionTextSize
import com.android.car.settings.feature.accessibility.domain.CaptionTextStyle
import com.android.car.settings.core.ui.AutomotiveLazyColumn as LazyColumn
import com.android.car.settings.core.ui.AutomotiveTextButton as TextButton

@Composable
fun AccessibilityRoute(
    viewModel: AccessibilityViewModel,
    onBack: () -> Unit,
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    var sizeDialogVisible by remember { mutableStateOf(false) }
    var styleDialogVisible by remember { mutableStateOf(false) }
    val focusSpec = accessibilityRootFocusSpec(state.accessibility, state.isWorking)

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    SettingsScaffold(
        title = "Accessibility",
        destinationKey = "accessibility",
        isRoot = true,
        onBack = onBack,
        firstContentFocusId = focusSpec.firstContentFocusId,
        isContentFocusReady = state.isLoaded,
        lazyListState = listState,
        // Accessibility data arrives after the first composition. Keep the logical focus order
        // tied to the rendered list so a service inserted above Captions remains reachable by a
        // backward rotary step instead of being appended after the already-registered rows.
        lazyFocusItemIndexById = focusSpec.itemIndexByFocusId,
    ) {
        SnackbarHost(snackbarHost)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            item { SettingsSection("Screen reader") {} }
            if (state.accessibility.screenReaderSupported) {
                item {
                    SettingsSwitchRow(
                        title = state.accessibility.screenReaderName,
                        focusId = ACCESSIBILITY_SCREEN_READER_FOCUS_ID,
                        summary = "Read items on screen aloud",
                        checked = state.accessibility.screenReaderEnabled,
                        enabled = !state.isWorking,
                        busy = state.isWorking,
                        onCheckedChange = { enabled ->
                            state.accessibility.screenReaderComponent?.let { component ->
                                viewModel.setServiceEnabled(component, enabled)
                            }
                        },
                    )
                }
                state.accessibility.screenReaderSettingsActivity?.let { component ->
                    item {
                        SettingsActionRow(
                            title = "Screen reader settings",
                            focusId = ACCESSIBILITY_SCREEN_READER_SETTINGS_FOCUS_ID,
                            summary = component,
                            onClick = { context.openComponent(component) },
                        )
                    }
                }
            } else {
                item { Text("No spoken-feedback accessibility service is installed") }
            }

            item { SettingsSection("Captions") {} }
            item {
                SettingsSwitchRow(
                    title = "Show captions",
                    focusId = ACCESSIBILITY_SHOW_CAPTIONS_FOCUS_ID,
                    summary = "Display captions for supported audio",
                    checked = state.accessibility.captionsEnabled,
                    enabled = !state.isWorking,
                    busy = state.isWorking,
                    onCheckedChange = viewModel::setCaptionsEnabled,
                )
            }
            item {
                SettingsActionRow(
                    title = "Caption text size",
                    focusId = ACCESSIBILITY_CAPTION_TEXT_SIZE_FOCUS_ID,
                    summary = state.accessibility.captionTextSize.title,
                    onClick = { sizeDialogVisible = true },
                )
            }
            item {
                SettingsActionRow(
                    title = "Caption style",
                    focusId = ACCESSIBILITY_CAPTION_STYLE_FOCUS_ID,
                    summary = state.accessibility.captionTextStyle.title,
                    onClick = { styleDialogVisible = true },
                )
            }

            item { SettingsSection("Installed accessibility services") {} }
            if (state.accessibility.services.isEmpty()) {
                item { Text("No accessibility service is installed") }
            }
            items(state.accessibility.services, key = AccessibilityServiceEntry::componentName) { service ->
                SettingsSwitchRow(
                    title = service.label,
                    focusId = accessibilityServiceFocusId(service.componentName),
                    summary = service.description.ifBlank { service.componentName },
                    checked = service.enabled,
                    enabled = !state.isWorking,
                    busy = state.isWorking,
                    onCheckedChange = { viewModel.setServiceEnabled(service.componentName, it) },
                )
            }
            state.accessibility.lastError?.let { error -> item { Text(error) } }
        }
    }

    if (sizeDialogVisible) {
        CaptionSizeDialog(
            selected = state.accessibility.captionTextSize,
            onSelect = {
                sizeDialogVisible = false
                viewModel.setCaptionTextSize(it)
            },
            onDismiss = { sizeDialogVisible = false },
        )
    }
    if (styleDialogVisible) {
        CaptionStyleDialog(
            selected = state.accessibility.captionTextStyle,
            onSelect = {
                styleDialogVisible = false
                viewModel.setCaptionTextStyle(it)
            },
            onDismiss = { styleDialogVisible = false },
        )
    }
}

internal fun accessibilityRootFocusSpec(
    state: AccessibilityState,
    isWorking: Boolean,
): SettingsLazyFocusListSpec =
    settingsLazyFocusListSpec(
        buildList {
            add(SettingsLazyFocusListSlot()) // Screen reader section heading.
            if (state.screenReaderSupported) {
                add(SettingsLazyFocusListSlot(ACCESSIBILITY_SCREEN_READER_FOCUS_ID, isEnabled = !isWorking))
                if (state.screenReaderSettingsActivity != null) {
                    add(SettingsLazyFocusListSlot(ACCESSIBILITY_SCREEN_READER_SETTINGS_FOCUS_ID))
                }
            } else {
                add(SettingsLazyFocusListSlot()) // No spoken-feedback service message.
            }

            add(SettingsLazyFocusListSlot()) // Captions section heading.
            add(SettingsLazyFocusListSlot(ACCESSIBILITY_SHOW_CAPTIONS_FOCUS_ID, isEnabled = !isWorking))
            add(SettingsLazyFocusListSlot(ACCESSIBILITY_CAPTION_TEXT_SIZE_FOCUS_ID))
            add(SettingsLazyFocusListSlot(ACCESSIBILITY_CAPTION_STYLE_FOCUS_ID))

            add(SettingsLazyFocusListSlot()) // Installed accessibility services heading.
            if (state.services.isEmpty()) {
                add(SettingsLazyFocusListSlot()) // No-service message.
            } else {
                state.services.forEach { service ->
                    add(
                        SettingsLazyFocusListSlot(
                            accessibilityServiceFocusId(service.componentName),
                            isEnabled = !isWorking,
                        ),
                    )
                }
            }
        },
    )

internal fun accessibilityLazyFocusIndexById(state: AccessibilityState): Map<String, Int> =
    accessibilityRootFocusSpec(state, isWorking = false).itemIndexByFocusId

private fun accessibilityServiceFocusId(componentName: String): String = "accessibility-service-$componentName"

private const val ACCESSIBILITY_SCREEN_READER_FOCUS_ID = "accessibility-screen-reader"
private const val ACCESSIBILITY_SCREEN_READER_SETTINGS_FOCUS_ID = "accessibility-screen-reader-settings"
private const val ACCESSIBILITY_SHOW_CAPTIONS_FOCUS_ID = "accessibility-show-captions"
private const val ACCESSIBILITY_CAPTION_TEXT_SIZE_FOCUS_ID = "accessibility-caption-text-size"
private const val ACCESSIBILITY_CAPTION_STYLE_FOCUS_ID = "accessibility-caption-style"

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
private fun CaptionSizeDialog(
    selected: CaptionTextSize,
    onSelect: (CaptionTextSize) -> Unit,
    onDismiss: () -> Unit,
) {
    AutomotiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Caption text size") },
        text = {
            LazyColumn {
                items(CaptionTextSize.values().toList()) { size ->
                    SettingsActionRow(
                        title = size.title,
                        leading = {
                            RadioButton(selected = size == selected, onClick = { onSelect(size) })
                        },
                        onClick = { onSelect(size) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        dismissAction = onDismiss,
    )
}

@Composable
private fun CaptionStyleDialog(
    selected: CaptionTextStyle,
    onSelect: (CaptionTextStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    AutomotiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Caption style") },
        text = {
            LazyColumn {
                items(CaptionTextStyle.values().toList()) { style ->
                    SettingsActionRow(
                        title = style.title,
                        leading = {
                            RadioButton(selected = style == selected, onClick = { onSelect(style) })
                        },
                        onClick = { onSelect(style) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        dismissAction = onDismiss,
    )
}

private fun Context.openComponent(flattenedComponent: String) {
    runCatching {
        val component =
            ComponentName.unflattenFromString(flattenedComponent)
                ?: return@runCatching
        startActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
