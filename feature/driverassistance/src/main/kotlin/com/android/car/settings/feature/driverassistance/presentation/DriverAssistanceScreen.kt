package com.android.car.settings.feature.driverassistance.presentation

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.VehicleControlUiModel
import com.android.car.settings.core.ui.VehicleEditorUiKind
import com.android.car.settings.core.ui.VehicleEnumOption
import com.android.car.settings.core.ui.VehicleFeatureScreen
import com.android.car.settings.core.ui.VehicleSliderUiKind
import com.android.car.settings.core.ui.VehicleSliderUiSpec
import com.android.car.settings.core.ui.VehicleObservationStatus
import com.android.car.settings.core.ui.VehicleObservedSnapshot
import com.android.car.settings.core.ui.VehicleVisualizationSource
import com.android.car.settings.core.ui.VehicleVisualBinding
import com.android.car.settings.core.ui.VehicleVisualMeaning
import com.android.car.settings.core.ui.VehicleZoneOption
import com.android.car.settings.core.ui.toVehicleVisualPolicy
import com.android.car.settings.core.vehicle.VehicleConnectionState
import com.android.car.settings.core.vehicle.VehicleFeatureAreaState
import com.android.car.settings.core.vehicle.VehicleFeatureState
import com.android.car.settings.core.vehicle.VehiclePropertyAccess
import com.android.car.settings.core.vehicle.VehiclePropertyArea
import com.android.car.settings.core.vehicle.VehiclePropertyError
import com.android.car.settings.core.vehicle.VehiclePropertyStatus
import com.android.car.settings.core.vehicle.VehicleUxPolicyState
import com.android.car.settings.feature.driverassistance.R
import com.android.car.settings.feature.driverassistance.domain.DRIVER_ASSISTANCE_DEFINITIONS
import com.android.car.settings.feature.driverassistance.domain.DriverAssistanceControlKind
import com.android.car.settings.feature.driverassistance.domain.DriverAssistanceDefinition
import com.android.car.settings.feature.driverassistance.domain.DriverAssistanceId
import kotlin.math.roundToInt
import com.android.car.settings.core.ui.R as CoreUiR

@Composable
fun DriverAssistanceRoute(
    viewModel: DriverAssistanceViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalContext.current.resources
    val titles = resources.getStringArray(R.array.driver_assistance_titles)
    val descriptions = resources.getStringArray(R.array.driver_assistance_descriptions)
    val sections = resources.getStringArray(R.array.driver_assistance_sections)
    val levelLabels = resources.getStringArray(R.array.driver_assistance_level_labels)
    val laneModeLabels = resources.getStringArray(R.array.driver_assistance_lane_mode_labels)
    val profileLabels = resources.getStringArray(R.array.driver_assistance_profile_labels)
    val limitations = stringResource(R.string.driver_assistance_limitations)
    val dependencies = stringResource(R.string.driver_assistance_dependencies)
    val unavailable = stringResource(R.string.driver_assistance_unavailable)

    val controls =
        remember(
            state,
            titles.contentHashCode(),
            descriptions.contentHashCode(),
            sections.contentHashCode(),
            levelLabels.contentHashCode(),
            laneModeLabels.contentHashCode(),
            profileLabels.contentHashCode(),
            limitations,
            dependencies,
            unavailable,
        ) {
            mapDriverAssistanceControls(
                state = state,
                titles = titles,
                descriptions = descriptions,
                sections = sections,
                levelLabels = levelLabels,
                laneModeLabels = laneModeLabels,
                profileLabels = profileLabels,
                limitations = limitations,
                dependencies = dependencies,
                unavailable = unavailable,
            )
        }

    VehicleFeatureScreen(
        title = stringResource(R.string.driver_assistance_title),
        titleIconRes = CoreUiR.drawable.ic_vehicle_driver_assistance,
        destinationKey = DRIVER_ASSISTANCE_ROUTE,
        loading = state.loading,
        connected = state.connection is VehicleConnectionState.Connected,
        restricted = state.uxPolicy !is VehicleUxPolicyState.Unrestricted,
        controls = controls,
        zones = listOf(VehicleZoneOption(0, stringResource(R.string.vehicle_zone_global))),
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSetBoolean = viewModel::setBoolean,
        onSetInt = viewModel::setInt,
        zoneSelectorLabel = stringResource(R.string.vehicle_zone_selector),
        connectingMessage = stringResource(R.string.vehicle_service_connecting),
        emptyMessage = stringResource(R.string.driver_assistance_empty),
        restrictedReason = stringResource(R.string.vehicle_control_restricted),
        visualizationSource = VehicleVisualizationSource.ILLUSTRATION,
        visualizationLabel = "Illustration / demo — not live sensor data",
        visualization = { selected, policy -> DriverAssistanceVisualization(selected, visualPolicy = policy) },
        guideVisualization = { selected, progress ->
            DriverAssistanceGuideVisualization(selected, progress)
        },
        visualPolicy = state.uxPolicy.toVehicleVisualPolicy(),
    )
}

/** Pure domain-to-presentation mapping kept outside the Compose route. */
internal fun mapDriverAssistanceControls(
    state: VehicleFeatureState,
    titles: Array<String>,
    descriptions: Array<String>,
    sections: Array<String>,
    levelLabels: Array<String>,
    laneModeLabels: Array<String>,
    profileLabels: Array<String>,
    limitations: String,
    dependencies: String,
    unavailable: String,
): List<VehicleControlUiModel> =
    state.controls.flatMap { control ->
        val id = DriverAssistanceId.valueOf(control.definition.key)
        val definition = DRIVER_ASSISTANCE_DEFINITIONS.first { it.id == id }
        val areas =
            control.areas.ifEmpty {
                listOf(
                    VehicleFeatureAreaState(
                        area = VehiclePropertyArea.GLOBAL,
                        access = VehiclePropertyAccess.NONE,
                        error = control.error,
                    ),
                )
            }
        areas.map { area ->
            val value = area.value
            val numeric = (value as? Number)?.toFloat()
            val min = (area.minValue as? Number)?.toFloat()
            val max = (area.maxValue as? Number)?.toFloat()
            val enumValues = area.supportedEnumValues.mapNotNull { (it as? Number)?.toInt() }
            VehicleControlUiModel(
                key = control.definition.key,
                propertyId = definition.propertyId,
                areaId = area.area.areaId,
                section = sections[definition.section.ordinal],
                categoryKey = definition.section.name,
                title = titles[id.ordinal],
                summary = descriptions[id.ordinal],
                info = descriptions[id.ordinal],
                limitations = limitations,
                dependencies = dependencies,
                illustrationRes = driverAssistanceArtwork(id),
                editor = definition.controlKind.toEditor(),
                readable = area.access.canRead,
                writable = area.access.canWrite,
                supported = control.supported,
                available =
                    control.supported &&
                        area.status == VehiclePropertyStatus.AVAILABLE &&
                        value != null &&
                        (
                            definition.controlKind != DriverAssistanceControlKind.INTEGER_RANGE ||
                                (min != null && max != null && max > min)
                        ) &&
                        (
                            definition.controlKind != DriverAssistanceControlKind.INTEGER_ENUM ||
                                enumValues.isNotEmpty()
                        ),
                pending = area.pending,
                booleanValue = value as? Boolean,
                numericValue = numeric,
                valueLabel = numeric?.roundToInt()?.let { definition.valueLabel(it) }.orEmpty(),
                range = if (min != null && max != null && max > min) min..max else 0f..1f,
                steps =
                    if (min != null && max != null) {
                        (max - min).roundToInt().minus(1).coerceAtLeast(0)
                    } else {
                        0
                    },
                sliderUiSpec = driverAssistanceSliderUiSpec(id),
                selectedEnumKey = numeric?.roundToInt()?.toString(),
                enumOptions =
                    enumValues.map { enumValue ->
                        VehicleEnumOption(
                            key = enumValue.toString(),
                            label = enumLabel(id, enumValue, levelLabels, laneModeLabels, profileLabels),
                        )
                    },
                errorMessage = (area.error ?: control.error)?.toMessage(unavailable),
                requiresUnrestrictedUx = control.definition.requiresUnrestrictedUx,
                observedSnapshot =
                    when {
                        !area.access.canRead -> VehicleObservedSnapshot(status = VehicleObservationStatus.UNKNOWN)
                        area.error != null || control.error != null || area.status == VehiclePropertyStatus.ERROR ->
                            VehicleObservedSnapshot(status = VehicleObservationStatus.ERROR)
                        area.status == VehiclePropertyStatus.UNAVAILABLE || area.confirmedValue == null ->
                            VehicleObservedSnapshot(status = VehicleObservationStatus.UNAVAILABLE)
                        area.confirmedValue is Boolean ->
                            VehicleObservedSnapshot(
                                booleanValue = area.confirmedValue as Boolean,
                                status = VehicleObservationStatus.CONFIRMED,
                                timestampNanos = area.confirmedTimestampNanos.takeIf { it != Long.MIN_VALUE },
                            )
                        area.confirmedValue is Number ->
                            VehicleObservedSnapshot(
                                numericValue = (area.confirmedValue as Number).toFloat(),
                                enumValue = (area.confirmedValue as Number).toInt(),
                                status = VehicleObservationStatus.CONFIRMED,
                                timestampNanos = area.confirmedTimestampNanos.takeIf { it != Long.MIN_VALUE },
                            )
                        else -> VehicleObservedSnapshot(status = VehicleObservationStatus.UNKNOWN)
                    },
                visualBinding = driverAssistanceVisualBinding(id),
            )
        }
    }

private fun driverAssistanceVisualBinding(id: DriverAssistanceId): VehicleVisualBinding {
    val guideSceneId =
        when (id) {
            DriverAssistanceId.BLIND_SPOT_WARNING -> "adas_blind_spot_warning"
            DriverAssistanceId.CROSS_TRAFFIC_MONITORING -> "adas_cross_traffic_monitoring"
            DriverAssistanceId.FORWARD_COLLISION_WARNING -> "adas_forward_collision_warning"
            DriverAssistanceId.AUTOMATIC_EMERGENCY_BRAKING -> "adas_automatic_emergency_braking"
            DriverAssistanceId.LANE_DEPARTURE_WARNING -> "adas_lane_departure_warning"
            DriverAssistanceId.LANE_KEEP_ASSIST -> "adas_lane_keep_assist"
            DriverAssistanceId.LANE_CENTERING_ASSIST -> "adas_lane_centering_assist"
            DriverAssistanceId.FRONT_PARKING_ASSISTANCE,
            DriverAssistanceId.REAR_PARKING_ASSISTANCE,
            -> "adas_parking_assistance"
            else -> null
        }
    return VehicleVisualBinding(
        previewSceneId = "adas_context_top_view",
        guideSceneId = guideSceneId,
        meaning =
            if (guideSceneId == null) {
                VehicleVisualMeaning.CONTEXT_ONLY
            } else {
                VehicleVisualMeaning.INSTRUCTIONAL
            },
    )
}

internal fun driverAssistanceSliderUiSpec(id: DriverAssistanceId): VehicleSliderUiSpec =
    when (id) {
        DriverAssistanceId.SPEED_LIMIT_OFFSET ->
            VehicleSliderUiSpec(
                kind = VehicleSliderUiKind.OFFSET,
                showTicks = true,
                centerMarker = 0f,
            )
        DriverAssistanceId.WARNING_VOLUME,
        DriverAssistanceId.PARKING_WARNING_VOLUME,
        -> VehicleSliderUiSpec(kind = VehicleSliderUiKind.LEVEL, showTicks = true)
        else -> VehicleSliderUiSpec()
    }

@DrawableRes
@Suppress("CyclomaticComplexMethod")
internal fun driverAssistanceArtwork(id: DriverAssistanceId): Int =
    when (id) {
        DriverAssistanceId.ELECTRONIC_STABILITY_CONTROL ->
            R.drawable.guide_adas_electronic_stability_control
        DriverAssistanceId.AUTOMATIC_EMERGENCY_BRAKING ->
            R.drawable.guide_adas_automatic_emergency_braking
        DriverAssistanceId.FORWARD_COLLISION_WARNING ->
            R.drawable.guide_adas_forward_collision_warning
        DriverAssistanceId.BLIND_SPOT_WARNING -> R.drawable.guide_adas_blind_spot_warning
        DriverAssistanceId.LANE_DEPARTURE_WARNING ->
            R.drawable.guide_adas_lane_departure_warning
        DriverAssistanceId.LANE_KEEP_ASSIST -> R.drawable.guide_adas_lane_keep_assist
        DriverAssistanceId.LANE_CENTERING_ASSIST -> R.drawable.guide_adas_lane_centering_assist
        DriverAssistanceId.EMERGENCY_LANE_KEEP_ASSIST ->
            R.drawable.guide_adas_emergency_lane_keep_assist
        DriverAssistanceId.CRUISE_CONTROL -> R.drawable.guide_adas_cruise_control
        DriverAssistanceId.HANDS_ON_DETECTION -> R.drawable.guide_adas_hands_on_detection
        DriverAssistanceId.DRIVER_DROWSINESS_ATTENTION_SYSTEM ->
            R.drawable.guide_adas_driver_drowsiness_attention_system
        DriverAssistanceId.DRIVER_DROWSINESS_ATTENTION_WARNING ->
            R.drawable.guide_adas_driver_drowsiness_attention_warning
        DriverAssistanceId.DRIVER_DISTRACTION_SYSTEM ->
            R.drawable.guide_adas_driver_distraction_system
        DriverAssistanceId.DRIVER_DISTRACTION_WARNING ->
            R.drawable.guide_adas_driver_distraction_warning
        DriverAssistanceId.LOW_SPEED_COLLISION_WARNING ->
            R.drawable.guide_adas_low_speed_collision_warning
        DriverAssistanceId.CROSS_TRAFFIC_MONITORING ->
            R.drawable.guide_adas_cross_traffic_monitoring
        DriverAssistanceId.LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING ->
            R.drawable.guide_adas_low_speed_automatic_emergency_braking
        DriverAssistanceId.LANE_WARNING_SENSITIVITY ->
            R.drawable.guide_adas_lane_warning_sensitivity
        DriverAssistanceId.STEERING_INTERVENTION_STRENGTH ->
            R.drawable.guide_adas_steering_intervention_strength
        DriverAssistanceId.LANE_ALERT_MODE -> R.drawable.guide_adas_lane_alert_mode
        DriverAssistanceId.COLLISION_WARNING_TIMING ->
            R.drawable.guide_adas_collision_warning_timing
        DriverAssistanceId.ACC_ACCELERATION_PROFILE ->
            R.drawable.guide_adas_acc_acceleration_profile
        DriverAssistanceId.SPEED_LIMIT_WARNING -> R.drawable.guide_adas_speed_limit_warning
        DriverAssistanceId.SPEED_LIMIT_OFFSET -> R.drawable.guide_adas_speed_limit_offset
        DriverAssistanceId.WARNING_VOLUME -> R.drawable.guide_adas_warning_volume
        DriverAssistanceId.FRONT_PARKING_ASSISTANCE ->
            R.drawable.guide_adas_front_parking_assistance
        DriverAssistanceId.REAR_PARKING_ASSISTANCE ->
            R.drawable.guide_adas_rear_parking_assistance
        DriverAssistanceId.PARKING_WARNING_VOLUME ->
            R.drawable.guide_adas_parking_warning_volume
        DriverAssistanceId.SAFE_EXIT_ASSIST -> R.drawable.guide_adas_safe_exit_assist
        DriverAssistanceId.DRIVER_ASSISTANCE_PROFILE ->
            R.drawable.guide_adas_driver_assistance_profile
    }

private fun DriverAssistanceControlKind.toEditor(): VehicleEditorUiKind =
    when (this) {
        DriverAssistanceControlKind.SWITCH -> VehicleEditorUiKind.SWITCH
        DriverAssistanceControlKind.INTEGER_RANGE -> VehicleEditorUiKind.SLIDER
        DriverAssistanceControlKind.INTEGER_ENUM -> VehicleEditorUiKind.ENUM
    }

private fun DriverAssistanceDefinition.valueLabel(value: Int): String =
    when (id) {
        DriverAssistanceId.SPEED_LIMIT_OFFSET -> if (value > 0) "+$value km/h" else "$value km/h"
        else -> value.toString()
    }

private fun enumLabel(
    id: DriverAssistanceId,
    value: Int,
    levelLabels: Array<String>,
    laneModeLabels: Array<String>,
    profileLabels: Array<String>,
): String =
    when (id) {
        DriverAssistanceId.LANE_ALERT_MODE -> laneModeLabels.getOrNull(value)
        DriverAssistanceId.DRIVER_ASSISTANCE_PROFILE -> profileLabels.getOrNull(value)
        else -> levelLabels.getOrNull(value)
    } ?: value.toString()

private fun VehiclePropertyError.toMessage(fallback: String): String =
    when (this) {
        is VehiclePropertyError.Unsupported -> fallback
        is VehiclePropertyError.Unavailable -> fallback
        is VehiclePropertyError.PermissionDenied -> description
        is VehiclePropertyError.ServiceUnavailable -> description
        is VehiclePropertyError.Timeout -> description
        is VehiclePropertyError.InvalidArea -> description
        is VehiclePropertyError.TypeMismatch -> description
        is VehiclePropertyError.InvalidValue -> description
        is VehiclePropertyError.WriteNotAllowed -> description
        is VehiclePropertyError.UxRestricted -> description
        is VehiclePropertyError.WriteRejected -> description
    }
