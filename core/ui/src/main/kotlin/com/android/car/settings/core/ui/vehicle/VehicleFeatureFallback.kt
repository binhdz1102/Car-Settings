package com.android.car.settings.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.uicomponents.chip.BChip
import com.b231001.bmaterial.uicomponents.chip.BChipSize
import com.b231001.bmaterial.uicomponents.chip.BChipStyle

/**
 * Touch/accessibility fallback for OEM images that do not provide the B-Material rotary host.
 *
 * This presentation is deliberately isolated from the rotary destination implementation. It
 * keeps loading, capability fallback, scrolling, zone selection and the information popup in one
 * touch-safe surface without changing the domain controller or the normal focus tree.
 */
@Composable
@Suppress("LongMethod")
internal fun VehicleFeatureHostlessFallback(
    title: String,
    @DrawableRes titleIconRes: Int?,
    loading: Boolean,
    connected: Boolean,
    restricted: Boolean,
    controls: List<VehicleControlUiModel>,
    zones: List<VehicleZoneOption>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetBoolean: (String, Int, Boolean) -> Unit,
    onSetInt: (String, Int, Int) -> Unit,
    onSetFloat: ((String, Int, Float) -> Unit)?,
    modifier: Modifier,
    zoneSelectorLabel: String,
    connectingMessage: String,
    emptyMessage: String,
    restrictedReason: String,
    showVehicleDiagram: Boolean,
    visualizationSource: VehicleVisualizationSource?,
    visualizationLabel: String?,
    visualization: (@Composable (VehicleControlUiModel?) -> Unit)?,
) {
    val retryLabel = stringResource(R.string.vehicle_control_retry)
    val showAllLabel = stringResource(R.string.vehicle_control_show_all)
    val hideUnavailableLabel = stringResource(R.string.vehicle_control_hide_unavailable)
    val noSupportedMessage = stringResource(R.string.vehicle_control_no_supported_controls)
    val partialSupportMessage = stringResource(R.string.vehicle_control_partial_support)
    val disconnectedReason = stringResource(R.string.vehicle_control_service_unavailable)
    var selectedAreaId by rememberSaveable(zones) {
        mutableIntStateOf(zones.firstOrNull { it.areaId != 0 }?.areaId ?: 0)
    }
    var showAllControls by rememberSaveable { mutableStateOf(false) }
    var infoControl by remember { mutableStateOf<VehicleControlUiModel?>(null) }
    val hasUnavailableControls = controls.any { !it.supported }
    val controlsForDisplay =
        remember(controls, showAllControls) {
            visibleVehicleControls(controls, showAllControls)
        }
    val hasScopedZoneFamilies = remember(zones) { zones.any { it.propertyFamily.isNotBlank() } }
    val visibleControls =
        remember(controlsForDisplay, selectedAreaId, zones, hasScopedZoneFamilies) {
            if (hasScopedZoneFamilies) {
                // This fallback intentionally has no category navigation. Showing each scoped
                // control is safer than applying an area bit from one AOSP family to another.
                controlsForDisplay
            } else {
                filterVehicleControlsForArea(
                    controlsForDisplay,
                    effectiveVehicleAreaId(zones, selectedAreaId),
                )
            }
        }
    val firstError = remember(visibleControls) { visibleControls.firstNotNullOfOrNull { it.errorMessage } }
    val sectionGroups = remember(visibleControls) { visibleControls.groupBy { it.section } }
    val selectableZones =
        remember(zones, hasScopedZoneFamilies) {
            if (hasScopedZoneFamilies) emptyList() else zones.filter { it.areaId != 0 }
        }
    val topViewZones = remember(zones) { zones.toTopViewZones() }

    VehicleSettingsScaffold(
        title = title,
        titleIconRes = titleIconRes,
        onBack = onBack,
        destinationKey = "vehicle-fallback",
        modifier = modifier,
    ) { padding ->
        when {
            loading && controls.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text(connectingMessage, modifier = Modifier.padding(top = 16.dp))
                }
            }
            !connected && visibleControls.isEmpty() -> {
                RotaryUnavailableState(
                    areaId = "vehicle-fallback-unavailable",
                    title = connectingMessage,
                    message = disconnectedReason,
                    actionLabel = if (hasUnavailableControls) showAllLabel else retryLabel,
                    onAction =
                        if (hasUnavailableControls) {
                            { showAllControls = true }
                        } else {
                            onRefresh
                        },
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                )
            }
            visibleControls.isEmpty() -> {
                RotaryUnavailableState(
                    areaId = "vehicle-fallback-empty",
                    title = null,
                    message = if (hasUnavailableControls) noSupportedMessage else emptyMessage,
                    actionLabel = if (hasUnavailableControls) showAllLabel else retryLabel,
                    onAction =
                        if (hasUnavailableControls) {
                            { showAllControls = true }
                        } else {
                            onRefresh
                        },
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                )
            }
            else -> {
                val fallbackListState = rememberLazyListState()
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .vehicleBLazyScrollbar(fallbackListState),
                    state = fallbackListState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(24.dp),
                ) {
                    if (visualization != null) {
                        item(key = "vehicle-fallback-preview") {
                            VehicleFallbackPreviewItem(
                                control = visibleControls.firstOrNull(),
                                onOpenInfo = { infoControl = it },
                                visualizationSource = visualizationSource,
                                visualizationLabel = visualizationLabel,
                                visualization = visualization,
                            )
                        }
                    }
                    firstError?.let { message ->
                        item { VehicleErrorBanner(message = message, onRetry = onRefresh) }
                    }
                    if (hasUnavailableControls) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = partialSupportMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                BChip(
                                    checked = showAllControls,
                                    onCheckedChange = { showAllControls = !showAllControls },
                                    style = BChipStyle.Outlined,
                                    size = BChipSize.Lg,
                                ) {
                                    Text(
                                        if (showAllControls) hideUnavailableLabel else showAllLabel,
                                    )
                                }
                            }
                        }
                    }
                    if (selectableZones.size > 1) {
                        item {
                            VehicleControlSection(title = zoneSelectorLabel) {
                                if (showVehicleDiagram) {
                                    VehicleTopView(
                                        selectedAreaId = selectedAreaId,
                                        zones = topViewZones,
                                        onZoneSelected = { selectedAreaId = it.areaId },
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                                VehicleZoneSelector(
                                    label = zoneSelectorLabel,
                                    zones = selectableZones,
                                    selectedAreaId = selectedAreaId,
                                    onZoneSelected = { selectedAreaId = it.areaId },
                                )
                            }
                        }
                    }
                    sectionGroups.forEach { (section, sectionControls) ->
                        item(key = section) {
                            VehicleControlSection(title = section) {
                                sectionControls.forEachIndexed { index, control ->
                                    VehicleFeatureControlRow(
                                        control = control,
                                        connected = connected,
                                        restricted = restricted,
                                        restrictedReason = restrictedReason,
                                        disconnectedReason = disconnectedReason,
                                        onOpenInfo = { infoControl = control },
                                        onSetBoolean = onSetBoolean,
                                        onSetInt = onSetInt,
                                        onSetFloat = onSetFloat,
                                        showDivider = index != sectionControls.lastIndex,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    infoControl?.let { control ->
        VehicleInfoGuideDialog(
            control = control,
            onDismissRequest = { infoControl = null },
        )
    }
}

@Composable
private fun VehicleFallbackPreviewItem(
    control: VehicleControlUiModel?,
    onOpenInfo: (VehicleControlUiModel) -> Unit,
    visualizationSource: VehicleVisualizationSource?,
    visualizationLabel: String?,
    visualization: (@Composable (VehicleControlUiModel?) -> Unit)?,
) {
    VehiclePreviewPane(
        control = control,
        areaId = "vehicle-fallback-preview-area",
        nextAreaId = "vehicle-fallback-controls-area",
        onOpenInfo = onOpenInfo,
        visualizationSource = visualizationSource,
        visualizationLabel = visualizationLabel,
        visualization = visualization,
        modifier = Modifier.fillMaxWidth(),
    )
}
