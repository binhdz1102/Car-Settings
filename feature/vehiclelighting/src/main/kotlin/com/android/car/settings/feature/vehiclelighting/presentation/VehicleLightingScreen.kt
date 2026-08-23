package com.android.car.settings.feature.vehiclelighting.presentation

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
import com.android.car.settings.core.ui.VehicleVisualizationSource
import com.android.car.settings.core.ui.VehicleZoneOption
import com.android.car.settings.core.ui.toUiControls
import com.android.car.settings.core.vehicle.VehicleConnectionState
import com.android.car.settings.core.vehicle.VehicleUxPolicyState
import com.android.car.settings.feature.vehiclelighting.R
import com.android.car.settings.feature.vehiclelighting.domain.VEHICLE_LIGHTING_DEFINITIONS
import com.android.car.settings.feature.vehiclelighting.domain.VehicleLightingId
import com.android.car.settings.core.ui.R as CoreUiR

@Composable
fun VehicleLightingRoute(
    viewModel: VehicleLightingViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalContext.current.resources
    val titles = resources.getStringArray(R.array.vehicle_lighting_titles)
    val descriptions = resources.getStringArray(R.array.vehicle_lighting_descriptions)
    val sections = resources.getStringArray(R.array.vehicle_lighting_sections)
    val modes = resources.getStringArray(R.array.vehicle_lighting_modes)
    val limitations = stringResource(R.string.vehicle_lighting_limitations)
    val dependencies = stringResource(R.string.vehicle_lighting_dependencies)
    val unavailable = stringResource(R.string.vehicle_control_unavailable_message)
    val modeLabels = remember(modes.contentHashCode()) { modes.mapIndexed { index, label -> index to label }.toMap() }
    val metadata =
        remember(
            titles.contentHashCode(),
            descriptions.contentHashCode(),
            sections.contentHashCode(),
            modeLabels,
            limitations,
            dependencies,
        ) {
            buildVehicleLightingMetadata(
                titles = titles,
                descriptions = descriptions,
                sections = sections,
                modeLabels = modeLabels,
                limitations = limitations,
                dependencies = dependencies,
            )
        }
    val controls = remember(state, metadata, unavailable) { state.toUiControls(metadata) { unavailable } }
    val zones =
        controls.map { it.areaId }.distinct().sorted().map { areaId ->
            VehicleZoneOption(areaId, lightingAreaLabel(areaId))
        }

    VehicleFeatureScreen(
        title = stringResource(R.string.vehicle_lighting_title),
        titleIconRes = CoreUiR.drawable.ic_vehicle_lighting,
        loading = state.loading,
        connected = state.connection is VehicleConnectionState.Connected,
        restricted = state.uxPolicy !is VehicleUxPolicyState.Unrestricted,
        controls = controls,
        zones = zones,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSetBoolean = viewModel::setBoolean,
        onSetInt = viewModel::setInt,
        zoneSelectorLabel = stringResource(R.string.lighting_zone_selector),
        connectingMessage = stringResource(R.string.vehicle_service_connecting),
        emptyMessage = stringResource(R.string.vehicle_lighting_empty),
        restrictedReason = stringResource(R.string.vehicle_control_restricted),
        showVehicleDiagram = true,
        visualizationSource = VehicleVisualizationSource.LIVE_PROPERTY,
        visualizationLabel = "Observed control setting visualisation — not a lamp diagnostic",
        visualization = { selected -> VehicleLightingVisualization(controls, selected) },
    )
}

/** Pure metadata construction kept out of the recomposing route body. */
internal fun buildVehicleLightingMetadata(
    titles: Array<String>,
    descriptions: Array<String>,
    sections: Array<String>,
    modeLabels: Map<Int, String>,
    limitations: String,
    dependencies: String,
): List<VehicleControlUiMetadata> =
    VEHICLE_LIGHTING_DEFINITIONS.map { definition ->
        VehicleControlUiMetadata(
            key = definition.id.name,
            categoryKey = definition.section.name,
            section = sections[definition.section.ordinal],
            title = titles[definition.id.ordinal],
            summary = descriptions[definition.id.ordinal],
            info = descriptions[definition.id.ordinal],
            limitations = limitations,
            dependencies = dependencies,
            illustrationRes = lightingArtwork(definition.id),
            // Every definition is an AAOS `*_SWITCH` INT32 enum (VehicleLightSwitch):
            // OFF / AUTOMATIC / ON / FLASH. Enum options come from VHAL-discovered
            // supported values, so vendors exposing a subset render only that subset.
            editor = VehicleEditorUiKind.ENUM,
            enumLabels = modeLabels,
            valueLabel = { value -> modeLabels[(value as? Number)?.toInt()] ?: value.toString() },
        )
    }

private fun lightingArtwork(id: VehicleLightingId): Int =
    when (id) {
        VehicleLightingId.HEADLIGHTS -> R.drawable.guide_lighting_headlights
        VehicleLightingId.HIGH_BEAM -> R.drawable.guide_lighting_high_beam
        VehicleLightingId.FOG_LIGHTS -> R.drawable.guide_lighting_fog_lights
        VehicleLightingId.HAZARD_LIGHTS -> R.drawable.guide_lighting_hazard_lights
        VehicleLightingId.CABIN_LIGHTS -> R.drawable.guide_lighting_cabin_lights
        VehicleLightingId.READING_LIGHTS -> R.drawable.guide_lighting_reading_lights
        VehicleLightingId.STEERING_WHEEL_LIGHTS -> R.drawable.guide_lighting_steering_wheel_lights
        VehicleLightingId.FOOTWELL_LIGHTS -> R.drawable.guide_lighting_footwell_lights
    }

@Composable
private fun lightingAreaLabel(areaId: Int): String =
    when (areaId) {
        0 -> stringResource(R.string.vehicle_zone_global)
        VehicleAreaSeat.SEAT_ROW_1_LEFT -> stringResource(R.string.zone_front_left)
        VehicleAreaSeat.SEAT_ROW_1_RIGHT -> stringResource(R.string.zone_front_right)
        VehicleAreaSeat.SEAT_ROW_2_LEFT -> stringResource(R.string.zone_rear_left)
        VehicleAreaSeat.SEAT_ROW_2_CENTER -> stringResource(R.string.zone_rear_center)
        VehicleAreaSeat.SEAT_ROW_2_RIGHT -> stringResource(R.string.zone_rear_right)
        else -> stringResource(R.string.vehicle_zone_id_format, areaId)
    }
