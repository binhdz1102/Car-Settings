package com.android.car.settings.feature.doorcontrol.presentation

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.VehicleControlUiMetadata
import com.android.car.settings.core.ui.VehicleEditorUiKind
import com.android.car.settings.core.ui.VehicleFeatureScreen
import com.android.car.settings.core.ui.VehicleVisualizationSource
import com.android.car.settings.core.ui.VehicleZoneOption
import com.android.car.settings.core.ui.toUiControls
import com.android.car.settings.core.vehicle.VehicleConnectionState
import com.android.car.settings.core.vehicle.VehiclePropertyAreaType
import com.android.car.settings.core.vehicle.VehicleUxPolicyState
import com.android.car.settings.feature.doorcontrol.R
import com.android.car.settings.feature.doorcontrol.domain.DOOR_CONTROL_DEFINITIONS
import com.android.car.settings.feature.doorcontrol.domain.DoorControlKind
import com.android.car.settings.core.ui.R as CoreUiR

@Composable
fun DoorControlRoute(
    viewModel: DoorControlViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalContext.current.resources
    val titles = resources.getStringArray(R.array.door_control_titles)
    val descriptions = resources.getStringArray(R.array.door_control_descriptions)
    val sections = resources.getStringArray(R.array.door_control_sections)
    val movement = resources.getStringArray(R.array.door_movement_labels)
    val limitations = stringResource(R.string.door_control_limitations)
    val dependencies = stringResource(R.string.door_control_dependencies)
    val unavailable = stringResource(R.string.vehicle_control_unavailable_message)
    val movementLabels = remember(movement.contentHashCode()) { mapOf(-1 to movement[0], 0 to movement[1], 1 to movement[2]) }

    val metadata =
        remember(
            titles.contentHashCode(),
            descriptions.contentHashCode(),
            sections.contentHashCode(),
            movementLabels,
            limitations,
            dependencies,
        ) {
            buildDoorControlMetadata(
                titles = titles,
                descriptions = descriptions,
                sections = sections,
                movementLabels = movementLabels,
                limitations = limitations,
                dependencies = dependencies,
            )
        }
    val controls = remember(state, metadata, unavailable) { state.toUiControls(metadata) { unavailable } }
    val zones =
        controls
            .map { control ->
                VehicleZoneOption(
                    areaId = control.areaId,
                    label = doorAreaLabel(control.areaType, control.areaId),
                    propertyFamily = control.categoryKey,
                    areaType = control.areaType,
                )
            }.distinctBy(VehicleZoneOption::key)
            .sortedWith(compareBy(VehicleZoneOption::propertyFamily, VehicleZoneOption::areaId))

    VehicleFeatureScreen(
        title = stringResource(R.string.door_control_title),
        titleIconRes = CoreUiR.drawable.ic_vehicle_door,
        loading = state.loading,
        connected = state.connection is VehicleConnectionState.Connected,
        restricted = state.uxPolicy !is VehicleUxPolicyState.Unrestricted,
        controls = controls,
        zones = zones,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSetBoolean = viewModel::setBoolean,
        onSetInt = viewModel::setInt,
        zoneSelectorLabel = stringResource(R.string.door_zone_selector),
        connectingMessage = stringResource(R.string.vehicle_service_connecting),
        emptyMessage = stringResource(R.string.door_control_empty),
        restrictedReason = stringResource(R.string.vehicle_control_restricted),
        // The zone selector labels remain context-aware because AOSP door/window/mirror area bits
        // overlap; the dedicated preview only uses actual position/state property values.
        showVehicleDiagram = false,
        visualizationSource = VehicleVisualizationSource.LIVE_PROPERTY,
        visualizationLabel = "Live position diagram from observed door, window and mirror properties",
        visualization = { selected -> DoorControlVisualization(controls, selected) },
    )
}

/** Pure metadata construction kept out of the recomposing route body. */
internal fun buildDoorControlMetadata(
    titles: Array<String>,
    descriptions: Array<String>,
    sections: Array<String>,
    movementLabels: Map<Int, String>,
    limitations: String,
    dependencies: String,
): List<VehicleControlUiMetadata> =
    DOOR_CONTROL_DEFINITIONS.map { definition ->
        VehicleControlUiMetadata(
            key = definition.id.name,
            categoryKey = definition.section.name,
            section = sections[definition.section.ordinal],
            title = titles[definition.id.ordinal],
            summary = descriptions[definition.id.ordinal],
            info = descriptions[definition.id.ordinal],
            limitations = limitations,
            dependencies = dependencies,
            illustrationRes = doorArtwork(definition.id),
            editor =
                when (definition.kind) {
                    DoorControlKind.SWITCH -> VehicleEditorUiKind.SWITCH
                    DoorControlKind.RANGE -> VehicleEditorUiKind.SLIDER
                    DoorControlKind.ENUM -> VehicleEditorUiKind.ENUM
                },
            enumLabels = movementLabels,
            valueLabel = { value -> doorValueLabel(definition.kind, movementLabels, value) },
        )
    }

@Composable
private fun doorAreaLabel(
    areaType: VehiclePropertyAreaType,
    areaId: Int,
): String {
    if (areaId == 0) return stringResource(R.string.vehicle_zone_global)
    return when (areaType) {
        VehiclePropertyAreaType.DOOR -> doorSpecificAreaLabel(areaId)
        VehiclePropertyAreaType.WINDOW -> windowSpecificAreaLabel(areaId)
        VehiclePropertyAreaType.MIRROR -> mirrorSpecificAreaLabel(areaId)
        else -> stringResource(R.string.vehicle_zone_id_format, areaId)
    }
}

@Composable
private fun doorSpecificAreaLabel(areaId: Int): String =
    when (areaId) {
        0x00000001 -> stringResource(R.string.door_area_row_1_left)
        0x00000004 -> stringResource(R.string.door_area_row_1_right)
        0x00000010 -> stringResource(R.string.door_area_row_2_left)
        0x00000040 -> stringResource(R.string.door_area_row_2_right)
        0x00000100 -> stringResource(R.string.door_area_row_3_left)
        0x00000400 -> stringResource(R.string.door_area_row_3_right)
        0x10000000 -> stringResource(R.string.door_area_hood)
        0x20000000 -> stringResource(R.string.door_area_tailgate)
        else -> stringResource(R.string.door_area_id_format, areaId)
    }

@Composable
private fun windowSpecificAreaLabel(areaId: Int): String =
    when (areaId) {
        0x00000001 -> stringResource(R.string.window_area_front_windshield)
        0x00000002 -> stringResource(R.string.window_area_rear_windshield)
        0x00000010 -> stringResource(R.string.window_area_row_1_left)
        0x00000040 -> stringResource(R.string.window_area_row_1_right)
        0x00000100 -> stringResource(R.string.window_area_row_2_left)
        0x00000400 -> stringResource(R.string.window_area_row_2_right)
        0x00001000 -> stringResource(R.string.window_area_row_3_left)
        0x00004000 -> stringResource(R.string.window_area_row_3_right)
        0x00010000 -> stringResource(R.string.window_area_roof_1)
        else -> stringResource(R.string.window_area_id_format, areaId)
    }

@Composable
private fun mirrorSpecificAreaLabel(areaId: Int): String =
    when (areaId) {
        0x00000001 -> stringResource(R.string.mirror_area_driver_left)
        0x00000002 -> stringResource(R.string.mirror_area_driver_right)
        0x00000004 -> stringResource(R.string.mirror_area_driver_center)
        else -> stringResource(R.string.mirror_area_id_format, areaId)
    }

internal fun doorValueLabel(
    kind: DoorControlKind,
    movementLabels: Map<Int, String>,
    value: Any,
): String =
    if (kind == DoorControlKind.ENUM) {
        movementLabels[(value as? Number)?.toInt()] ?: value.toString()
    } else {
        (value as? Number)?.toInt()?.toString() ?: value.toString()
    }

/** Stable artwork mapping kept exhaustive with [DoorControlId] so every fallback item has a guide. */
@DrawableRes
internal fun doorArtwork(id: com.android.car.settings.feature.doorcontrol.domain.DoorControlId): Int =
    when (id) {
        com.android.car.settings.feature.doorcontrol.domain.DoorControlId.DOOR_LOCK ->
            R.drawable.guide_door_door_lock
        com.android.car.settings.feature.doorcontrol.domain.DoorControlId.CHILD_LOCK ->
            R.drawable.guide_door_child_lock
        com.android.car.settings.feature.doorcontrol.domain.DoorControlId.DOOR_POSITION ->
            R.drawable.guide_door_door_position
        com.android.car.settings.feature.doorcontrol.domain.DoorControlId.DOOR_MOVEMENT ->
            R.drawable.guide_door_door_movement
        com.android.car.settings.feature.doorcontrol.domain.DoorControlId.WINDOW_LOCK ->
            R.drawable.guide_door_window_lock
        com.android.car.settings.feature.doorcontrol.domain.DoorControlId.WINDOW_POSITION ->
            R.drawable.guide_door_window_position
        com.android.car.settings.feature.doorcontrol.domain.DoorControlId.WINDOW_MOVEMENT ->
            R.drawable.guide_door_window_movement
        com.android.car.settings.feature.doorcontrol.domain.DoorControlId.MIRROR_LOCK ->
            R.drawable.guide_door_mirror_lock
        com.android.car.settings.feature.doorcontrol.domain.DoorControlId.MIRROR_FOLD ->
            R.drawable.guide_door_mirror_fold
        com.android.car.settings.feature.doorcontrol.domain.DoorControlId.MIRROR_AUTO_FOLD ->
            R.drawable.guide_door_mirror_auto_fold
        com.android.car.settings.feature.doorcontrol.domain.DoorControlId.MIRROR_AUTO_TILT ->
            R.drawable.guide_door_mirror_auto_tilt
        com.android.car.settings.feature.doorcontrol.domain.DoorControlId.MIRROR_VERTICAL ->
            R.drawable.guide_door_mirror_vertical
        com.android.car.settings.feature.doorcontrol.domain.DoorControlId.MIRROR_HORIZONTAL ->
            R.drawable.guide_door_mirror_horizontal
    }
