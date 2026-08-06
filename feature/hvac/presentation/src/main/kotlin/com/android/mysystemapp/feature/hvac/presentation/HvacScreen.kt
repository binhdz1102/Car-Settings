package com.android.car.settings.feature.hvac.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection
import com.android.car.settings.core.ui.SettingsSwitchRow
import com.android.car.settings.feature.hvac.domain.ClimateControl
import com.android.car.settings.feature.hvac.domain.ClimateControlId
import com.android.car.settings.feature.hvac.domain.ClimateControlKind
import com.android.car.settings.feature.hvac.domain.ClimateState
import com.android.car.settings.feature.hvac.domain.ClimateValueStatus

@Composable
fun HvacRoute(
    viewModel: HvacViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HvacScreen(
        state = uiState.climate,
        isRefreshing = uiState.isRefreshing,
        message = uiState.message,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSetBoolean = viewModel::setBoolean,
        onSetInt = viewModel::setInt,
        onSetFloat = viewModel::setFloat,
        onDismissMessage = viewModel::clearMessage,
    )
}

@Composable
internal fun HvacScreen(
    state: ClimateState,
    isRefreshing: Boolean,
    message: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetBoolean: (String, Boolean) -> Unit,
    onSetInt: (String, Int) -> Unit,
    onSetFloat: (String, Float) -> Unit,
    onDismissMessage: () -> Unit,
) {
    // Tabs represent occupant climate zones only.  Properties such as defrost can use
    // windshield bit masks as their area; exposing those masks as tabs is misleading.
    val occupantZoneControls = setOf(
        ClimateControlId.TEMPERATURE_SET,
        ClimateControlId.TEMPERATURE_CURRENT,
        ClimateControlId.FAN_SPEED,
        ClimateControlId.FAN_DIRECTION,
        ClimateControlId.AC,
        ClimateControlId.SEAT_TEMPERATURE,
        ClimateControlId.SEAT_VENTILATION,
    )
    val zones = state.controls
        .filter { it.capability.id in occupantZoneControls }
        .map { it.capability.zone }
        .distinctBy { it.areaId }
        .filterNot { it.areaId == 0 || it.title.contains(" + ") }
    var selectedAreaId by rememberSaveable { mutableStateOf<Int?>(null) }
    val activeAreaId = selectedAreaId?.takeIf { selected -> zones.any { it.areaId == selected } }
        ?: zones.firstOrNull()?.areaId
    val visibleControls = state.controls.filter { control ->
        val area = control.capability.zone.areaId
        control.capability.zone.title == "Global" ||
            control.capability.zone.title == "All climate zones" ||
            activeAreaId == null || area == activeAreaId
    }

    SettingsScaffold(
        title = "Climate",
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh climate controls")
            }
        },
    ) {
        if (isRefreshing && state.controls.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            return@SettingsScaffold
        }
        if (!state.connected && state.controls.isEmpty()) {
            Text(
                state.lastError ?: "Connecting to vehicle climate service…",
                modifier = Modifier.padding(24.dp),
            )
            return@SettingsScaffold
        }
        LazyColumn {
            if (zones.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        zones.forEach { zone ->
                            FilterChip(
                                selected = zone.areaId == activeAreaId,
                                onClick = { selectedAreaId = zone.areaId },
                                label = { Text(zone.title) },
                            )
                        }
                    }
                }
            }
            message?.let { text ->
                item {
                    SettingsActionRow(
                        title = "Climate action failed",
                        summary = text,
                        onClick = onDismissMessage,
                    )
                }
            }
            visibleControls.groupBy(ClimateControl::section).forEach { (section, controls) ->
                item { SettingsSection(title = section) {} }
                items(controls, key = ClimateControl::key) { control ->
                    ClimateControlRow(control, onSetBoolean, onSetInt, onSetFloat)
                }
            }
        }
    }
}

@Composable
private fun ClimateControlRow(
    control: ClimateControl,
    onSetBoolean: (String, Boolean) -> Unit,
    onSetInt: (String, Int) -> Unit,
    onSetFloat: (String, Float) -> Unit,
) {
    val enabled = control.capability.writable &&
        control.status != ClimateValueStatus.UNAVAILABLE &&
        control.status != ClimateValueStatus.ERROR
    val summary = buildString {
        append(control.capability.zone.title)
        if (control.unavailableReason != null) append(" · ${control.unavailableReason}")
        else if (control.capability.kind == ClimateControlKind.READ_ONLY_FLOAT) {
            append(" · ${control.floatValue?.let { "%.1f".format(it) } ?: "--"}")
        }
    }
    when (control.capability.kind) {
        ClimateControlKind.TOGGLE -> SettingsSwitchRow(
            title = control.title,
            summary = summary,
            checked = control.booleanValue == true,
            enabled = enabled,
            busy = control.status == ClimateValueStatus.PENDING,
            onCheckedChange = { onSetBoolean(control.key, it) },
        )
        ClimateControlKind.FLOAT_RANGE -> FloatRangeRow(
            control = control,
            summary = summary,
            enabled = enabled,
            onSet = onSetFloat,
        )
        ClimateControlKind.INT_RANGE -> IntRangeRow(
            control = control,
            summary = summary,
            enabled = enabled,
            onSet = onSetInt,
        )
        ClimateControlKind.INT_OPTIONS -> IntOptionsRow(
            control = control,
            summary = summary,
            enabled = enabled,
            onSet = onSetInt,
        )
        ClimateControlKind.READ_ONLY_FLOAT -> SettingsActionRow(
            title = control.title,
            summary = summary,
            enabled = false,
            onClick = {},
        )
    }
}

@Composable
private fun FloatRangeRow(
    control: ClimateControl,
    summary: String,
    enabled: Boolean,
    onSet: (String, Float) -> Unit,
) {
    val min = control.capability.min ?: return
    val max = control.capability.max ?: return
    var dragging by remember(control.key) { mutableStateOf(false) }
    var draft by remember(control.key) { mutableFloatStateOf(control.floatValue ?: min) }
    LaunchedEffect(control.floatValue, dragging) {
        if (!dragging) draft = control.floatValue ?: min
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(control.title, style = MaterialTheme.typography.titleMedium)
            Text("%.1f".format(draft))
        }
        Text(summary, style = MaterialTheme.typography.bodySmall)
        Slider(
            value = draft,
            onValueChange = { dragging = true; draft = it },
            onValueChangeFinished = {
                dragging = false
                onSet(control.key, draft)
            },
            valueRange = min..max,
            enabled = enabled,
        )
        if (control.status == ClimateValueStatus.PENDING) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun IntRangeRow(
    control: ClimateControl,
    summary: String,
    enabled: Boolean,
    onSet: (String, Int) -> Unit,
) {
    val min = control.capability.min?.toInt() ?: return
    val max = control.capability.max?.toInt() ?: return
    var dragging by remember(control.key) { mutableStateOf(false) }
    var draft by remember(control.key) { mutableFloatStateOf((control.intValue ?: min).toFloat()) }
    LaunchedEffect(control.intValue, dragging) {
        if (!dragging) draft = (control.intValue ?: min).toFloat()
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(control.title, style = MaterialTheme.typography.titleMedium)
            Text(draft.toInt().toString())
        }
        Text(summary, style = MaterialTheme.typography.bodySmall)
        Slider(
            value = draft,
            onValueChange = { dragging = true; draft = it },
            onValueChangeFinished = {
                dragging = false
                onSet(control.key, draft.toInt())
            },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1).coerceAtLeast(0),
            enabled = enabled,
        )
        if (control.status == ClimateValueStatus.PENDING) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun IntOptionsRow(
    control: ClimateControl,
    summary: String,
    enabled: Boolean,
    onSet: (String, Int) -> Unit,
) {
    val options = control.capability.options
    if (options.isEmpty()) {
        SettingsActionRow(
            title = control.title,
            summary = "$summary · ${control.intValue ?: "--"}",
            enabled = false,
            onClick = {},
        )
        return
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(control.title, style = MaterialTheme.typography.titleMedium)
        Text(summary, style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = control.intValue == option,
                    enabled = enabled,
                    onClick = { onSet(control.key, option) },
                    label = { Text(control.capability.optionLabels[option] ?: option.toString()) },
                )
            }
        }
        if (control.status == ClimateValueStatus.PENDING) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}
