package com.android.car.settings.feature.hvac.presentation

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.VehicleControlUiModel
import com.android.car.settings.core.ui.VehicleEditorUiKind
import com.android.car.settings.core.ui.VehicleEnumOption
import com.android.car.settings.core.ui.VehicleFeatureScreen
import com.android.car.settings.core.ui.VehicleObservationStatus
import com.android.car.settings.core.ui.VehicleObservedSnapshot
import com.android.car.settings.core.ui.VehicleSliderUiKind
import com.android.car.settings.core.ui.VehicleSliderUiSpec
import com.android.car.settings.core.ui.VehicleVisualBinding
import com.android.car.settings.core.ui.VehicleVisualMeaning
import com.android.car.settings.core.ui.VehicleVisualPolicy
import com.android.car.settings.core.ui.VehicleVisualizationSource
import com.android.car.settings.core.ui.VehicleZoneOption
import com.android.car.settings.feature.hvac.R
import com.android.car.settings.feature.hvac.domain.ClimateControl
import com.android.car.settings.feature.hvac.domain.ClimateControlId
import com.android.car.settings.feature.hvac.domain.ClimateControlKind
import com.android.car.settings.feature.hvac.domain.ClimateState
import com.android.car.settings.feature.hvac.domain.ClimateValueStatus
import kotlin.math.roundToInt
import com.android.car.settings.core.ui.R as CoreUiR

private const val CATEGORY_SYSTEM = "SYSTEM"
private const val CATEGORY_TEMPERATURE = "TEMPERATURE"
private const val CATEGORY_AIRFLOW = "AIRFLOW"
private const val CATEGORY_AIR_QUALITY = "AIR_QUALITY"
private const val CATEGORY_DEFROST = "DEFROST_HEATING"
private const val CATEGORY_COMFORT = "COMFORT"

internal data class ClimatePresentation(
    val id: ClimateControlId,
    val title: String,
    val categoryKey: String,
    val categoryTitle: String,
    val expectedKind: ClimateControlKind,
    val info: String,
    val illustrationRes: Int? = null,
)

@Composable
fun HvacRoute(
    viewModel: HvacViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HvacScreen(
        state = uiState.climate,
        isRefreshing = uiState.isRefreshing,
        message = uiState.message,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSetBoolean = viewModel::setBoolean,
        onSetInt = viewModel::setInt,
        onSetFloat = viewModel::setFloat,
        onDismissMessage = viewModel::clearMessage,
    )
}

@Composable
internal fun HvacScreen(
    state: ClimateState,
    isRefreshing: Boolean,
    message: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetBoolean: (String, Boolean) -> Unit,
    onSetInt: (String, Int) -> Unit,
    onSetFloat: (String, Float) -> Unit,
    onDismissMessage: () -> Unit,
) {
    val limitations = stringResource(R.string.hvac_limitations)
    val dependencies = stringResource(R.string.hvac_dependencies)
    val unavailable = stringResource(CoreUiR.string.vehicle_control_unavailable)
    val onLabel = stringResource(CoreUiR.string.vehicle_control_on)
    val offLabel = stringResource(CoreUiR.string.vehicle_control_off)
    val presentations = climatePresentations()
    val controls =
        mapClimateControls(
            state = state,
            presentations = presentations,
            limitations = limitations,
            dependencies = dependencies,
            onLabel = onLabel,
            offLabel = offLabel,
            unavailableLabel = unavailable,
            transientMessage = message,
        )
    val occupantZoneControls =
        setOf(
            ClimateControlId.TEMPERATURE_SET,
            ClimateControlId.TEMPERATURE_CURRENT,
            ClimateControlId.FAN_SPEED,
            ClimateControlId.FAN_DIRECTION,
            ClimateControlId.AC,
            ClimateControlId.SEAT_TEMPERATURE,
            ClimateControlId.SEAT_VENTILATION,
        )
    val discoveredZones =
        state.controls
            .filter { it.capability.id in occupantZoneControls }
            .map { it.capability.zone }
            .distinctBy { it.areaType to it.areaId }
            .filterNot { it.areaId == 0 }
            .sortedBy { it.areaId }
            .map {
                VehicleZoneOption(
                    areaId = it.areaId,
                    label = it.title,
                    areaType = it.areaType,
                )
            }
    val zones =
        discoveredZones.ifEmpty {
            listOf(VehicleZoneOption(0, stringResource(R.string.hvac_global_zone)))
        }

    VehicleFeatureScreen(
        title = stringResource(R.string.hvac_title),
        titleIconRes = CoreUiR.drawable.ic_vehicle_climate,
        destinationKey = HVAC_ROUTE,
        loading = isRefreshing,
        connected = state.connected,
        restricted = state.uxRestricted,
        controls = controls,
        zones = zones,
        onBack = onBack,
        onRefresh = {
            onDismissMessage()
            onRefresh()
        },
        onSetBoolean = { key, _, value -> onSetBoolean(key, value) },
        onSetInt = { key, _, value -> onSetInt(key, value) },
        onSetFloat = { key, _, value -> onSetFloat(key, value) },
        zoneSelectorLabel = stringResource(R.string.hvac_zone_selector),
        connectingMessage = state.lastError ?: stringResource(R.string.hvac_connecting),
        emptyMessage = stringResource(R.string.hvac_empty),
        restrictedReason = stringResource(R.string.hvac_restricted),
        showVehicleDiagram = discoveredZones.size > 1,
        visualizationSource = VehicleVisualizationSource.LIVE_PROPERTY,
        visualizationLabel = stringResource(R.string.hvac_visualization_label),
        visualization = { selected, policy -> HvacVisualization(controls, selected, visualPolicy = policy) },
        guideVisualization = { selected, progress -> HvacGuideVisualization(selected, progress) },
        visualPolicy =
            if (state.uxRestricted) {
                VehicleVisualPolicy(false, false, false, stringResource(R.string.hvac_restricted))
            } else {
                VehicleVisualPolicy(true, true, true)
            },
    )
}

@Composable
private fun climatePresentations(): List<ClimatePresentation> =
    listOf(
        climatePresentation(
            ClimateControlId.POWER,
            R.string.hvac_power,
            CATEGORY_SYSTEM,
            R.string.hvac_category_system,
            ClimateControlKind.TOGGLE,
        ),
        climatePresentation(
            ClimateControlId.AUTO,
            R.string.hvac_auto,
            CATEGORY_SYSTEM,
            R.string.hvac_category_system,
            ClimateControlKind.TOGGLE,
        ),
        climatePresentation(
            ClimateControlId.DUAL,
            R.string.hvac_dual,
            CATEGORY_SYSTEM,
            R.string.hvac_category_system,
            ClimateControlKind.TOGGLE,
        ),
        climatePresentation(
            ClimateControlId.TEMPERATURE_DISPLAY_UNITS,
            R.string.hvac_temperature_units,
            CATEGORY_SYSTEM,
            R.string.hvac_category_system,
            ClimateControlKind.INT_OPTIONS,
        ),
        climatePresentation(
            ClimateControlId.TEMPERATURE_SET,
            R.string.hvac_temperature_set,
            CATEGORY_TEMPERATURE,
            R.string.hvac_category_temperature,
            ClimateControlKind.FLOAT_RANGE,
        ),
        climatePresentation(
            ClimateControlId.TEMPERATURE_CURRENT,
            R.string.hvac_temperature_current,
            CATEGORY_TEMPERATURE,
            R.string.hvac_category_temperature,
            ClimateControlKind.READ_ONLY_FLOAT,
        ),
        climatePresentation(
            ClimateControlId.FAN_SPEED,
            R.string.hvac_fan_speed,
            CATEGORY_AIRFLOW,
            R.string.hvac_category_airflow,
            ClimateControlKind.INT_RANGE,
        ),
        climatePresentation(
            ClimateControlId.FAN_DIRECTION,
            R.string.hvac_fan_direction,
            CATEGORY_AIRFLOW,
            R.string.hvac_category_airflow,
            ClimateControlKind.INT_OPTIONS,
        ),
        climatePresentation(
            ClimateControlId.AC,
            R.string.hvac_ac,
            CATEGORY_AIRFLOW,
            R.string.hvac_category_airflow,
            ClimateControlKind.TOGGLE,
        ),
        climatePresentation(
            ClimateControlId.MAX_AC,
            R.string.hvac_max_ac,
            CATEGORY_AIRFLOW,
            R.string.hvac_category_airflow,
            ClimateControlKind.TOGGLE,
        ),
        climatePresentation(
            ClimateControlId.RECIRCULATION,
            R.string.hvac_recirculation,
            CATEGORY_AIR_QUALITY,
            R.string.hvac_category_air_quality,
            ClimateControlKind.TOGGLE,
        ),
        climatePresentation(
            ClimateControlId.AUTO_RECIRCULATION,
            R.string.hvac_auto_recirculation,
            CATEGORY_AIR_QUALITY,
            R.string.hvac_category_air_quality,
            ClimateControlKind.TOGGLE,
        ),
        climatePresentation(
            ClimateControlId.FRONT_DEFROSTER,
            R.string.hvac_front_defroster,
            CATEGORY_DEFROST,
            R.string.hvac_category_defrost,
            ClimateControlKind.TOGGLE,
        ),
        climatePresentation(
            ClimateControlId.REAR_DEFROSTER,
            R.string.hvac_rear_defroster,
            CATEGORY_DEFROST,
            R.string.hvac_category_defrost,
            ClimateControlKind.TOGGLE,
        ),
        climatePresentation(
            ClimateControlId.MAX_DEFROST,
            R.string.hvac_max_defrost,
            CATEGORY_DEFROST,
            R.string.hvac_category_defrost,
            ClimateControlKind.TOGGLE,
        ),
        climatePresentation(
            ClimateControlId.MIRROR_HEAT,
            R.string.hvac_mirror_heat,
            CATEGORY_DEFROST,
            R.string.hvac_category_defrost,
            ClimateControlKind.INT_RANGE,
        ),
        climatePresentation(
            ClimateControlId.SEAT_TEMPERATURE,
            R.string.hvac_seat_temperature,
            CATEGORY_COMFORT,
            R.string.hvac_category_comfort,
            ClimateControlKind.INT_RANGE,
        ),
        climatePresentation(
            ClimateControlId.SEAT_VENTILATION,
            R.string.hvac_seat_ventilation,
            CATEGORY_COMFORT,
            R.string.hvac_category_comfort,
            ClimateControlKind.INT_RANGE,
        ),
        climatePresentation(
            ClimateControlId.STEERING_WHEEL_HEAT,
            R.string.hvac_steering_heat,
            CATEGORY_COMFORT,
            R.string.hvac_category_comfort,
            ClimateControlKind.INT_RANGE,
        ),
    )

@Composable
private fun climatePresentation(
    id: ClimateControlId,
    titleRes: Int,
    categoryKey: String,
    categoryTitleRes: Int,
    kind: ClimateControlKind,
): ClimatePresentation {
    val title = stringResource(titleRes)
    return ClimatePresentation(
        id = id,
        title = title,
        categoryKey = categoryKey,
        categoryTitle = stringResource(categoryTitleRes),
        expectedKind = kind,
        info = "$title. ${stringResource(categoryTitleRes)}.",
        illustrationRes = hvacArtwork(id),
    )
}

internal fun mapClimateControls(
    state: ClimateState,
    presentations: List<ClimatePresentation>,
    limitations: String,
    dependencies: String,
    onLabel: String,
    offLabel: String,
    unavailableLabel: String,
    transientMessage: String? = null,
): List<VehicleControlUiModel> {
    val presentationsById = presentations.associateBy(ClimatePresentation::id)
    val discoveredIds = state.controls.map { it.capability.id }.toSet()
    val discovered =
        state.controls.mapNotNull { control ->
            val presentation = presentationsById[control.capability.id] ?: return@mapNotNull null
            control.toVehicleControl(
                presentation = presentation,
                limitations = limitations,
                dependencies = dependencies,
                onLabel = onLabel,
                offLabel = offLabel,
                unavailableLabel = unavailableLabel,
            )
        }
    val missing =
        presentations
            .filterNot { it.id in discoveredIds }
            .map { presentation ->
                VehicleControlUiModel(
                    key = presentation.id.name,
                    propertyId = 0,
                    areaId = 0,
                    section = presentation.categoryTitle,
                    categoryKey = presentation.categoryKey,
                    title = presentation.title,
                    summary = unavailableLabel,
                    info = presentation.info,
                    limitations = limitations,
                    dependencies = dependencies,
                    illustrationRes = presentation.illustrationRes,
                    editor = presentation.expectedKind.toVehicleEditor(),
                    readable = false,
                    writable = false,
                    supported = false,
                    available = false,
                    pending = false,
                    usesFloatSlider = presentation.expectedKind == ClimateControlKind.FLOAT_RANGE,
                    sliderUiSpec = climateSliderUiSpec(presentation.id),
                    errorMessage = null,
                )
            }
    val result = discovered + missing
    return if (transientMessage == null || result.isEmpty()) {
        result
    } else {
        result.mapIndexed { index, control ->
            if (index == 0) control.copy(errorMessage = transientMessage) else control
        }
    }
}

@Suppress("CyclomaticComplexMethod")
private fun ClimateControl.toVehicleControl(
    presentation: ClimatePresentation,
    limitations: String,
    dependencies: String,
    onLabel: String,
    offLabel: String,
    unavailableLabel: String,
): VehicleControlUiModel {
    val kind = capability.kind
    val numeric = floatValue ?: intValue?.toFloat()
    val min = capability.min
    val max = capability.max
    val rangeValid =
        kind !in setOf(ClimateControlKind.FLOAT_RANGE, ClimateControlKind.INT_RANGE) ||
            (min != null && max != null && max > min)
    val optionsValid = kind != ClimateControlKind.INT_OPTIONS || capability.options.isNotEmpty()
    val hasValue =
        when (kind) {
            ClimateControlKind.TOGGLE -> booleanValue != null
            ClimateControlKind.FLOAT_RANGE, ClimateControlKind.READ_ONLY_FLOAT -> floatValue != null
            ClimateControlKind.INT_RANGE, ClimateControlKind.INT_OPTIONS -> intValue != null
        }
    val statusAllowsValue =
        status == ClimateValueStatus.AVAILABLE || status == ClimateValueStatus.PENDING
    val valueLabel =
        when (kind) {
            ClimateControlKind.TOGGLE -> if (booleanValue == true) onLabel else offLabel
            ClimateControlKind.FLOAT_RANGE, ClimateControlKind.READ_ONLY_FLOAT ->
                floatValue?.let { "%.1f".format(it) }.orEmpty()
            ClimateControlKind.INT_OPTIONS ->
                intValue?.let { capability.optionLabels[it] ?: it.toString() }.orEmpty()
            ClimateControlKind.INT_RANGE -> intValue?.toString().orEmpty()
        }
    return VehicleControlUiModel(
        key = key,
        propertyId = capability.propertyId,
        areaId = capability.zone.areaId,
        section = presentation.categoryTitle,
        categoryKey = presentation.categoryKey,
        title = presentation.title,
        summary = "${presentation.title} · ${capability.zone.title}",
        info = presentation.info,
        limitations = limitations,
        dependencies = dependencies,
        illustrationRes = presentation.illustrationRes,
        editor = kind.toVehicleEditor(),
        readable = capability.readable,
        areaType = capability.areaType,
        writable = capability.writable,
        supported = true,
        available = statusAllowsValue && hasValue && rangeValid && optionsValid,
        pending = status == ClimateValueStatus.PENDING,
        booleanValue = booleanValue,
        numericValue = numeric,
        usesFloatSlider = kind == ClimateControlKind.FLOAT_RANGE,
        valueLabel = valueLabel,
        range = if (min != null && max != null && max > min) min..max else 0f..1f,
        steps =
            when {
                min == null || max == null -> 0
                kind == ClimateControlKind.FLOAT_RANGE ->
                    (((max - min) * 2f).roundToInt() - 1).coerceAtLeast(0)
                else -> ((max - min).roundToInt() - 1).coerceAtLeast(0)
            },
        sliderUiSpec = climateSliderUiSpec(presentation.id),
        selectedEnumKey = intValue?.toString(),
        enumOptions =
            capability.options.map { option ->
                VehicleEnumOption(
                    key = option.toString(),
                    label = capability.optionLabels[option] ?: option.toString(),
                )
            },
        errorMessage =
            when (status) {
                ClimateValueStatus.ERROR -> unavailableReason ?: unavailableLabel
                else -> null
            },
        observedSnapshot =
            when {
                !capability.readable ->
                    VehicleObservedSnapshot(status = VehicleObservationStatus.UNKNOWN)
                status == ClimateValueStatus.ERROR ->
                    VehicleObservedSnapshot(status = VehicleObservationStatus.ERROR)
                status == ClimateValueStatus.UNAVAILABLE ->
                    VehicleObservedSnapshot(status = VehicleObservationStatus.UNAVAILABLE)
                capability.kind == ClimateControlKind.TOGGLE &&
                    observedBooleanValue != null ->
                    VehicleObservedSnapshot(
                        booleanValue = observedBooleanValue,
                        status = VehicleObservationStatus.CONFIRMED,
                        timestampNanos = observedTimestampNanos,
                    )
                capability.kind != ClimateControlKind.TOGGLE &&
                    observedFloatValue != null &&
                    observedFloatValue.isFinite() ->
                    VehicleObservedSnapshot(
                        numericValue = observedFloatValue,
                        enumValue = observedIntValue.takeIf { capability.kind == ClimateControlKind.INT_OPTIONS },
                        status = VehicleObservationStatus.CONFIRMED,
                        timestampNanos = observedTimestampNanos,
                    )
                else -> VehicleObservedSnapshot(status = VehicleObservationStatus.UNKNOWN)
            },
        visualBinding = climateVisualBinding(presentation.id),
    )
}

private fun climateVisualBinding(id: ClimateControlId): VehicleVisualBinding =
    VehicleVisualBinding(
        previewSceneId = "climate_cabin_top_view",
        guideSceneId =
            when (id) {
                ClimateControlId.FAN_DIRECTION,
                ClimateControlId.FAN_SPEED,
                ClimateControlId.RECIRCULATION,
                ClimateControlId.AUTO_RECIRCULATION,
                ClimateControlId.FRONT_DEFROSTER,
                ClimateControlId.REAR_DEFROSTER,
                ClimateControlId.MAX_DEFROST,
                -> "climate_${id.name.lowercase()}"
                else -> null
            },
        meaning =
            if (id == ClimateControlId.TEMPERATURE_CURRENT) {
                VehicleVisualMeaning.OBSERVED_STATE
            } else {
                VehicleVisualMeaning.CONFIRMED_SETTING
            },
    )

internal fun climateSliderUiSpec(id: ClimateControlId): VehicleSliderUiSpec =
    when (id) {
        ClimateControlId.TEMPERATURE_SET,
        ClimateControlId.MIRROR_HEAT,
        ClimateControlId.SEAT_TEMPERATURE,
        ClimateControlId.STEERING_WHEEL_HEAT,
        -> VehicleSliderUiSpec(kind = VehicleSliderUiKind.THERMAL)
        ClimateControlId.FAN_SPEED,
        ClimateControlId.SEAT_VENTILATION,
        -> VehicleSliderUiSpec(kind = VehicleSliderUiKind.LEVEL, showTicks = true)
        else -> VehicleSliderUiSpec()
    }

private fun ClimateControlKind.toVehicleEditor(): VehicleEditorUiKind =
    when (this) {
        ClimateControlKind.TOGGLE -> VehicleEditorUiKind.SWITCH
        ClimateControlKind.FLOAT_RANGE, ClimateControlKind.INT_RANGE -> VehicleEditorUiKind.SLIDER
        ClimateControlKind.INT_OPTIONS -> VehicleEditorUiKind.ENUM
        ClimateControlKind.READ_ONLY_FLOAT -> VehicleEditorUiKind.STATUS
    }

@DrawableRes
internal fun hvacArtwork(id: ClimateControlId): Int =
    when (id) {
        ClimateControlId.POWER -> R.drawable.guide_hvac_power
        ClimateControlId.TEMPERATURE_SET -> R.drawable.guide_hvac_temperature_set
        ClimateControlId.TEMPERATURE_CURRENT -> R.drawable.guide_hvac_temperature_current
        ClimateControlId.TEMPERATURE_DISPLAY_UNITS -> R.drawable.guide_hvac_temperature_display_units
        ClimateControlId.FAN_SPEED -> R.drawable.guide_hvac_fan_speed
        ClimateControlId.FAN_DIRECTION -> R.drawable.guide_hvac_fan_direction
        ClimateControlId.AC -> R.drawable.guide_hvac_ac
        ClimateControlId.MAX_AC -> R.drawable.guide_hvac_max_ac
        ClimateControlId.AUTO -> R.drawable.guide_hvac_auto
        ClimateControlId.RECIRCULATION -> R.drawable.guide_hvac_recirculation
        ClimateControlId.AUTO_RECIRCULATION -> R.drawable.guide_hvac_auto_recirculation
        ClimateControlId.DUAL -> R.drawable.guide_hvac_dual
        ClimateControlId.FRONT_DEFROSTER -> R.drawable.guide_hvac_front_defroster
        ClimateControlId.REAR_DEFROSTER -> R.drawable.guide_hvac_rear_defroster
        ClimateControlId.MAX_DEFROST -> R.drawable.guide_hvac_max_defrost
        ClimateControlId.MIRROR_HEAT -> R.drawable.guide_hvac_mirror_heat
        ClimateControlId.SEAT_TEMPERATURE -> R.drawable.guide_hvac_seat_temperature
        ClimateControlId.SEAT_VENTILATION -> R.drawable.guide_hvac_seat_ventilation
        ClimateControlId.STEERING_WHEEL_HEAT -> R.drawable.guide_hvac_steering_wheel_heat
    }
