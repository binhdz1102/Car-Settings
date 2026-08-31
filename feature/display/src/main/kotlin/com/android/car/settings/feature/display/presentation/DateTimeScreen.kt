package com.android.car.settings.feature.display.presentation

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsAppBarAction
import com.android.car.settings.core.ui.SettingsFormTextField
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import java.util.Calendar
import java.util.TimeZone
import com.android.car.settings.core.ui.AutomotiveLazyColumn as LazyColumn

@Composable
fun DateTimeRoute(
    viewModel: DateTimeViewModel,
    onBack: () -> Unit,
    onTimeZone: () -> Unit,
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

    DateTimeScreen(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onAutoTime = viewModel::setAutoTime,
        onAutoTimeZone = viewModel::setAutoTimeZone,
        onUse24HourFormat = viewModel::setUse24HourFormat,
        onManualTime = viewModel::setManualTime,
        onTimeZone = onTimeZone,
    )
}

@Composable
private fun DateTimeScreen(
    state: DateTimeUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAutoTime: (Boolean) -> Unit,
    onAutoTimeZone: (Boolean) -> Unit,
    onUse24HourFormat: (Boolean) -> Unit,
    onManualTime: (Long) -> Unit,
    onTimeZone: () -> Unit,
) {
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val dateTime = state.dateTime

    DatePickerEffect(
        visible = showDatePicker,
        epochMillis = dateTime.currentEpochMillis,
        onDismiss = { showDatePicker = false },
        onDateSelected = onManualTime,
    )
    TimePickerEffect(
        visible = showTimePicker,
        epochMillis = dateTime.currentEpochMillis,
        is24Hour = dateTime.use24HourFormat,
        onDismiss = { showTimePicker = false },
        onTimeSelected = onManualTime,
    )

    SettingsScaffold(
        title = "Date & time",
        onBack = onBack,
        actions = {
            SettingsAppBarAction(
                id = "date-time-refresh",
                contentDescription = "Refresh date and time",
                onClick = onRefresh,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        SnackbarHost(hostState = snackbarHost)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSection("Automatic settings") {} }
            if (dateTime.autoTimeAvailable) {
                item {
                    SettingsSwitchRow(
                        title = "Set time automatically",
                        summary = "Use the network or vehicle time source",
                        checked = dateTime.autoTimeEnabled,
                        enabled = !state.isWorking,
                        busy = state.isWorking,
                        onCheckedChange = onAutoTime,
                    )
                }
            }
            if (dateTime.autoTimeZoneAvailable) {
                item {
                    SettingsSwitchRow(
                        title = "Set time zone automatically",
                        summary = "Use the current location or network time zone",
                        checked = dateTime.autoTimeZoneEnabled,
                        enabled = !state.isWorking,
                        busy = state.isWorking,
                        onCheckedChange = onAutoTimeZone,
                    )
                }
            }
            item { SettingsSection("Manual settings") {} }
            item {
                SettingsActionRow(
                    title = "Set date",
                    summary = DateFormat.getLongDateFormat(context).format(dateTime.currentEpochMillis),
                    enabled = dateTime.canSetManualTime && !state.isWorking,
                    onClick = { showDatePicker = true },
                )
            }
            item {
                SettingsActionRow(
                    title = "Set time",
                    summary = DateFormat.getTimeFormat(context).format(dateTime.currentEpochMillis),
                    enabled = dateTime.canSetManualTime && !state.isWorking,
                    onClick = { showTimePicker = true },
                )
            }
            item {
                SettingsActionRow(
                    title = "Time zone",
                    summary = timeZoneSummary(dateTime.timeZoneId),
                    enabled = dateTime.canSetManualTimeZone && !state.isWorking,
                    onClick = onTimeZone,
                )
            }
            item { SettingsSection("Format") {} }
            item {
                SettingsSwitchRow(
                    title = "Use 24-hour format",
                    summary = timeFormatPreview(context),
                    checked = dateTime.use24HourFormat,
                    enabled = !state.isWorking,
                    busy = state.isWorking,
                    onCheckedChange = onUse24HourFormat,
                )
            }
        }
    }
}

@Composable
fun TimeZoneRoute(
    viewModel: DateTimeViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    val zones = remember { TimeZone.getAvailableIDs().sorted() }
    val matchingZones =
        remember(query, zones) {
            zones.filter { id -> id.contains(query, ignoreCase = true) }
        }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.savedTimeZoneId) {
        if (state.savedTimeZoneId != null) {
            viewModel.clearSavedTimeZone()
            onBack()
        }
    }

    SettingsScaffold(title = "Time zone", onBack = onBack) {
        SnackbarHost(hostState = snackbarHost)
        SettingsFormTextField(
            focusId = "timezone-search",
            value = query,
            onValueChange = { query = it },
            label = "Search time zones",
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(matchingZones, key = { it }) { id ->
                SettingsActionRow(
                    title = id,
                    summary =
                        if (state.dateTime.timeZoneId == id) {
                            "${timeZoneSummary(id)} · Current"
                        } else {
                            timeZoneSummary(id)
                        },
                    focusId = "timezone-$id",
                    enabled = !state.isWorking,
                    onClick = { viewModel.setManualTimeZone(id) },
                )
            }
        }
    }
}

@Composable
private fun DatePickerEffect(
    visible: Boolean,
    epochMillis: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit,
) {
    val context = LocalContext.current
    val onDismissUpdated by rememberUpdatedState(onDismiss)
    val onDateSelectedUpdated by rememberUpdatedState(onDateSelected)
    DisposableEffect(visible, epochMillis) {
        if (!visible) return@DisposableEffect onDispose {}
        val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val dialog =
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    onDateSelectedUpdated(calendar.timeInMillis)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
            )
        dialog.setOnDismissListener { onDismissUpdated() }
        dialog.show()
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
        }
    }
}

@Composable
private fun TimePickerEffect(
    visible: Boolean,
    epochMillis: Long,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onTimeSelected: (Long) -> Unit,
) {
    val context = LocalContext.current
    val onDismissUpdated by rememberUpdatedState(onDismiss)
    val onTimeSelectedUpdated by rememberUpdatedState(onTimeSelected)
    DisposableEffect(visible, epochMillis, is24Hour) {
        if (!visible) return@DisposableEffect onDispose {}
        val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val dialog =
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    onTimeSelectedUpdated(calendar.timeInMillis)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                is24Hour,
            )
        dialog.setOnDismissListener { onDismissUpdated() }
        dialog.show()
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
        }
    }
}

private fun timeZoneSummary(timeZoneId: String): String {
    val zone = TimeZone.getTimeZone(timeZoneId)
    val offsetMillis = zone.getOffset(System.currentTimeMillis())
    val sign = if (offsetMillis >= 0) "+" else "-"
    val totalMinutes = kotlin.math.abs(offsetMillis / 60_000)
    return "GMT$sign%02d:%02d · %s".format(totalMinutes / 60, totalMinutes % 60, zone.getDisplayName())
}

private fun timeFormatPreview(context: android.content.Context): String {
    val demo = Calendar.getInstance().apply { set(2000, Calendar.DECEMBER, 31, 13, 0, 0) }
    return "Example: ${DateFormat.getTimeFormat(context).format(demo.time)}"
}
