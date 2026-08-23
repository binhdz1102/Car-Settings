package com.android.car.settings.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusTarget

/**
 * Pure presentation decisions shared by the rotary and hostless vehicle surfaces.
 *
 * Keeping these decisions out of the large screen composable makes capability refreshes cheap and
 * gives tests a deterministic contract for which rows may become CCP stops.
 */
internal fun vehicleBackEnabled(
    categoryKey: String?,
    suppressParentBack: Boolean,
): Boolean = categoryKey != null || suppressParentBack

/** Returns a valid zone while preserving the caller's selection whenever it is still exposed. */
internal fun effectiveVehicleAreaId(
    zones: List<VehicleZoneOption>,
    selectedAreaId: Int,
): Int =
    if (zones.any { it.areaId == selectedAreaId }) {
        selectedAreaId
    } else {
        zones.firstOrNull { it.areaId != 0 }?.areaId ?: 0
    }

/** Keeps unsupported rows discoverable only when the user explicitly enables Show all. */
internal fun visibleVehicleControls(
    controls: List<VehicleControlUiModel>,
    showAllControls: Boolean,
): List<VehicleControlUiModel> = if (showAllControls) controls else controls.filter { it.supported }

/** Stable category order used by both the overview and the rotary fallback calculation. */
internal fun vehicleCategoryKeys(controls: List<VehicleControlUiModel>): List<String> = controls.map { it.categoryKey }.distinct()

/**
 * Precomputed overview data.  The screen only renders this model; grouping, de-duplication and
 * focus ordering stay outside composition so a pending pulse or slider frame cannot allocate the
 * same maps repeatedly.
 */
@Immutable
internal data class VehicleCategoryPresentation(
    val key: String,
    val title: String,
    val controls: List<VehicleControlUiModel>,
    val summaryControls: List<VehicleControlUiModel>,
    @param:DrawableRes val artworkRes: Int?,
)

@Immutable
internal data class VehicleOverviewPresentation(
    val areaId: String,
    val categories: List<VehicleCategoryPresentation>,
    val hasUnavailableControls: Boolean,
    val errorMessage: String?,
    val focusOrder: List<FocusItemId>,
    val firstFocusId: FocusItemId?,
)

/**
 * Computes the only initial fallback owned by the overview destination. The target must use the
 * content area (where cards are registered), never the shell/header area; otherwise a restore
 * request can be accepted but can never resolve to a real FocusItemView.
 */
internal fun vehicleOverviewFallbackTarget(
    presentation: VehicleOverviewPresentation,
    contentAreaId: String,
    loading: Boolean,
): RotaryFocusTarget? =
    when {
        presentation.firstFocusId != null ->
            RotaryFocusTarget(FocusAreaId(contentAreaId), presentation.firstFocusId)
        !loading ->
            RotaryFocusTarget(
                FocusAreaId(contentAreaId),
                FocusItemId("$contentAreaId-action"),
            )
        else -> null
    }

internal fun buildVehicleOverviewPresentation(
    title: String,
    controls: List<VehicleControlUiModel>,
    controlsForDisplay: List<VehicleControlUiModel>,
): VehicleOverviewPresentation {
    val areaId = "vehicle-overview-${vehicleFocusKey(title)}"
    val grouped = controlsForDisplay.groupBy { it.categoryKey }
    val categories =
        grouped.map { (key, categoryControls) ->
            VehicleCategoryPresentation(
                key = key,
                title = categoryControls.firstOrNull()?.section ?: key,
                controls = categoryControls,
                summaryControls = categoryControls.distinctBy(VehicleControlUiModel::key),
                artworkRes = categoryControls.firstNotNullOfOrNull { it.illustrationRes },
            )
        }
    val hasUnavailableControls = controls.any { !it.supported }
    val errorMessage = controlsForDisplay.firstNotNullOfOrNull { it.errorMessage }
    val focusOrder =
        buildList {
            if (errorMessage != null) add(FocusItemId("$areaId-retry"))
            if (hasUnavailableControls) add(FocusItemId("$areaId-show-all"))
            categories.forEach { add(FocusItemId("$areaId-${vehicleFocusKey(it.key)}")) }
        }
    val firstFocusId =
        if (hasUnavailableControls) {
            FocusItemId("$areaId-show-all")
        } else {
            categories.firstOrNull()?.let { FocusItemId("$areaId-${vehicleFocusKey(it.key)}") }
        }
    return VehicleOverviewPresentation(
        areaId = areaId,
        categories = categories,
        hasUnavailableControls = hasUnavailableControls,
        errorMessage = errorMessage,
        focusOrder = focusOrder,
        firstFocusId = firstFocusId,
    )
}

/** Keeps the illustrated preview selection stable when a value refresh changes a row summary. */
internal fun selectVehicleControl(
    controls: List<VehicleControlUiModel>,
    selectedKey: String?,
    selectedAreaId: Int,
): VehicleControlUiModel? =
    controls.firstOrNull { it.key == selectedKey && it.areaId == selectedAreaId }
        ?: controls.firstOrNull()

/** Returns only controls that actually belong to the selected zone. */
internal fun filterVehicleControlsForArea(
    controls: List<VehicleControlUiModel>,
    selectedAreaId: Int,
): List<VehicleControlUiModel> = controls.filter { it.areaId == 0 || it.areaId == selectedAreaId }

/** Keeps overlapping AOSP area bitmasks scoped to the property family that owns them. */
internal fun vehicleZonesForCategory(
    zones: List<VehicleZoneOption>,
    categoryKey: String?,
): List<VehicleZoneOption> =
    zones.filter { zone ->
        zone.propertyFamily.isBlank() || categoryKey == null || zone.propertyFamily == categoryKey
    }

/** Zone chips are navigation actions only while a connected capability list exists. */
internal fun vehicleZoneSelectionEnabled(
    connected: Boolean,
    hasControls: Boolean,
): Boolean = connected && hasControls

/** Disabled/unavailable zones never enter CCP order, while remaining visible to touch users. */
internal fun actionableVehicleZoneIds(
    zones: List<VehicleZoneOption>,
    selectionEnabled: Boolean,
): List<Int> = zones.filter { selectionEnabled && it.enabled }.map { it.areaId }

/** Static/read-only rows expose information but never become dead rotary stops. */
@Suppress("ReturnCount", "ComplexCondition")
internal fun isVehicleControlRotaryActionable(
    control: VehicleControlUiModel,
    connected: Boolean,
    restricted: Boolean,
): Boolean {
    if (!connected || !control.supported || !control.available || !control.writable) return false
    if (restricted && control.requiresUnrestrictedUx) return false
    return when (control.editor) {
        VehicleEditorUiKind.SWITCH -> control.booleanValue != null
        VehicleEditorUiKind.SLIDER -> control.numericValue != null
        VehicleEditorUiKind.ENUM -> control.enumOptions.isNotEmpty()
        VehicleEditorUiKind.STATUS -> false
    }
}

/** Stable, value-independent key for B-Material FocusItem IDs. */
internal fun vehicleFocusKey(value: String): String = value.hashCode().toUInt().toString(16)

/**
 * Precomputed focus contract for a vehicle controls pane.
 *
 * The screen can recompose for a slider frame, pending pulse, or connection update without
 * rebuilding maps and focus IDs in the composition phase. The model is also the single source of
 * truth for the order in which CCP visits editor and information actions.
 */
@Immutable
internal data class VehicleControlFocusEntry(
    val control: VehicleControlUiModel,
    val focusId: FocusItemId,
    val infoFocusId: FocusItemId,
    val rotaryActionable: Boolean,
)

@Immutable
internal data class VehicleControlFocusLayout(
    val entries: List<VehicleControlFocusEntry>,
    val focusOrder: List<FocusItemId>,
    val firstMeaningfulFocusId: FocusItemId?,
)

internal fun buildVehicleControlFocusLayout(
    controls: List<VehicleControlUiModel>,
    selectedAreaId: Int,
    controlsAreaId: String,
    connected: Boolean,
    restricted: Boolean,
    hasRetryAction: Boolean,
): VehicleControlFocusLayout {
    val entries =
        controls.map { control ->
            VehicleControlFocusEntry(
                control = control,
                focusId =
                    FocusItemId(
                        "$controlsAreaId-${vehicleFocusKey(control.key)}-${control.areaId}-" +
                            "area-${vehicleFocusKey(selectedAreaId.toString())}",
                    ),
                infoFocusId =
                    FocusItemId(
                        "$controlsAreaId-${vehicleFocusKey(control.key)}-${control.areaId}-" +
                            "area-${vehicleFocusKey(selectedAreaId.toString())}-info",
                    ),
                rotaryActionable =
                    isVehicleControlRotaryActionable(
                        control = control,
                        connected = connected,
                        restricted = restricted,
                    ),
            )
        }
    val retryFocusId = FocusItemId("$controlsAreaId-retry")
    val duplicateIds =
        entries
            .flatMap { entry -> listOf(entry.focusId, entry.infoFocusId) }
            .groupingBy { it }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
    check(duplicateIds.isEmpty()) {
        "Duplicate vehicle FocusItem IDs in $controlsAreaId: ${duplicateIds.joinToString()}"
    }
    val focusOrder =
        buildList {
            if (hasRetryAction) add(retryFocusId)
            entries.forEach { entry ->
                if (entry.rotaryActionable) add(entry.focusId)
                // Information remains available for disabled/read-only controls and is a distinct
                // actionable stop rather than a nested action in the editor row.
                add(entry.infoFocusId)
            }
        }
    return VehicleControlFocusLayout(
        entries = entries,
        focusOrder = focusOrder,
        firstMeaningfulFocusId = focusOrder.firstOrNull { it != retryFocusId },
    )
}
