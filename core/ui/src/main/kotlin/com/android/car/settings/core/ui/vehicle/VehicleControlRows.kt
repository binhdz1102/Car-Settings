package com.android.car.settings.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.uicomponents.bswitch.BSwitch
import com.b231001.bmaterial.uicomponents.chip.BChip
import com.b231001.bmaterial.uicomponents.chip.BChipSize
import com.b231001.bmaterial.uicomponents.chip.BChipStyle
import com.b231001.bmaterial.uicomponents.listitem.BListItem
import com.b231001.bmaterial.uicomponents.listitem.BListItemSize
import com.b231001.bmaterial.uicomponents.slider.BSlider
import com.b231001.bmaterial.uicomponents.slider.BSliderDefaults
import com.b231001.bmaterial.uicomponents.slider.BSliderSize
import com.b231001.bmaterial.uicomponents.slider.BSliderStyle

/** Boolean vehicle editor with one logical switch semantic on the complete row. */
@Composable
fun VehicleSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    pending: Boolean = false,
    disabledReason: String? = null,
    pendingDescription: String = stringResource(R.string.vehicle_control_pending),
    readOnlyDescription: String = stringResource(R.string.vehicle_control_read_only),
    onLabel: String = stringResource(R.string.vehicle_control_on),
    offLabel: String = stringResource(R.string.vehicle_control_off),
    leadingContent: @Composable (() -> Unit)? = null,
    infoContent: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val interactive = enabled && !readOnly
    val valueDescription = if (checked) onLabel else offLabel
    val annotation =
        vehicleControlAnnotation(
            enabled = enabled,
            readOnly = readOnly,
            pending = pending,
            disabledReason = disabledReason,
            pendingDescription = pendingDescription,
            readOnlyDescription = readOnlyDescription,
        )
    val interactionModifier =
        if (readOnly) {
            Modifier.semantics {
                stateDescription = valueDescription
                if (!enabled) disabled()
            }
        } else {
            Modifier
                .toggleable(
                    value = checked,
                    enabled = interactive,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ).semantics {
                    stateDescription = vehiclePendingStateDescription(valueDescription, pending, pendingDescription)
                }
        }

    BListItem(
        headline = title,
        supporting = vehicleSupportingText(summary = summary, annotation = annotation),
        leading = leadingContent,
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                infoContent?.invoke()
                if (pending) VehiclePendingIndicator(contentDescription = pendingDescription)
                when {
                    readOnly -> {
                        Text(
                            text = valueDescription,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    else -> {
                        BSwitch(
                            checked = checked,
                            enabled = interactive,
                            onCheckedChange = onCheckedChange,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            }
        },
        size = BListItemSize.Large,
        modifier =
            modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 72.dp)
                .then(interactionModifier),
    )
    if (showDivider) HorizontalDivider()
}

/** Numeric vehicle editor with explicit value text and a non-editable read-only rendering. */
@Composable
fun VehicleSliderRow(
    title: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    uiSpec: VehicleSliderUiSpec = VehicleSliderUiSpec(),
    summary: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    pending: Boolean = false,
    disabledReason: String? = null,
    pendingDescription: String = stringResource(R.string.vehicle_control_pending),
    readOnlyDescription: String = stringResource(R.string.vehicle_control_read_only),
    onValueChangeFinished: (() -> Unit)? = null,
    infoContent: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val validRange =
        valueRange.start.isFinite() &&
            valueRange.endInclusive.isFinite() &&
            valueRange.endInclusive > valueRange.start
    val effectiveEnabled = enabled && validRange
    val annotation =
        vehicleControlAnnotation(
            enabled = effectiveEnabled,
            readOnly = readOnly,
            pending = pending,
            disabledReason = disabledReason,
            pendingDescription = pendingDescription,
            readOnlyDescription = readOnlyDescription,
        )
    val clampedValue =
        if (validRange && value.isFinite()) {
            value.coerceIn(valueRange.start, valueRange.endInclusive)
        } else {
            valueRange.start
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 88.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .semantics {
                    stateDescription = vehiclePendingStateDescription(valueLabel, pending, pendingDescription)
                    if (!effectiveEnabled) disabled()
                },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            infoContent?.invoke()
            if (pending) {
                VehiclePendingIndicator(contentDescription = pendingDescription)
            }
        }
        VehicleSupportingText(summary = summary, annotation = annotation)
        if (validRange) {
            val sliderInteractive = effectiveEnabled && !readOnly
            val normalizedValue =
                ((clampedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start))
                    .coerceIn(0f, 1f)
            val sliderColor =
                when (uiSpec.kind) {
                    VehicleSliderUiKind.THERMAL ->
                        lerp(Color(0xFF1586B8), Color(0xFFF28C28), normalizedValue)
                    VehicleSliderUiKind.LEVEL -> MaterialTheme.colorScheme.primary
                    VehicleSliderUiKind.POSITION -> MaterialTheme.colorScheme.secondary
                    VehicleSliderUiKind.OFFSET -> MaterialTheme.colorScheme.tertiary
                }
            val sliderStyle =
                when (uiSpec.kind) {
                    VehicleSliderUiKind.THERMAL -> BSliderStyle.Warning
                    VehicleSliderUiKind.LEVEL -> BSliderStyle.Primary
                    VehicleSliderUiKind.POSITION -> BSliderStyle.Info
                    VehicleSliderUiKind.OFFSET -> BSliderStyle.Success
                }
            val sliderColors =
                BSliderDefaults.colors(sliderStyle).copy(
                    thumb = sliderColor,
                    trackActive = sliderColor,
                )
            val endpointIcons = vehicleSliderEndpointIcons(uiSpec.kind)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VehicleSliderEndpoint(
                    label = uiSpec.startLabel,
                    icon = endpointIcons.first,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.weight(1f).height(56.dp)) {
                    val markerFraction = vehicleSliderCenterFraction(uiSpec.centerMarker, valueRange)
                    if (markerFraction != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawVehicleSliderCenterMarker(
                                markerFraction = markerFraction,
                                color = sliderColor,
                            )
                        }
                    }
                    BSlider(
                        value = clampedValue,
                        onValueChange = if (readOnly) ({}) else onValueChange,
                        onValueChangeFinished = if (readOnly) null else onValueChangeFinished,
                        enabled = sliderInteractive,
                        valueRange = valueRange,
                        steps = steps.coerceAtLeast(0),
                        limitMin = valueRange.start,
                        limitMax = valueRange.endInclusive,
                        showTickMarks = vehicleSliderShowsTicks(uiSpec, steps),
                        showValueLabel = uiSpec.showValueLabel,
                        style = sliderStyle,
                        size = BSliderSize.Lg,
                        colors = sliderColors,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .sizeIn(minHeight = 48.dp)
                                .semantics {
                                    progressBarRangeInfo =
                                        ProgressBarRangeInfo(
                                            current = clampedValue,
                                            range = valueRange,
                                            steps = steps.coerceAtLeast(0),
                                        )
                                    if (!sliderInteractive && !readOnly) disabled()
                                    if (sliderInteractive) {
                                        setProgress { requestedValue ->
                                            onValueChange(
                                                requestedValue.coerceIn(
                                                    valueRange.start,
                                                    valueRange.endInclusive,
                                                ),
                                            )
                                            onValueChangeFinished?.invoke()
                                            true
                                        }
                                    }
                                },
                    )
                }
                VehicleSliderEndpoint(
                    label = uiSpec.endLabel,
                    icon = endpointIcons.second,
                    tint = sliderColor,
                )
            }
        }
    }
    if (showDivider) HorizontalDivider()
}

@Composable
private fun VehicleSliderEndpoint(
    label: String?,
    icon: ImageVector,
    tint: Color,
) {
    if (label.isNullOrBlank()) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
    } else {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

private fun vehicleSliderEndpointIcons(kind: VehicleSliderUiKind): Pair<ImageVector, ImageVector> =
    when (kind) {
        VehicleSliderUiKind.THERMAL -> Icons.Outlined.AcUnit to Icons.Outlined.LocalFireDepartment
        VehicleSliderUiKind.LEVEL -> Icons.Outlined.Remove to Icons.Outlined.Add
        VehicleSliderUiKind.POSITION ->
            Icons.AutoMirrored.Outlined.ArrowBack to Icons.AutoMirrored.Outlined.ArrowForward
        VehicleSliderUiKind.OFFSET -> Icons.Outlined.Remove to Icons.Outlined.Add
    }

private fun DrawScope.drawVehicleSliderCenterMarker(
    markerFraction: Float,
    color: Color,
) {
    val x = size.width * markerFraction
    drawLine(
        color = color.copy(alpha = 0.55f),
        start = Offset(x, size.height * 0.22f),
        end = Offset(x, size.height * 0.78f),
        strokeWidth = 2.dp.toPx(),
    )
}

/** Enum editor backed only by a presentation key and labels supplied by the caller. */
@Composable
fun VehicleEnumRow(
    title: String,
    selectedKey: String?,
    options: List<VehicleEnumOption>,
    onOptionSelected: (VehicleEnumOption) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    pending: Boolean = false,
    disabledReason: String? = null,
    unavailableLabel: String = stringResource(R.string.vehicle_control_unavailable),
    pendingDescription: String = stringResource(R.string.vehicle_control_pending),
    readOnlyDescription: String = stringResource(R.string.vehicle_control_read_only),
    leadingContent: @Composable (() -> Unit)? = null,
    infoContent: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options.firstOrNull { it.key == selectedKey }
    val selectedLabel = selectedOption?.label ?: unavailableLabel
    val selectedDescription = selectedOption?.contentDescription ?: unavailableLabel
    val interactive = enabled && !readOnly && options.any { it.enabled }
    val annotation =
        vehicleControlAnnotation(
            enabled = enabled,
            readOnly = readOnly,
            pending = pending,
            disabledReason = disabledReason,
            pendingDescription = pendingDescription,
            readOnlyDescription = readOnlyDescription,
        )

    LaunchedEffect(interactive) {
        if (!interactive) expanded = false
    }

    Box(modifier = modifier.fillMaxWidth()) {
        val rowModifier =
            if (readOnly) {
                Modifier.semantics {
                    stateDescription = selectedDescription
                    if (!enabled) disabled()
                }
            } else {
                Modifier
                    .clickable(
                        enabled = interactive,
                        role = Role.Button,
                        onClick = { expanded = true },
                    ).semantics {
                        stateDescription =
                            vehiclePendingStateDescription(
                                selectedDescription,
                                pending,
                                pendingDescription,
                            )
                    }
            }
        BListItem(
            headline = title,
            supporting = vehicleSupportingText(summary = summary, annotation = annotation),
            leading = leadingContent,
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = selectedLabel, style = MaterialTheme.typography.labelLarge)
                    infoContent?.invoke()
                    if (pending) VehiclePendingIndicator(contentDescription = pendingDescription)
                    if (!readOnly) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                        )
                    }
                }
            },
            size = BListItemSize.Large,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 72.dp)
                    .then(rowModifier),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.label) },
                    enabled = option.enabled,
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    },
                    modifier =
                        Modifier
                            .sizeIn(minHeight = 48.dp)
                            .semantics {
                                contentDescription = option.contentDescription
                                stateDescription = option.label
                                selected = option.key == selectedKey
                            },
                )
            }
        }
    }
    if (showDivider) HorizontalDivider()
}

/** Read-only key/value row for vehicle telemetry and read-only properties. */
@Composable
fun VehicleStatusRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    available: Boolean = true,
    pending: Boolean = false,
    unavailableReason: String? = null,
    pendingDescription: String = stringResource(R.string.vehicle_control_pending),
    statusDescription: String = value,
    tone: VehicleStatusTone = VehicleStatusTone.Neutral,
    leadingContent: @Composable (() -> Unit)? = null,
    infoContent: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val annotation =
        when {
            pending -> pendingDescription
            !available -> unavailableReason ?: stringResource(R.string.vehicle_control_unavailable)
            else -> null
        }
    BListItem(
        headline = title,
        supporting = vehicleSupportingText(summary = summary, annotation = annotation),
        leading = leadingContent,
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        vehicleStatusColor(
                            if (available) tone else VehicleStatusTone.Unavailable,
                        ),
                )
                if (pending) VehiclePendingIndicator(contentDescription = pendingDescription)
                infoContent?.invoke()
            }
        },
        size = BListItemSize.Large,
        modifier =
            modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 72.dp)
                .semantics {
                    stateDescription =
                        when {
                            pending -> vehiclePendingStateDescription(statusDescription, true, pendingDescription)
                            !available -> annotation.orEmpty()
                            else -> statusDescription
                        }
                    if (!available) disabled()
                },
    )
    if (showDivider) HorizontalDivider()
}

/** Area selector that preserves the independent area ID chosen by the feature. */
@Composable
fun VehicleZoneSelector(
    label: String,
    zones: List<VehicleZoneOption>,
    selectedAreaId: Int?,
    onZoneSelected: (VehicleZoneOption) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    pending: Boolean = false,
    disabledReason: String? = null,
    contentDescription: String =
        stringResource(R.string.vehicle_zone_selector_content_description),
    unavailableLabel: String = stringResource(R.string.vehicle_control_unavailable),
    pendingDescription: String = stringResource(R.string.vehicle_control_pending),
    readOnlyDescription: String = stringResource(R.string.vehicle_control_read_only),
) {
    val selectedZone = zones.firstOrNull { it.areaId == selectedAreaId }
    val annotation =
        vehicleControlAnnotation(
            enabled = enabled,
            readOnly = readOnly,
            pending = pending,
            disabledReason = disabledReason,
            pendingDescription = pendingDescription,
            readOnlyDescription = readOnlyDescription,
        )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (pending) VehiclePendingIndicator(contentDescription = pendingDescription)
        }
        VehicleSupportingText(summary = null, annotation = annotation)
        when {
            zones.isEmpty() -> {
                Text(
                    text = unavailableLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { stateDescription = unavailableLabel },
                )
            }
            readOnly -> {
                Text(
                    text = selectedZone?.label ?: unavailableLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier.semantics {
                            stateDescription = selectedZone?.contentDescription ?: unavailableLabel
                            if (!enabled) disabled()
                        },
                )
            }
            else -> {
                val zoneScrollState = rememberScrollState()
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .vehicleBScrollbar(
                                scrollState = zoneScrollState,
                                orientation = Orientation.Horizontal,
                            ).horizontalScroll(zoneScrollState)
                            .selectableGroup()
                            .semantics { this.contentDescription = contentDescription },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    zones.forEach { zone ->
                        val selected = zone.areaId == selectedAreaId
                        BChip(
                            checked = selected,
                            enabled = enabled && zone.enabled,
                            onCheckedChange = { onZoneSelected(zone) },
                            style = BChipStyle.Outlined,
                            size = BChipSize.Lg,
                            modifier =
                                Modifier
                                    .sizeIn(minHeight = 48.dp)
                                    .semantics {
                                        this.contentDescription = zone.contentDescription
                                        this.selected = selected
                                    },
                        ) {
                            Text(text = zone.label)
                        }
                    }
                }
            }
        }
    }
}

private fun vehicleSupportingText(
    summary: String?,
    annotation: String?,
): String? = listOfNotNull(summary, annotation).takeIf { it.isNotEmpty() }?.joinToString("\n")

private fun vehiclePendingStateDescription(
    valueDescription: String,
    pending: Boolean,
    pendingDescription: String,
): String = if (pending) "$valueDescription, $pendingDescription" else valueDescription

@Composable
private fun VehicleSupportingText(
    summary: String?,
    annotation: String?,
) {
    if (summary != null || annotation != null) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (annotation != null) {
                Text(
                    text = annotation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun vehicleControlAnnotation(
    enabled: Boolean,
    readOnly: Boolean,
    pending: Boolean,
    disabledReason: String?,
    pendingDescription: String,
    readOnlyDescription: String,
): String? =
    when {
        pending -> pendingDescription
        !enabled -> disabledReason ?: stringResource(R.string.vehicle_control_unavailable)
        readOnly -> readOnlyDescription
        else -> null
    }

@Composable
private fun vehicleStatusColor(tone: VehicleStatusTone): Color =
    when (tone) {
        VehicleStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        VehicleStatusTone.Positive -> MaterialTheme.colorScheme.primary
        VehicleStatusTone.Warning -> MaterialTheme.colorScheme.tertiary
        VehicleStatusTone.Error -> MaterialTheme.colorScheme.error
        VehicleStatusTone.Unavailable -> MaterialTheme.colorScheme.outline
    }
