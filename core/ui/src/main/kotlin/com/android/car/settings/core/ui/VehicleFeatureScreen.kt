package com.android.car.settings.core.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.LocalIsInTouchMode
import com.b231001.bmaterial.ccp.rotaryfocus.LocalRotaryFocusController
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusTarget
import kotlinx.coroutines.delay

/**
 * Capability-driven vehicle UI with overview, category, and bounded illustrated-guide popup.
 * Unsupported controls remain discoverable as static, accessible content. Rotary focus is reserved
 * for actions only; unavailable controls expose their reason without creating a dead focus stop.
 */
@Composable
@Suppress("LongMethod")
fun VehicleFeatureScreen(
    title: String,
    @DrawableRes titleIconRes: Int? = null,
    /** Stable navigation route used by the activity's one-shot CCP handoff. */
    destinationKey: String? = null,
    loading: Boolean,
    connected: Boolean,
    restricted: Boolean,
    controls: List<VehicleControlUiModel>,
    zones: List<VehicleZoneOption>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetBoolean: (String, Int, Boolean) -> Unit,
    onSetInt: (String, Int, Int) -> Unit,
    onSetFloat: ((String, Int, Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    zoneSelectorLabel: String = "Vehicle zone",
    connectingMessage: String = "Connecting to vehicle service…",
    emptyMessage: String = "No supported controls are exposed by this vehicle.",
    restrictedReason: String = "Unavailable while driving",
    showVehicleDiagram: Boolean = false,
    visualizationSource: VehicleVisualizationSource? = null,
    visualizationLabel: String? = null,
    visualization: (@Composable (VehicleControlUiModel?, VehicleVisualPolicy) -> Unit)? = null,
    guideVisualization: (@Composable (VehicleControlUiModel, Float) -> Unit)? = null,
    visualPolicy: VehicleVisualPolicy =
        VehicleVisualPolicy(
            allowPreviewTransition = false,
            allowGuide = false,
            allowGuidePlayback = false,
        ),
) {
    if (isRotaryFallbackMode()) {
        VehicleFeatureHostlessFallback(
            title = title,
            titleIconRes = titleIconRes,
            loading = loading,
            connected = connected,
            restricted = restricted,
            controls = controls,
            zones = zones,
            onBack = onBack,
            onRefresh = onRefresh,
            onSetBoolean = onSetBoolean,
            onSetInt = onSetInt,
            onSetFloat = onSetFloat,
            modifier = modifier,
            zoneSelectorLabel = zoneSelectorLabel,
            connectingMessage = connectingMessage,
            emptyMessage = emptyMessage,
            restrictedReason = restrictedReason,
            showVehicleDiagram = showVehicleDiagram,
            visualizationSource = visualizationSource,
            visualizationLabel = visualizationLabel,
            visualization = visualization,
            guideVisualization = guideVisualization,
            visualPolicy = visualPolicy,
        )
        return
    }
    var selectedAreaId by rememberSaveable(zones) {
        mutableIntStateOf(zones.firstOrNull { it.areaId != 0 }?.areaId ?: 0)
    }
    var showAllControls by rememberSaveable(title) { mutableStateOf(false) }
    var selectedCategoryKey by rememberSaveable(title) { mutableStateOf<String?>(null) }
    var selectedInfoKey by rememberSaveable(title) { mutableStateOf<String?>(null) }
    var selectedInfoAreaId by rememberSaveable(title) { mutableIntStateOf(0) }
    var suppressParentBackAfterInfoDismiss by remember { mutableStateOf(false) }
    val overviewScrollState = rememberSaveable(title, saver = ScrollState.Saver) { ScrollState(initial = 0) }

    val activeZones =
        remember(zones, selectedCategoryKey) {
            vehicleZonesForCategory(zones, selectedCategoryKey)
        }
    val effectiveSelectedAreaId =
        remember(activeZones, selectedAreaId) {
            effectiveVehicleAreaId(activeZones, selectedAreaId)
        }
    LaunchedEffect(activeZones) {
        val fallbackAreaId = activeZones.firstOrNull { it.areaId != 0 }?.areaId ?: 0
        if (activeZones.none { it.areaId == selectedAreaId } && selectedAreaId != fallbackAreaId) {
            selectedAreaId = fallbackAreaId
        }
    }

    val controlsForDisplay =
        remember(controls, showAllControls) {
            visibleVehicleControls(controls, showAllControls)
        }
    val overviewPresentation =
        remember(title, controls, controlsForDisplay) {
            buildVehicleOverviewPresentation(
                title = title,
                controls = controls,
                controlsForDisplay = controlsForDisplay,
            )
        }
    val rotaryDestinationKey =
        destinationKey?.takeIf { it.isNotBlank() }
            ?: "vehicle-overview-${vehicleFocusKey(title)}"
    val pendingFocusEntry = LocalSettingsFocusEntryState.current?.pendingRequest
    val ownsFocusHandoff =
        pendingFocusEntry?.let { request -> settingsFocusEntryBelongsTo(request, rotaryDestinationKey) } == true
    val categoryKeys = remember(overviewPresentation) { overviewPresentation.categories.map { it.key } }
    LaunchedEffect(categoryKeys) {
        if (selectedCategoryKey != null && selectedCategoryKey !in categoryKeys) {
            selectedCategoryKey = null
        }
    }
    LaunchedEffect(selectedCategoryKey) {
        if (selectedCategoryKey == null) {
            Log.i("MySystemVehicle", "vehicle-overview title=$title")
        } else {
            Log.i(
                "MySystemVehicle",
                "vehicle-category title=$title key=$selectedCategoryKey",
            )
        }
    }

    val selectedInfoControl =
        controls.firstOrNull {
            it.key == selectedInfoKey && it.areaId == selectedInfoAreaId
        }

    LaunchedEffect(visualPolicy.allowGuide, selectedInfoControl?.key, selectedInfoControl?.areaId) {
        if (selectedInfoControl != null && !visualPolicy.allowGuide) {
            selectedInfoKey = null
            suppressParentBackAfterInfoDismiss = true
        }
    }

    // The rotary dialog owns the Back event while it is visible.  Keeping the parent handler
    // from consuming the same hardware event after the dialog posts its dismiss callback is
    // important on AAOS: a dialog window can dispatch Back again when it is removed.
    LaunchedEffect(suppressParentBackAfterInfoDismiss) {
        if (suppressParentBackAfterInfoDismiss) {
            delay(500)
            suppressParentBackAfterInfoDismiss = false
        }
    }
    val rotaryController = LocalRotaryFocusController.current
    val isInTouchMode = LocalIsInTouchMode.current
    // Park before removing the category tree.  Waiting for RotaryDestination's onDispose is too
    // late: FocusItemView unregisters first, so the controller no longer knows which destination
    // owns the current target and the host parking view restores the shell Search item.
    val leaveCategoryToOverview: () -> Unit = {
        prepareVehicleDetailNavigation(
            isInTouchMode = isInTouchMode,
            parkFocus = { rotaryController?.parkFocus() == true },
            navigate = {
                Log.i(
                    "MySystemVehicle",
                    "vehicle-category-back title=$title category=$selectedCategoryKey",
                )
                Log.d("MySystemVehicle", "vehicle-back category-to-overview")
                selectedCategoryKey = null
            },
        )
    }
    // Keep this handler enabled for the short hand-off window after a rotary dialog closes or
    // while a control is in direct-manipulation mode.
    // Some AAOS builds dispatch the same hardware Back event again after removing the dialog
    // window; if the category state has already recomposed to null, a category-only handler is
    // disabled and NavHost would pop the whole activity to CarLauncher.
    BackHandler(
        enabled =
            vehicleBackEnabled(selectedCategoryKey, suppressParentBackAfterInfoDismiss) ||
                rotaryController?.isDirectManipulationActive == true,
    ) {
        if (rotaryController?.isDirectManipulationActive == true) {
            Log.d("MySystemVehicle", "vehicle-back consumed for direct manipulation exit")
            rotaryController.exitDirectManipulation()
            return@BackHandler
        }
        // The dialog owns its separate window. If a platform image re-dispatches the same Back
        // event after that window is removed, consume it here without collapsing the category.
        if (selectedInfoControl != null) {
            Log.d("MySystemVehicle", "vehicle-back ignored while info popup is visible")
        } else if (suppressParentBackAfterInfoDismiss) {
            Log.d("MySystemVehicle", "vehicle-back consumed after info popup dismiss")
            suppressParentBackAfterInfoDismiss = false
        } else {
            leaveCategoryToOverview()
        }
    }

    if (selectedInfoControl != null) {
        VehicleInfoGuideDialog(
            control = selectedInfoControl,
            visualPolicy = visualPolicy,
            guideVisualization = guideVisualization,
            onDismissRequest = {
                // RotaryFocusDialog owns native focus restoration for rotary dismissal. In touch
                // mode it intentionally leaves focus parked; requesting focus here would paint a
                // phantom border immediately after a finger tap.
                selectedInfoKey = null
                suppressParentBackAfterInfoDismiss = true
            },
        )
    }

    AutomotiveRouteMotion(
        routeKey = selectedCategoryKey ?: "overview",
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            selectedCategoryKey != null -> {
                val categoryControls =
                    remember(controlsForDisplay, selectedCategoryKey) {
                        controlsForDisplay.filter { it.categoryKey == selectedCategoryKey }
                    }
                val categoryTitle = categoryControls.firstOrNull()?.section ?: title
                val destinationKey = vehicleFocusKey("$title-$categoryTitle")
                val controlsAreaId = "vehicle-controls-$destinationKey"
                val selectedAreaFocusKey = vehicleFocusKey(effectiveSelectedAreaId.toString())
                val initialCategoryControl =
                    filterVehicleControlsForArea(categoryControls, effectiveSelectedAreaId)
                        .firstOrNull { isVehicleControlRotaryActionable(it, connected, restricted) }
                val categoryFallback =
                    initialCategoryControl?.let { control ->
                        RotaryFocusTarget(
                            FocusAreaId(controlsAreaId),
                            FocusItemId(
                                "$controlsAreaId-${vehicleFocusKey(control.key)}-${control.areaId}-" +
                                    "area-$selectedAreaFocusKey",
                            ),
                        )
                    }
                RotaryDestination(
                    destinationKey = "vehicle-category-${vehicleFocusKey(selectedCategoryKey.orEmpty())}",
                    fallback = categoryFallback,
                ) {
                    VehicleCategoryScreen(
                        featureTitle = title,
                        categoryTitle = categoryTitle,
                        titleIconRes = titleIconRes,
                        connected = connected,
                        restricted = restricted,
                        controls = categoryControls,
                        zones = activeZones,
                        selectedAreaId = effectiveSelectedAreaId,
                        onAreaSelected = { selectedAreaId = it },
                        onBack = leaveCategoryToOverview,
                        onRefresh = onRefresh,
                        onOpenInfo = { control ->
                            selectedInfoKey = control.key
                            selectedInfoAreaId = control.areaId
                        },
                        onSetBoolean = onSetBoolean,
                        onSetInt = onSetInt,
                        onSetFloat = onSetFloat,
                        modifier = modifier,
                        zoneSelectorLabel = zoneSelectorLabel,
                        restrictedReason = restrictedReason,
                        showVehicleDiagram = showVehicleDiagram,
                        visualizationSource = visualizationSource,
                        visualizationLabel = visualizationLabel,
                        visualization = visualization,
                        guideVisualization = guideVisualization,
                        visualPolicy = visualPolicy,
                    )
                }
            }
            else -> {
                val overviewAreaId = "vehicle-overview-${vehicleFocusKey(title)}"
                val overviewContentAreaId = "vehicle-overview-content-${vehicleFocusKey(overviewAreaId)}"
                val overviewFallback =
                    vehicleOverviewFallbackTarget(
                        presentation = overviewPresentation,
                        contentAreaId = overviewContentAreaId,
                        loading = loading,
                    )
                RotaryDestination(
                    destinationKey = rotaryDestinationKey,
                    // An explicit CCP handoff is the only owner of initial detail focus. Letting
                    // destination restoration win here can park focus in the shell Search item
                    // before VehicleSettingsScaffold has materialized the first control.
                    fallback = overviewFallback.takeUnless { ownsFocusHandoff },
                ) {
                    VehicleFeatureOverview(
                        title = title,
                        titleIconRes = titleIconRes,
                        destinationKey = rotaryDestinationKey,
                        contentFocusAreaId = overviewContentAreaId,
                        loading = loading,
                        connected = connected,
                        controls = controls,
                        presentation = overviewPresentation,
                        showAllControls = showAllControls,
                        onToggleShowAll = { showAllControls = !showAllControls },
                        onOpenCategory = { categoryKey ->
                            prepareVehicleDetailNavigation(
                                isInTouchMode = isInTouchMode,
                                parkFocus = { rotaryController?.parkFocus() == true },
                                navigate = { selectedCategoryKey = categoryKey },
                            )
                        },
                        onBack = onBack,
                        onRefresh = onRefresh,
                        modifier = modifier,
                        connectingMessage = connectingMessage,
                        emptyMessage = emptyMessage,
                        scrollState = overviewScrollState,
                    )
                }
            }
        }
    }
}
