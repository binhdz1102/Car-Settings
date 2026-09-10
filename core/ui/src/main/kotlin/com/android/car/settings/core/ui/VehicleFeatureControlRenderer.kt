package com.android.car.settings.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.ccp.rotaryfocus.DirectManipulationConfig
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRole
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemSemantics
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemTouchBehavior
import com.b231001.bmaterial.uicomponents.chip.BChip
import com.b231001.bmaterial.uicomponents.chip.BChipSize
import com.b231001.bmaterial.uicomponents.chip.BChipStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Vehicle controls rendering and rotary focus contract. Navigation/overview and feature previews
 * stay in VehicleFeatureScreen.kt; this file owns the reusable controls pane and its actions.
 */
@Composable
internal fun VehicleControlsPane(
    controls: List<VehicleControlUiModel>,
    zones: List<VehicleZoneOption>,
    selectedAreaId: Int,
    onAreaSelected: (Int) -> Unit,
    connected: Boolean,
    restricted: Boolean,
    restrictedReason: String,
    onRefresh: () -> Unit,
    onOpenInfo: (VehicleControlUiModel) -> Unit,
    onFocused: (VehicleControlUiModel) -> Unit,
    onSetBoolean: (String, Int, Boolean) -> Unit,
    onSetInt: (String, Int, Int) -> Unit,
    onSetFloat: ((String, Int, Float) -> Unit)?,
    zoneAreaId: String,
    controlsAreaId: String,
    zoneSelectorLabel: String,
    showVehicleDiagram: Boolean,
    allowInfo: Boolean = true,
    scrollInternally: Boolean,
    modifier: Modifier = Modifier,
) {
    val controlsScrollState = rememberScrollState()
    val scrollModifier =
        if (scrollInternally) {
            Modifier
                .automotiveScrollbar(controlsScrollState)
                .verticalScroll(controlsScrollState)
        } else {
            Modifier
        }
    Column(
        modifier = modifier.then(scrollModifier),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val errorMessage = controls.firstNotNullOfOrNull { it.errorMessage }
        if (zones.size > 1) {
            VehicleZoneFocusSelector(
                label = zoneSelectorLabel,
                zones = zones,
                zoneSelectionEnabled = vehicleZoneSelectionEnabled(connected, controls.isNotEmpty()),
                selectedAreaId = selectedAreaId,
                onAreaSelected = onAreaSelected,
                areaId = zoneAreaId,
                showVehicleDiagram = showVehicleDiagram,
            )
        }
        if (controls.isEmpty()) {
            RotaryUnavailableState(
                areaId = controlsAreaId,
                title = null,
                message = stringResource(R.string.vehicle_control_category_empty),
                actionLabel = stringResource(R.string.vehicle_control_retry),
                onAction = onRefresh,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val focusLayout =
                remember(controls, selectedAreaId, controlsAreaId, connected, restricted, errorMessage, allowInfo) {
                    buildVehicleControlFocusLayout(
                        controls = controls,
                        selectedAreaId = selectedAreaId,
                        controlsAreaId = controlsAreaId,
                        connected = connected,
                        restricted = restricted,
                        hasRetryAction = errorMessage != null,
                        includeInfo = allowInfo,
                    )
                }
            val controlsFocusAreaId = FocusAreaId(controlsAreaId)
            val controlsFocusPolicy = vehicleDetailFocusAreaPolicy(controlsFocusAreaId)
            FocusArea(
                id = controlsFocusAreaId,
                firstFocusAt = focusLayout.firstMeaningfulFocusId ?: focusLayout.focusOrder.firstOrNull(),
                focusOrder = focusLayout.focusOrder,
                wrapAround = controlsFocusPolicy.wrapAround,
                previousFocusArea = controlsFocusPolicy.previousFocusArea,
                nextFocusArea = controlsFocusPolicy.nextFocusArea,
                modifier = Modifier.fillMaxWidth(),
            ) {
                VehicleControlSection(
                    title = controls.first().section,
                    description = stringResource(R.string.vehicle_control_rotary_hint),
                ) {
                    if (errorMessage != null) {
                        VehicleErrorBanner(
                            message = errorMessage,
                            onRetry = onRefresh,
                            retryFocusId = FocusItemId("$controlsAreaId-retry"),
                        )
                    }
                    focusLayout.entries.forEachIndexed { index, entry ->
                        val control = entry.control
                        if (entry.rotaryActionable) {
                            VehicleRotaryControlRow(
                                focusId = entry.focusId,
                                infoFocusId = entry.infoFocusId,
                                control = control,
                                connected = connected,
                                restricted = restricted,
                                restrictedReason = restrictedReason,
                                onFocused = { onFocused(control) },
                                onOpenInfo = { onOpenInfo(control) },
                                onSetBoolean = onSetBoolean,
                                onSetInt = onSetInt,
                                onSetFloat = onSetFloat,
                                showInfo = allowInfo,
                                showDivider = index != controls.lastIndex,
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier =
                                        Modifier.weight(1f).clickable(
                                            // Read-only rows carry no FocusItem so they never
                                            // become rotary stops, but a finger tap should still
                                            // select the control for the preview pane.
                                            onClickLabel = "Select for preview",
                                            onClick = { onFocused(control) },
                                        ),
                                ) {
                                    VehicleFeatureControlRow(
                                        control = control,
                                        connected = connected,
                                        restricted = restricted,
                                        restrictedReason = restrictedReason,
                                        disconnectedReason =
                                            stringResource(R.string.vehicle_control_service_unavailable),
                                        onOpenInfo = { onOpenInfo(control) },
                                        onSetBoolean = onSetBoolean,
                                        onSetInt = onSetInt,
                                        onSetFloat = onSetFloat,
                                        showInfo = allowInfo,
                                        showDivider = false,
                                    )
                                }
                                if (allowInfo) {
                                    VehicleInfoFocusItem(
                                        focusId = entry.infoFocusId,
                                        title = control.title,
                                        onClick = { onOpenInfo(control) },
                                    )
                                }
                            }
                            if (index != controls.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VehicleZoneFocusSelector(
    label: String,
    zones: List<VehicleZoneOption>,
    zoneSelectionEnabled: Boolean,
    selectedAreaId: Int,
    onAreaSelected: (Int) -> Unit,
    areaId: String,
    showVehicleDiagram: Boolean,
) {
    val focusIds = remember(zones, areaId) { zones.map { FocusItemId("$areaId-${it.areaId}") } }
    val actionableAreaIds =
        remember(zones, zoneSelectionEnabled) {
            actionableVehicleZoneIds(zones, zoneSelectionEnabled).toSet()
        }
    val actionableFocusIds =
        remember(focusIds, actionableAreaIds) {
            zones.mapIndexedNotNull { index, zone ->
                if (zone.areaId in actionableAreaIds) focusIds[index] else null
            }
        }
    val zoneScrollState = rememberScrollState()
    val topViewZones = remember(zones) { zones.toTopViewZones() }
    val zoneFocusAreaId = FocusAreaId(areaId)
    val zoneFocusPolicy = vehicleDetailFocusAreaPolicy(zoneFocusAreaId)
    FocusArea(
        id = zoneFocusAreaId,
        firstFocusAt = actionableFocusIds.firstOrNull(),
        focusOrder = actionableFocusIds,
        wrapAround = zoneFocusPolicy.wrapAround,
        previousFocusArea = zoneFocusPolicy.previousFocusArea,
        nextFocusArea = zoneFocusPolicy.nextFocusArea,
        modifier = Modifier.fillMaxWidth(),
    ) {
        VehicleControlSection(title = label) {
            if (showVehicleDiagram) {
                VehicleTopView(
                    selectedAreaId = selectedAreaId,
                    zones = topViewZones,
                    onZoneSelected = if (zoneSelectionEnabled) ({ onAreaSelected(it.areaId) }) else null,
                    enabled = zoneSelectionEnabled,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .automotiveScrollbar(
                            scrollState = zoneScrollState,
                            orientation = Orientation.Horizontal,
                        ).horizontalScroll(zoneScrollState)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                zones.forEachIndexed { index, zone ->
                    FocusItem(
                        id = focusIds[index],
                        isEnabled = zoneSelectionEnabled && zone.enabled,
                        onClick = { onAreaSelected(zone.areaId) },
                        semantics =
                            FocusItemSemantics(
                                label = zone.label,
                                stateDescription = zone.contentDescription,
                                role = FocusItemRole.Button,
                            ),
                    ) { _ ->
                        BChip(
                            checked = zone.areaId == selectedAreaId,
                            onCheckedChange = { if (zoneSelectionEnabled && zone.enabled) onAreaSelected(zone.areaId) },
                            enabled = zoneSelectionEnabled && zone.enabled,
                            style = BChipStyle.Outlined,
                            size = BChipSize.Lg,
                        ) {
                            Text(zone.label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun VehicleRotaryControlRow(
    focusId: FocusItemId,
    infoFocusId: FocusItemId,
    control: VehicleControlUiModel,
    connected: Boolean,
    restricted: Boolean,
    restrictedReason: String,
    onFocused: () -> Unit,
    onOpenInfo: () -> Unit,
    onSetBoolean: (String, Int, Boolean) -> Unit,
    onSetInt: (String, Int, Int) -> Unit,
    onSetFloat: ((String, Int, Float) -> Unit)?,
    showInfo: Boolean = true,
    showDivider: Boolean,
) {
    val disconnectedReason = stringResource(R.string.vehicle_control_service_unavailable)
    val interaction =
        remember(control, connected, restricted) {
            buildVehicleControlInteraction(
                control = control,
                connected = connected,
                restricted = restricted,
            )
        }
    val canWrite = interaction.canWrite
    val uxBlocked = restricted && control.requiresUnrestrictedUx
    var directSliderValue by
        remember(control.key, control.areaId, control.range) {
            mutableFloatStateOf(control.numericValue ?: control.range.start)
        }
    var submittedDirectSliderValue by
        remember(control.key, control.areaId, control.range) { mutableStateOf<Float?>(null) }
    val rotaryWriteScope = rememberCoroutineScope()
    var rotaryWriteJob by remember(control.key, control.areaId, control.range) { mutableStateOf<Job?>(null) }
    DisposableEffect(control.key, control.areaId, control.range) {
        onDispose { rotaryWriteJob?.cancel() }
    }
    LaunchedEffect(
        control.numericValue,
        control.range,
        control.pending,
        control.errorMessage,
        submittedDirectSliderValue,
    ) {
        val sync =
            resolveVehicleSliderValue(
                externalValue = control.numericValue ?: control.range.start,
                currentValue = directSliderValue,
                interactionActive = false,
                submittedValue = submittedDirectSliderValue,
                pending = control.pending,
                hasError = control.errorMessage != null,
            )
        directSliderValue = sync.value
        if (sync.clearSubmittedValue) {
            submittedDirectSliderValue = null
        }
    }
    val sliderStep = remember(control.range, control.steps) { vehicleSliderStep(control.range, control.steps) }
    val directManipulation =
        if (
            control.editor == VehicleEditorUiKind.SLIDER &&
            canWrite &&
            control.numericValue != null
        ) {
            remember(control.key, control.areaId, control.range, sliderStep, onSetFloat) {
                DirectManipulationConfig(
                    onRotary = { event ->
                        directSliderValue =
                            nextVehicleSliderValue(
                                currentValue = directSliderValue,
                                detents = event.detents,
                                step = sliderStep,
                                valueRange = control.range,
                            )
                        submittedDirectSliderValue = directSliderValue
                        // Keep the direct-manipulation thumb synchronous, but coalesce a burst of
                        // detents into one VHAL write. The controller remains the authoritative
                        // latest-wins boundary if a write is already in flight.
                        rotaryWriteJob?.cancel()
                        rotaryWriteJob =
                            rotaryWriteScope.launch {
                                delay(VEHICLE_ROTARY_WRITE_COALESCE_MILLIS)
                                val requested = directSliderValue
                                if (control.usesFloatSlider && onSetFloat != null) {
                                    onSetFloat(control.key, control.areaId, requested)
                                } else {
                                    onSetInt(control.key, control.areaId, requested.roundToInt())
                                }
                            }
                    },
                )
            }
        } else {
            null
        }
    val centerAction = {
        when (val action = interaction.centerAction) {
            is VehicleCenterAction.Toggle -> onSetBoolean(control.key, control.areaId, action.value)
            is VehicleCenterAction.SelectEnum -> onSetInt(control.key, control.areaId, action.value)
            VehicleCenterAction.OpenInfo -> onOpenInfo()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusItem(
            id = focusId,
            modifier = Modifier.weight(1f),
            isEnabled = canWrite,
            onClick = centerAction,
            directManipulation = directManipulation,
            touchBehavior = FocusItemTouchBehavior.ComposeContent,
            layout = FocusItemLayout(fillCrossAxis = false, minHeight = 80.dp),
            semantics =
                FocusItemSemantics(
                    label = control.title,
                    stateDescription =
                        vehicleControlStateDescription(
                            control,
                            disconnectedReason,
                            uxBlocked,
                            restrictedReason,
                        ),
                    role = interaction.focusRole,
                ),
        ) { state ->
            LaunchedEffect(state.isFocused) {
                if (state.isFocused) onFocused()
            }
            val targetColor =
                when {
                    state.isDirectManipulationMode -> MaterialTheme.colorScheme.tertiaryContainer
                    // Focus is communicated via the rotary border ring provided by the library
                    // FocusItem wrapper; filling the container with primaryContainer creates a
                    // harsh dual signal. Transparent keeps the visual hierarchy clean.
                    else -> Color.Transparent
                }
            val rowColor by
                animateColorAsState(
                    targetColor,
                    tween(VehicleMotionTokens.FOCUS_DURATION_MILLIS),
                    label = "vehicle-row-focus",
                )
            Surface(color = rowColor, shape = MaterialTheme.shapes.large) {
                VehicleFeatureControlRow(
                    control =
                        if (control.editor == VehicleEditorUiKind.SLIDER) {
                            control.copy(numericValue = directSliderValue)
                        } else {
                            control
                        },
                    connected = connected,
                    restricted = restricted,
                    restrictedReason = restrictedReason,
                    disconnectedReason = disconnectedReason,
                    onOpenInfo = onOpenInfo,
                    onSetBoolean = onSetBoolean,
                    onSetInt = onSetInt,
                    onSetFloat = onSetFloat,
                    showInfo = false,
                    showDivider = false,
                )
            }
        }
        if (showInfo) {
            VehicleInfoFocusItem(
                focusId = infoFocusId,
                title = control.title,
                onClick = onOpenInfo,
            )
        }
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
@Suppress("CyclomaticComplexMethod")
internal fun VehicleFeatureControlRow(
    control: VehicleControlUiModel,
    connected: Boolean,
    restricted: Boolean,
    restrictedReason: String,
    disconnectedReason: String,
    onOpenInfo: () -> Unit,
    onSetBoolean: (String, Int, Boolean) -> Unit,
    onSetInt: (String, Int, Int) -> Unit,
    onSetFloat: ((String, Int, Float) -> Unit)?,
    showInfo: Boolean = true,
    showDivider: Boolean,
) {
    var sliderValue by
        remember(control.key, control.areaId, control.range) {
            mutableFloatStateOf(control.numericValue ?: control.range.start)
        }
    var sliderInteractionActive by remember(control.key, control.areaId, control.range) { mutableStateOf(false) }
    var submittedSliderValue by remember(control.key, control.areaId, control.range) { mutableStateOf<Float?>(null) }
    LaunchedEffect(
        control.numericValue,
        control.range,
        control.pending,
        control.errorMessage,
        submittedSliderValue,
    ) {
        val externalValue = control.numericValue ?: control.range.start
        val sync =
            resolveVehicleSliderValue(
                externalValue = externalValue,
                currentValue = sliderValue,
                interactionActive = sliderInteractionActive,
                submittedValue = submittedSliderValue,
                pending = control.pending,
                hasError = control.errorMessage != null,
            )
        sliderValue = sync.value
        if (sync.clearSubmittedValue) {
            submittedSliderValue = null
        }
    }
    val uxBlocked = restricted && control.requiresUnrestrictedUx
    val enabled = connected && control.supported && control.available && !uxBlocked
    val infoContent: (@Composable () -> Unit)? =
        if (showInfo) {
            {
                VehicleInfoButton(
                    title = control.title,
                    body = control.info,
                    limitations = control.limitations,
                    dependencies = control.dependencies,
                    onOpenInfo = onOpenInfo,
                )
            }
        } else {
            null
        }
    val disabledReason =
        when {
            !connected -> disconnectedReason
            !control.supported -> stringResource(R.string.vehicle_control_unavailable)
            uxBlocked -> restrictedReason
            else -> control.errorMessage
        }
    when (control.editor) {
        VehicleEditorUiKind.SWITCH ->
            VehicleSwitchRow(
                title = control.title,
                summary = control.summary,
                checked = control.booleanValue ?: false,
                onCheckedChange = { onSetBoolean(control.key, control.areaId, it) },
                enabled = enabled && control.booleanValue != null,
                readOnly = !control.writable,
                disabledReason = disabledReason,
                infoContent = infoContent,
                showDivider = showDivider,
            )
        VehicleEditorUiKind.SLIDER ->
            VehicleSliderRow(
                title = control.title,
                summary = control.summary,
                value = sliderValue,
                valueLabel =
                    if (sliderInteractionActive || submittedSliderValue != null) {
                        if (control.usesFloatSlider) {
                            "%.1f".format(Locale.US, sliderValue)
                        } else {
                            sliderValue.roundToInt().toString()
                        }
                    } else {
                        control.valueLabel
                    },
                onValueChange = {
                    sliderInteractionActive = true
                    sliderValue = it
                },
                onValueChangeFinished = {
                    // Publish the target before ending the local drag.  CarService can emit the
                    // previous VHAL value in the same frame; keeping the submitted value first
                    // prevents BSlider from repainting the old thumb once during that race.
                    val submitted = sliderValue
                    submittedSliderValue = submitted
                    sliderInteractionActive = false
                    if (control.usesFloatSlider && onSetFloat != null) {
                        onSetFloat(control.key, control.areaId, submitted)
                    } else {
                        onSetInt(control.key, control.areaId, submitted.roundToInt())
                    }
                },
                valueRange = control.range,
                steps = control.steps,
                uiSpec = control.sliderUiSpec,
                enabled = enabled && control.numericValue != null,
                readOnly = !control.writable,
                disabledReason = disabledReason,
                infoContent = infoContent,
                showDivider = showDivider,
            )
        VehicleEditorUiKind.ENUM ->
            VehicleEnumRow(
                title = control.title,
                summary = control.summary,
                selectedKey = control.selectedEnumKey,
                options = control.enumOptions,
                onOptionSelected = { option ->
                    option.key.toIntOrNull()?.let { onSetInt(control.key, control.areaId, it) }
                },
                enabled = enabled && control.enumOptions.isNotEmpty(),
                readOnly = !control.writable,
                disabledReason = disabledReason,
                infoContent = infoContent,
                unavailableLabel = control.valueLabel.ifBlank { stringResource(R.string.vehicle_control_unavailable) },
                showDivider = showDivider,
            )
        VehicleEditorUiKind.STATUS ->
            VehicleStatusRow(
                title = control.title,
                summary = control.summary,
                value = control.valueLabel,
                available = control.available,
                unavailableReason = disabledReason,
                infoContent = infoContent,
                showDivider = showDivider,
            )
    }
}

@Composable
internal fun vehicleControlStatusLabel(control: VehicleControlUiModel): String =
    when {
        !control.supported || !control.available -> stringResource(R.string.vehicle_control_unavailable)
        control.valueLabel.isNotBlank() -> control.valueLabel
        control.booleanValue == true -> stringResource(R.string.vehicle_control_on)
        control.booleanValue == false -> stringResource(R.string.vehicle_control_off)
        else -> control.summary
    }

@Composable
internal fun vehicleControlStateDescription(
    control: VehicleControlUiModel,
    disconnectedReason: String,
    uxBlocked: Boolean,
    restrictedReason: String,
): String =
    when {
        !control.supported -> stringResource(R.string.vehicle_control_unavailable)
        control.errorMessage != null -> control.errorMessage
        !control.available -> stringResource(R.string.vehicle_control_unavailable)
        uxBlocked -> restrictedReason
        !control.readable -> disconnectedReason
        else -> {
            val valueDescription =
                when {
                    control.valueLabel.isNotBlank() -> control.valueLabel
                    control.booleanValue == true -> stringResource(R.string.vehicle_control_on)
                    control.booleanValue == false -> stringResource(R.string.vehicle_control_off)
                    else -> control.summary
                }
            stableVehicleValueStateDescription(valueDescription)
        }
    }

internal fun List<VehicleZoneOption>.toTopViewZones(): List<VehicleTopViewZone> {
    val nonGlobal = filter { it.areaId != 0 }
    return nonGlobal.mapIndexed { index, zone ->
        val row = index / 2
        val left = index % 2 == 0
        VehicleTopViewZone(
            areaId = zone.areaId,
            label = zone.label,
            shortLabel = zone.label.take(2),
            contentDescription = zone.contentDescription,
            horizontalFraction = if (left) 0.42f else 0.58f,
            verticalFraction = (0.37f + row * 0.15f).coerceAtMost(0.76f),
            enabled = zone.enabled,
        )
    }
}
