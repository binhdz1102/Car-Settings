package com.android.car.settings.feature.seatcontrol.presentation

import android.car.VehicleAreaSeat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.VehicleControlUiMetadata
import com.android.car.settings.core.ui.VehicleEditorUiKind
import com.android.car.settings.core.ui.VehicleFeatureScreen
import com.android.car.settings.core.ui.VehicleSliderUiKind
import com.android.car.settings.core.ui.VehicleSliderUiSpec
import com.android.car.settings.core.ui.VehicleVisualizationSource
import com.android.car.settings.core.ui.VehicleZoneOption
import com.android.car.settings.core.ui.toUiControls
import com.android.car.settings.core.ui.toVehicleVisualPolicy
import com.android.car.settings.core.vehicle.VehicleConnectionState
import com.android.car.settings.core.vehicle.VehicleUxPolicyState
import com.android.car.settings.feature.seatcontrol.R
import com.android.car.settings.feature.seatcontrol.domain.SEAT_CONTROL_DEFINITIONS
import com.android.car.settings.feature.seatcontrol.domain.SeatControlId
import com.android.car.settings.feature.seatcontrol.domain.SeatControlKind
import com.android.car.settings.core.ui.R as CoreUiR

@Composable
fun SeatControlRoute(
    viewModel: SeatControlViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalContext.current.resources
    val titles = resources.getStringArray(R.array.seat_control_titles)
    val descriptions = resources.getStringArray(R.array.seat_control_descriptions)
    val sections = resources.getStringArray(R.array.seat_control_sections)
    val occupancyLabels = resources.getStringArray(R.array.seat_occupancy_labels)
    val limitations = stringResource(R.string.seat_control_limitations)
    val dependencies = stringResource(R.string.seat_control_dependencies)
    val unavailable = stringResource(R.string.vehicle_control_unavailable_message)
    val buckled = stringResource(R.string.seat_belt_buckled)
    val unbuckled = stringResource(R.string.seat_belt_unbuckled)
    val slotFormat = stringResource(R.string.seat_memory_slot_format)
    val chooseSlot = stringResource(R.string.seat_memory_choose_slot)

    val metadata =
        remember(
            titles.contentHashCode(),
            descriptions.contentHashCode(),
            sections.contentHashCode(),
            occupancyLabels.contentHashCode(),
            limitations,
            dependencies,
            buckled,
            unbuckled,
            slotFormat,
            chooseSlot,
        ) {
            buildSeatControlMetadata(
                titles = titles,
                descriptions = descriptions,
                sections = sections,
                occupancyLabels = occupancyLabels,
                limitations = limitations,
                dependencies = dependencies,
                buckled = buckled,
                unbuckled = unbuckled,
                slotFormat = slotFormat,
                chooseSlot = chooseSlot,
            )
        }
    // The mapper is pure and can be reused until a capability/value snapshot changes. This keeps
    // resource/metadata work out of unrelated rotary focus and illustration recompositions.
    val controls = remember(state, metadata, unavailable) { state.toUiControls(metadata) { unavailable } }
    val zones =
        controls
            .map { it.areaId }
            .distinct()
            .sorted()
            .map { VehicleZoneOption(it, seatAreaLabel(it)) }

    VehicleFeatureScreen(
        title = stringResource(R.string.seat_control_title),
        titleIconRes = CoreUiR.drawable.ic_vehicle_seat,
        destinationKey = SEAT_CONTROL_ROUTE,
        loading = state.loading,
        connected = state.connection is VehicleConnectionState.Connected,
        restricted = state.uxPolicy !is VehicleUxPolicyState.Unrestricted,
        controls = controls,
        zones = zones,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSetBoolean = viewModel::setBoolean,
        onSetInt = viewModel::setInt,
        zoneSelectorLabel = stringResource(R.string.seat_zone_selector),
        connectingMessage = stringResource(R.string.vehicle_service_connecting),
        emptyMessage = stringResource(R.string.seat_control_empty),
        restrictedReason = stringResource(R.string.vehicle_control_restricted),
        showVehicleDiagram = true,
        visualizationSource = VehicleVisualizationSource.LIVE_PROPERTY,
        visualizationLabel = "Observed seat setting — not live occupancy data",
        visualization = { selected, policy -> SeatControlVisualization(controls, selected, visualPolicy = policy) },
        visualPolicy = state.uxPolicy.toVehicleVisualPolicy(),
    )
}

/** Pure metadata construction kept out of the recomposing route body. */
internal fun buildSeatControlMetadata(
    titles: Array<String>,
    descriptions: Array<String>,
    sections: Array<String>,
    occupancyLabels: Array<String>,
    limitations: String,
    dependencies: String,
    buckled: String,
    unbuckled: String,
    slotFormat: String,
    chooseSlot: String,
): List<VehicleControlUiMetadata> =
    SEAT_CONTROL_DEFINITIONS.map { definition ->
        VehicleControlUiMetadata(
            key = definition.id.name,
            categoryKey = definition.section.name,
            section = sections[definition.section.ordinal],
            title = titles[definition.id.ordinal],
            summary = descriptions[definition.id.ordinal],
            info = descriptions[definition.id.ordinal],
            limitations = limitations,
            dependencies = dependencies,
            illustrationRes = seatArtwork(definition.id),
            editor =
                when (definition.kind) {
                    SeatControlKind.SWITCH -> VehicleEditorUiKind.SWITCH
                    SeatControlKind.RANGE -> VehicleEditorUiKind.SLIDER
                    SeatControlKind.SLOT_ACTION -> VehicleEditorUiKind.ENUM
                    SeatControlKind.STATUS -> VehicleEditorUiKind.STATUS
                },
            sliderUiSpec = seatSliderUiSpec(definition.kind),
            enumLabels =
                if (definition.kind == SeatControlKind.SLOT_ACTION) {
                    (0..3).associateWith { slot -> slotFormat.replace("%1\$d", slot.toString()) }
                } else {
                    emptyMap()
                },
            emptyValueLabel = if (definition.kind == SeatControlKind.SLOT_ACTION) chooseSlot else "",
            valueLabel = { value ->
                when (definition.id) {
                    SeatControlId.OCCUPANCY ->
                        occupancyLabels.getOrNull((value as Number).toInt()) ?: value.toString()
                    SeatControlId.SEAT_BELT -> if (value == true) buckled else unbuckled
                    SeatControlId.MEMORY_RECALL, SeatControlId.MEMORY_SAVE ->
                        slotFormat.replace("%1\$d", (value as Number).toInt().toString())
                    else -> (value as? Number)?.toInt()?.toString() ?: value.toString()
                }
            },
        )
    }

internal fun seatSliderUiSpec(kind: SeatControlKind): VehicleSliderUiSpec =
    if (kind == SeatControlKind.RANGE) {
        VehicleSliderUiSpec(kind = VehicleSliderUiKind.POSITION, centerMarker = 0f)
    } else {
        VehicleSliderUiSpec()
    }

private fun seatArtwork(id: SeatControlId): Int =
    when (id) {
        SeatControlId.FORE_AFT -> R.drawable.guide_seat_fore_aft
        SeatControlId.HEIGHT -> R.drawable.guide_seat_height
        SeatControlId.DEPTH -> R.drawable.guide_seat_depth
        SeatControlId.TILT -> R.drawable.guide_seat_tilt
        SeatControlId.BACKREST_PRIMARY -> R.drawable.guide_seat_backrest_primary
        SeatControlId.BACKREST_SECONDARY -> R.drawable.guide_seat_backrest_secondary
        SeatControlId.LUMBAR_FORE_AFT -> R.drawable.guide_seat_lumbar_fore_aft
        SeatControlId.LUMBAR_VERTICAL -> R.drawable.guide_seat_lumbar_vertical
        SeatControlId.LUMBAR_SIDE_SUPPORT -> R.drawable.guide_seat_lumbar_side_support
        SeatControlId.CUSHION_SIDE_SUPPORT -> R.drawable.guide_seat_cushion_side_support
        SeatControlId.HEADREST_HEIGHT -> R.drawable.guide_seat_headrest_height
        SeatControlId.HEADREST_ANGLE -> R.drawable.guide_seat_headrest_angle
        SeatControlId.HEADREST_FORE_AFT -> R.drawable.guide_seat_headrest_fore_aft
        SeatControlId.EASY_ACCESS -> R.drawable.guide_seat_easy_access
        SeatControlId.OCCUPANCY -> R.drawable.guide_seat_occupancy
        SeatControlId.SEAT_BELT -> R.drawable.guide_seat_seat_belt
        SeatControlId.MEMORY_RECALL -> R.drawable.guide_seat_memory_recall
        SeatControlId.MEMORY_SAVE -> R.drawable.guide_seat_memory_save
        SeatControlId.STEERING_WHEEL_DEPTH -> R.drawable.guide_seat_steering_wheel_depth
        SeatControlId.STEERING_WHEEL_HEIGHT -> R.drawable.guide_seat_steering_wheel_height
    }

@Composable
private fun seatAreaLabel(areaId: Int): String =
    when (areaId) {
        0 -> stringResource(R.string.vehicle_zone_global)
        VehicleAreaSeat.SEAT_ROW_1_LEFT -> stringResource(R.string.seat_front_left)
        VehicleAreaSeat.SEAT_ROW_1_CENTER -> stringResource(R.string.seat_front_center)
        VehicleAreaSeat.SEAT_ROW_1_RIGHT -> stringResource(R.string.seat_front_right)
        VehicleAreaSeat.SEAT_ROW_2_LEFT -> stringResource(R.string.seat_rear_left)
        VehicleAreaSeat.SEAT_ROW_2_CENTER -> stringResource(R.string.seat_rear_center)
        VehicleAreaSeat.SEAT_ROW_2_RIGHT -> stringResource(R.string.seat_rear_right)
        else -> stringResource(R.string.vehicle_zone_id_format, areaId)
    }
