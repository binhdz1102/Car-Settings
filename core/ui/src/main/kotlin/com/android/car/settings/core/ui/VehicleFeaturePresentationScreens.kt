package com.android.car.settings.core.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRole
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemSemantics
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemTouchBehavior
import com.b231001.bmaterial.ccp.rotaryfocus.LocalRotaryFocusController
import com.b231001.bmaterial.uicomponents.card.BCard
import com.b231001.bmaterial.uicomponents.card.BCardSize
import com.b231001.bmaterial.uicomponents.card.BCardStyle
import com.b231001.bmaterial.uicomponents.chip.BChip
import com.b231001.bmaterial.uicomponents.chip.BChipSize
import com.b231001.bmaterial.uicomponents.chip.BChipStyle

@Composable
internal fun VehicleFeatureOverview(
    title: String,
    @DrawableRes titleIconRes: Int?,
    destinationKey: String,
    contentFocusAreaId: String,
    loading: Boolean,
    connected: Boolean,
    controls: List<VehicleControlUiModel>,
    presentation: VehicleOverviewPresentation,
    showAllControls: Boolean,
    onToggleShowAll: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier,
    connectingMessage: String,
    emptyMessage: String,
    scrollState: ScrollState = rememberScrollState(),
) {
    val areaId = presentation.areaId
    val hasUnavailableControls = presentation.hasUnavailableControls
    val retryLabel = stringResource(R.string.vehicle_control_retry)
    val showAllLabel = stringResource(R.string.vehicle_control_show_all)
    val noSupportedMessage = stringResource(R.string.vehicle_control_no_supported_controls)
    val disconnectedReason = stringResource(R.string.vehicle_control_service_unavailable)
    val categories = presentation.categories
    val focusOrder = presentation.focusOrder
    val overviewContentArea = FocusAreaId(contentFocusAreaId)
    val firstContentFocusId =
        focusOrder.firstOrNull()
            ?: if (!loading) FocusItemId("$contentFocusAreaId-action") else null

    VehicleSettingsScaffold(
        title = title,
        titleIconRes = titleIconRes,
        onBack = onBack,
        destinationKey = destinationKey,
        firstContentFocusId = firstContentFocusId,
        isContentFocusReady = firstContentFocusId != null,
        modifier = modifier,
        // The overview is the shell's detail root. Reusing the shared area prevents a one-frame
        // overlap with the previous standard destination while NavHost swaps entries.
        contentFocusAreaIds = listOf(contentFocusAreaId),
        actions = {
            SettingsAppBarAction(
                id = "$areaId-refresh",
                contentDescription = retryLabel,
                onClick = onRefresh,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
            }
        },
    ) { padding ->
        when {
            loading && controls.isEmpty() -> {
                VehicleLoadingState(
                    message = connectingMessage,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
            categories.isEmpty() -> {
                val canShowAll = hasUnavailableControls && !showAllControls
                RotaryUnavailableState(
                    areaId = overviewContentArea.value,
                    title = if (!connected) connectingMessage else null,
                    message =
                        when {
                            !connected -> disconnectedReason
                            hasUnavailableControls -> noSupportedMessage
                            else -> emptyMessage
                        },
                    actionLabel = if (canShowAll) showAllLabel else retryLabel,
                    onAction = if (canShowAll) onToggleShowAll else onRefresh,
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                )
            }
            else -> {
                val firstFocusId = focusOrder.firstOrNull()
                FocusArea(
                    id = overviewContentArea,
                    firstFocusAt = firstFocusId,
                    focusOrder = focusOrder,
                    wrapAround = vehicleDetailFocusAreaPolicy(overviewContentArea).wrapAround,
                    previousFocusArea = vehicleDetailFocusAreaPolicy(overviewContentArea).previousFocusArea,
                    nextFocusArea = vehicleDetailFocusAreaPolicy(overviewContentArea).nextFocusArea,
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .automotiveScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(horizontal = 32.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.vehicle_control_categories_heading),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() },
                        )
                        presentation.errorMessage?.let { message ->
                            VehicleErrorBanner(
                                message = message,
                                onRetry = onRefresh,
                                retryFocusId = FocusItemId("$areaId-retry"),
                            )
                        }
                        if (hasUnavailableControls) {
                            VehicleShowAllCard(
                                focusId = FocusItemId("$areaId-show-all"),
                                showAll = showAllControls,
                                onClick = onToggleShowAll,
                            )
                        }
                        categories.forEach { category ->
                            VehicleCategoryCard(
                                focusId = FocusItemId("$areaId-${vehicleFocusKey(category.key)}"),
                                title = category.title,
                                controls = category.controls,
                                summaryControls = category.summaryControls,
                                onClick = { onOpenCategory(category.key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleLoadingState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(message, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
internal fun RotaryUnavailableState(
    areaId: String,
    title: String?,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedTitle = title ?: stringResource(R.string.vehicle_control_unavailable)
    val actionId = FocusItemId("$areaId-action")
    val focusAreaId = FocusAreaId(areaId)
    val focusPolicy = vehicleDetailFocusAreaPolicy(focusAreaId)
    FocusArea(
        id = focusAreaId,
        firstFocusAt = actionId,
        wrapAround = focusPolicy.wrapAround,
        previousFocusArea = focusPolicy.previousFocusArea,
        nextFocusArea = focusPolicy.nextFocusArea,
        modifier = modifier,
    ) {
        FocusItem(
            id = actionId,
            onClick = onAction,
            touchBehavior = FocusItemTouchBehavior.ComposeContent,
            semantics = FocusItemSemantics(label = actionLabel, role = FocusItemRole.Button),
        ) { _ ->
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = Color.Transparent,
            ) {
                VehicleUnavailableState(
                    title = resolvedTitle,
                    message = message,
                    actionLabel = actionLabel,
                    onAction = onAction,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun VehicleShowAllCard(
    focusId: FocusItemId,
    showAll: Boolean,
    onClick: () -> Unit,
) {
    val label =
        if (showAll) {
            stringResource(R.string.vehicle_control_hide_unavailable)
        } else {
            stringResource(R.string.vehicle_control_show_all)
        }
    FocusItem(
        id = focusId,
        onClick = onClick,
        touchBehavior = FocusItemTouchBehavior.ComposeContent,
        semantics =
            FocusItemSemantics(
                label = label,
                stateDescription = stringResource(R.string.vehicle_control_partial_support),
                role = FocusItemRole.Toggle,
            ),
    ) { _ ->
        BCard(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            style = BCardStyle.Filled,
            size = BCardSize.Lg,
            onClick = onClick,
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.vehicle_control_partial_support),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BChip(
                        checked = showAll,
                        onCheckedChange = { onClick() },
                        style = BChipStyle.Outlined,
                        size = BChipSize.Lg,
                    ) {
                        Text(if (showAll) "All" else "Supported")
                    }
                }
            },
        )
    }
}

@Composable
private fun VehicleCategoryCard(
    focusId: FocusItemId,
    title: String,
    controls: List<VehicleControlUiModel>,
    summaryControls: List<VehicleControlUiModel>,
    onClick: () -> Unit,
) {
    val artwork = controls.firstNotNullOfOrNull { it.illustrationRes }
    FocusItem(
        id = focusId,
        onClick = onClick,
        // This card is a navigation action, not a touch-rich Compose control. View ownership is
        // required so a physical tap reaches FocusItemView.performClick as well as CCP Center.
        touchBehavior = FocusItemTouchBehavior.View,
        semantics =
            FocusItemSemantics(
                label = title,
                stateDescription =
                    stringResource(
                        R.string.vehicle_control_category_count,
                        summaryControls.size,
                    ),
                role = FocusItemRole.Button,
            ),
    ) { _ ->
        SettingsCardSurface(
            modifier = Modifier.fillMaxWidth().height(156.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VehicleCategoryArtwork(
                    illustrationRes = artwork,
                    contentDescription = title,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(
                            R.string.vehicle_control_category_count,
                            summaryControls.size,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        summaryControls.take(3).joinToString(" • ") { it.title },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleCategoryArtwork(
    @DrawableRes illustrationRes: Int?,
    contentDescription: String,
) {
    Surface(
        modifier = Modifier.width(176.dp).height(112.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        if (illustrationRes != null) {
            VehicleIllustrationImage(
                illustrationRes = illustrationRes,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

@Composable
@Suppress("LongMethod")
internal fun VehicleCategoryScreen(
    featureTitle: String,
    categoryTitle: String,
    @DrawableRes titleIconRes: Int?,
    connected: Boolean,
    restricted: Boolean,
    controls: List<VehicleControlUiModel>,
    zones: List<VehicleZoneOption>,
    selectedAreaId: Int,
    onAreaSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenInfo: (VehicleControlUiModel) -> Unit,
    onSetBoolean: (String, Int, Boolean) -> Unit,
    onSetInt: (String, Int, Int) -> Unit,
    onSetFloat: ((String, Int, Float) -> Unit)?,
    modifier: Modifier,
    zoneSelectorLabel: String,
    restrictedReason: String,
    showVehicleDiagram: Boolean,
    visualizationSource: VehicleVisualizationSource?,
    visualizationLabel: String?,
    visualization: (@Composable (VehicleControlUiModel?) -> Unit)?,
) {
    val rotaryController = LocalRotaryFocusController.current
    // Register the category Back callback inside the nested rotary destination. This callback is
    // composed after the feature-level callback and therefore remains the topmost dispatcher
    // owner while a popup has returned focus to the category window.
    BackHandler {
        if (rotaryController?.isDirectManipulationActive == true) {
            Log.d("MySystemVehicle", "vehicle-back consumed for direct manipulation exit")
            rotaryController.exitDirectManipulation()
            return@BackHandler
        }
        Log.d("MySystemVehicle", "vehicle-back category-screen-to-overview")
        onBack()
    }
    val destinationKey = vehicleFocusKey("$featureTitle-$categoryTitle")
    val previewAreaId = "vehicle-preview-$destinationKey"
    val zoneAreaId = "vehicle-zones-$destinationKey"
    val controlsAreaId = "vehicle-controls-$destinationKey"
    val selectableZones = zones.filter { it.areaId != 0 }
    val visibleControls =
        remember(controls, selectedAreaId) {
            filterVehicleControlsForArea(controls, selectedAreaId)
        }
    var selectedControlKey by rememberSaveable(destinationKey, selectedAreaId) {
        mutableStateOf(visibleControls.firstOrNull()?.key)
    }
    var selectedControlAreaId by rememberSaveable(destinationKey, selectedAreaId) {
        mutableIntStateOf(visibleControls.firstOrNull()?.areaId ?: 0)
    }
    val selectedControl =
        selectVehicleControl(
            controls = visibleControls,
            selectedKey = selectedControlKey,
            selectedAreaId = selectedControlAreaId,
        )
    LaunchedEffect(visibleControls) {
        if (selectedControl == null) {
            selectedControlKey = null
            selectedControlAreaId = 0
        } else if (
            selectedControl.key != selectedControlKey ||
            selectedControl.areaId != selectedControlAreaId
        ) {
            selectedControlKey = selectedControl.key
            selectedControlAreaId = selectedControl.areaId
        }
    }
    // Rotary focus updates the preview through onFocused; finger taps must do the same. Wrap
    // every write callback so touching a control also selects it for the preview pane before
    // the value change is dispatched.
    val touchSelectingOnSetBoolean: (String, Int, Boolean) -> Unit =
        { key, areaId, value ->
            selectedControlKey = key
            selectedControlAreaId = areaId
            onSetBoolean(key, areaId, value)
        }
    val touchSelectingOnSetInt: (String, Int, Int) -> Unit =
        { key, areaId, value ->
            selectedControlKey = key
            selectedControlAreaId = areaId
            onSetInt(key, areaId, value)
        }
    val touchSelectingOnSetFloat: ((String, Int, Float) -> Unit)? =
        onSetFloat?.let { setter ->
            { key, areaId, value ->
                selectedControlKey = key
                selectedControlAreaId = areaId
                setter(key, areaId, value)
            }
        }

    VehicleSettingsScaffold(
        title = categoryTitle,
        titleIconRes = titleIconRes,
        onBack = onBack,
        destinationKey = destinationKey,
        modifier = modifier,
        // The first selected target must be a useful writable control whenever one exists.
        // The illustrated-guide button is only the fallback for an all-read-only category.
        // Every visible row exposes an information action, including read-only/unsupported
        // capabilities. Prefer the controls area whenever it has a meaningful action; only use
        // the preview guide as a fallback for a genuinely empty category.
        // The controls pane always owns the first actionable fallback.  A preview with no
        // selected control intentionally contains only decorative content and therefore must not
        // become the destination's initial focus area; doing so leaves CCP with a missing target
        // on empty/unsupported categories.
        contentFocusAreaIds =
            buildList {
                add(previewAreaId)
                if (selectableZones.size > 1) add(zoneAreaId)
                add(controlsAreaId)
            },
        actions = {
            SettingsAppBarAction(
                id = "$destinationKey-refresh",
                contentDescription = stringResource(R.string.vehicle_control_retry),
                onClick = onRefresh,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
            }
        },
    ) { padding ->
        // Scaffold's safe-drawing top inset and the native B-Material app-bar FocusArea can
        // differ by a few pixels on API 37. Keep category FocusAreas below the app bar instead
        // of allowing the zone selector to overlap its bottom edge.
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(top = 8.dp),
        ) {
            val wideLayout = maxWidth >= 1_000.dp
            if (wideLayout) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    VehiclePreviewPane(
                        control = selectedControl,
                        areaId = previewAreaId,
                        onOpenInfo = onOpenInfo,
                        visualizationSource = visualizationSource,
                        visualizationLabel = visualizationLabel,
                        visualization = visualization,
                        modifier = Modifier.weight(0.38f).fillMaxHeight(),
                    )
                    VehicleControlsPane(
                        controls = visibleControls,
                        zones = selectableZones,
                        selectedAreaId = selectedAreaId,
                        onAreaSelected = onAreaSelected,
                        connected = connected,
                        restricted = restricted,
                        restrictedReason = restrictedReason,
                        onRefresh = onRefresh,
                        onOpenInfo = onOpenInfo,
                        onFocused = { control ->
                            selectedControlKey = control.key
                            selectedControlAreaId = control.areaId
                        },
                        onSetBoolean = touchSelectingOnSetBoolean,
                        onSetInt = touchSelectingOnSetInt,
                        onSetFloat = touchSelectingOnSetFloat,
                        zoneAreaId = zoneAreaId,
                        controlsAreaId = controlsAreaId,
                        zoneSelectorLabel = zoneSelectorLabel,
                        showVehicleDiagram = showVehicleDiagram,
                        scrollInternally = true,
                        // The native app-bar FocusArea reports a 184px bottom edge on API 37,
                        // while the scrollable content can be translated upward when CCP brings
                        // the first control into view. Offset the entire controls pane (not just
                        // its children) by a full 48dp so the zone FocusArea keeps a visible
                        // clearance from the header in both the settled and bring-into-view
                        // layouts. This prevents the native validator from registering a
                        // transient header/zone overlap after typography or row-height changes.
                        modifier =
                            Modifier
                                .weight(0.62f)
                                .fillMaxHeight()
                                .offset(y = 48.dp),
                    )
                }
            } else {
                val overviewScrollState = rememberScrollState()
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .automotiveScrollbar(overviewScrollState)
                            .verticalScroll(overviewScrollState)
                            .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    VehiclePreviewPane(
                        control = selectedControl,
                        areaId = previewAreaId,
                        onOpenInfo = onOpenInfo,
                        visualizationSource = visualizationSource,
                        visualizationLabel = visualizationLabel,
                        visualization = visualization,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VehicleControlsPane(
                        controls = visibleControls,
                        zones = selectableZones,
                        selectedAreaId = selectedAreaId,
                        onAreaSelected = onAreaSelected,
                        connected = connected,
                        restricted = restricted,
                        restrictedReason = restrictedReason,
                        onRefresh = onRefresh,
                        onOpenInfo = onOpenInfo,
                        onFocused = { control ->
                            selectedControlKey = control.key
                            selectedControlAreaId = control.areaId
                        },
                        onSetBoolean = touchSelectingOnSetBoolean,
                        onSetInt = touchSelectingOnSetInt,
                        onSetFloat = touchSelectingOnSetFloat,
                        zoneAreaId = zoneAreaId,
                        controlsAreaId = controlsAreaId,
                        zoneSelectorLabel = zoneSelectorLabel,
                        showVehicleDiagram = showVehicleDiagram,
                        scrollInternally = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun VehiclePreviewPane(
    control: VehicleControlUiModel?,
    areaId: String,
    onOpenInfo: (VehicleControlUiModel) -> Unit,
    visualizationSource: VehicleVisualizationSource?,
    visualizationLabel: String?,
    visualization: (@Composable (VehicleControlUiModel?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val focusAreaId = FocusAreaId(areaId)
    val focusPolicy = vehicleDetailFocusAreaPolicy(focusAreaId)
    FocusArea(
        id = focusAreaId,
        firstFocusAt = if (control != null) FocusItemId("$areaId-guide") else null,
        wrapAround = focusPolicy.wrapAround,
        previousFocusArea = focusPolicy.previousFocusArea,
        nextFocusArea = focusPolicy.nextFocusArea,
        modifier = modifier,
    ) {
        if (control == null) {
            BCard(
                modifier = Modifier.fillMaxWidth(),
                style = BCardStyle.Filled,
                size = BCardSize.Lg,
                content = {
                    Text(stringResource(R.string.vehicle_control_category_empty))
                },
            )
        } else {
            // The illustration and its explanatory text are deliberately static. The separate
            // labelled button below is the only focusable information action in this pane.
            BCard(
                modifier = Modifier.fillMaxWidth(),
                style = BCardStyle.Elevated,
                size = BCardSize.Lg,
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (visualization != null) {
                            visualization(control)
                        } else {
                            AnimatedContent(
                                targetState = control.key to control.illustrationRes,
                                transitionSpec = {
                                    fadeIn(tween(VehicleMotionTokens.CONTENT_ENTER_DURATION_MILLIS)) togetherWith
                                        fadeOut(tween(VehicleMotionTokens.CONTENT_EXIT_DURATION_MILLIS))
                                },
                                label = "vehicle-control-preview",
                            ) { (_, illustrationRes) ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ) {
                                    if (illustrationRes != null) {
                                        VehicleIllustrationImage(
                                            illustrationRes = illustrationRes,
                                            contentDescription = control.info,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DirectionsCar,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(104.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (visualizationSource != null && visualizationLabel != null) {
                            Text(
                                text = visualizationLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier.semantics {
                                        stateDescription = visualizationSource.name
                                    },
                            )
                        }
                        Text(
                            text = control.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = control.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = vehicleControlStatusLabel(control),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
            FocusItem(
                id = FocusItemId("$areaId-guide"),
                onClick = { onOpenInfo(control) },
                touchBehavior = FocusItemTouchBehavior.ComposeContent,
                semantics =
                    FocusItemSemantics(
                        label = control.title,
                        stateDescription = stringResource(R.string.vehicle_control_illustrated_guide),
                        role = FocusItemRole.Button,
                    ),
            ) { _ ->
                AutomotiveTextButton(
                    label = stringResource(R.string.vehicle_control_illustrated_guide),
                    onClick = { onOpenInfo(control) },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
