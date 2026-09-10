package com.android.car.settings.feature.hvac.data

import android.car.VehicleAreaSeat
import android.car.VehicleAreaWindow
import android.car.VehiclePropertyIds
import android.car.VehicleUnit
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.core.vehicle.VehicleConnectionState
import com.android.car.settings.core.vehicle.VehicleFeatureController
import com.android.car.settings.core.vehicle.VehicleFeatureControllerFactory
import com.android.car.settings.core.vehicle.VehicleFeatureDefinition
import com.android.car.settings.core.vehicle.VehicleFeatureState
import com.android.car.settings.core.vehicle.VehiclePropertySpec
import com.android.car.settings.core.vehicle.VehiclePropertyStatus
import com.android.car.settings.core.vehicle.VehicleUxPolicyState
import com.android.car.settings.feature.hvac.domain.ClimateCapability
import com.android.car.settings.feature.hvac.domain.ClimateControl
import com.android.car.settings.feature.hvac.domain.ClimateControlId
import com.android.car.settings.feature.hvac.domain.ClimateControlKind
import com.android.car.settings.feature.hvac.domain.ClimateState
import com.android.car.settings.feature.hvac.domain.ClimateValueStatus
import com.android.car.settings.feature.hvac.domain.ClimateZone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** HVAC adapter backed by the single shared core:vehicle Car connection and event pipeline. */
@Singleton
internal class AndroidHvacPlatform
    @Inject
    constructor(
        factory: VehicleFeatureControllerFactory,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) : HvacPlatform {
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val controller: VehicleFeatureController =
            factory.create(
                scope,
                CONTROL_SPECS
                    .distinctBy(ControlSpec::controllerKey)
                    .map { spec ->
                        VehicleFeatureDefinition(
                            key = spec.controllerKey,
                            spec =
                                when (spec.kind) {
                                    ClimateControlKind.TOGGLE -> VehiclePropertySpec.boolean(spec.propertyId)
                                    ClimateControlKind.FLOAT_RANGE,
                                    ClimateControlKind.READ_ONLY_FLOAT,
                                    -> VehiclePropertySpec.float(spec.propertyId)
                                    ClimateControlKind.INT_RANGE,
                                    ClimateControlKind.INT_OPTIONS,
                                    -> VehiclePropertySpec.int(spec.propertyId)
                                },
                            requiresUnrestrictedUx =
                                spec.kind != ClimateControlKind.READ_ONLY_FLOAT,
                        )
                    },
            )

        override val state: StateFlow<ClimateState> =
            controller.state
                .map(::toClimateState)
                .stateIn(scope, SharingStarted.Eagerly, ClimateState())

        override suspend fun refresh(): ActionResult {
            controller.refresh()
            return ActionResult.Success
        }

        override suspend fun setBoolean(
            key: String,
            value: Boolean,
        ): ActionResult = mutate(key) { spec, areaId -> controller.setBoolean(spec.controllerKey, areaId, value) }

        override suspend fun setInt(
            key: String,
            value: Int,
        ): ActionResult = mutate(key) { spec, areaId -> controller.setInt(spec.controllerKey, areaId, value) }

        override suspend fun setFloat(
            key: String,
            value: Float,
        ): ActionResult = mutate(key) { spec, areaId -> controller.setFloat(spec.controllerKey, areaId, value) }

        private fun mutate(
            key: String,
            action: (ControlSpec, Int) -> Unit,
        ): ActionResult =
            runCatching {
                val id = ClimateControlId.valueOf(key.substringBefore(':'))
                val areaId = key.substringAfter(':').toInt()
                val spec = CONTROL_SPECS.first { it.id == id && it.supportsArea(areaId) }
                android.util.Log.d(
                    "MsaVehicleWrite",
                    "hvac mutate key=$key property=0x${Integer.toHexString(spec.propertyId)} area=0x${Integer.toHexString(areaId)}",
                )
                action(spec, areaId)
            }.fold(
                onSuccess = { ActionResult.Success },
                onFailure = {
                    android.util.Log.w(
                        "MsaVehicleWrite",
                        "hvac mutate failed key=$key",
                        it,
                    )
                    ActionResult.Failure(
                        it.message ?: "This climate control is no longer available",
                        it,
                    )
                },
            )
    }

private fun toClimateState(state: VehicleFeatureState): ClimateState {
    val controls =
        state.controls
            .filter { it.supported }
            .flatMap { property ->
                property.areas.mapNotNull { area ->
                    val spec =
                        CONTROL_SPECS.firstOrNull {
                            it.controllerKey == property.definition.key && it.supportsArea(area.area.areaId)
                        } ?: return@mapNotNull null
                    val capability =
                        ClimateCapability(
                            id = spec.id,
                            propertyId = spec.propertyId,
                            zone =
                                ClimateZone(
                                    area.area.areaId,
                                    zoneTitle(spec, area.area.areaId),
                                    property.areaType,
                                ),
                            kind = spec.kind,
                            writable = area.access.canWrite,
                            readable = area.access.canRead,
                            areaType = property.areaType,
                            min = (area.minValue as? Number)?.toFloat(),
                            max = (area.maxValue as? Number)?.toFloat(),
                            step =
                                if (spec.id == ClimateControlId.TEMPERATURE_SET) {
                                    property.configArray
                                        .getOrNull(2)
                                        ?.takeIf { it > 0 }
                                        ?.div(10f)
                                } else {
                                    null
                                },
                            // CarService exposes UNKNOWN (0) in the available-value list for
                            // HVAC_FAN_DIRECTION, but explicitly rejects it on SET.  Do not
                            // render a control that can only produce a deterministic error.
                            options =
                                area.supportedEnumValues
                                    .mapNotNull { (it as? Number)?.toInt() }
                                    .filterNot { spec.id == ClimateControlId.FAN_DIRECTION && it == 0 },
                            optionLabels =
                                area.supportedEnumValues
                                    .mapNotNull { (it as? Number)?.toInt() }
                                    .filterNot { spec.id == ClimateControlId.FAN_DIRECTION && it == 0 }
                                    .associateWith { optionLabel(spec, it) },
                        )
                    ClimateControl(
                        key = "${spec.id.name}:${area.area.areaId}",
                        capability = capability,
                        title = spec.title,
                        section = spec.section,
                        status =
                            when {
                                area.pending -> ClimateValueStatus.PENDING
                                area.error != null -> ClimateValueStatus.ERROR
                                area.status == VehiclePropertyStatus.AVAILABLE ->
                                    ClimateValueStatus.AVAILABLE
                                else -> ClimateValueStatus.UNAVAILABLE
                            },
                        unavailableReason = area.error?.description,
                        booleanValue = area.value as? Boolean,
                        intValue = (area.value as? Number)?.toInt(),
                        floatValue = (area.value as? Number)?.toFloat(),
                        observedBooleanValue = area.confirmedValue as? Boolean,
                        observedIntValue = (area.confirmedValue as? Number)?.toInt(),
                        observedFloatValue = (area.confirmedValue as? Number)?.toFloat(),
                        observedTimestampNanos =
                            area.confirmedTimestampNanos.takeIf { it != Long.MIN_VALUE },
                    )
                }
            }
    return ClimateState(
        connected = state.connection is VehicleConnectionState.Connected,
        uxRestricted = state.uxPolicy !is VehicleUxPolicyState.Unrestricted,
        controls = controls,
        lastError =
            state.controls.firstNotNullOfOrNull { it.error?.description }
                ?: if (state.connection is VehicleConnectionState.Connected) {
                    null
                } else {
                    "Vehicle climate service is not connected"
                },
    )
}

private fun optionLabel(
    spec: ControlSpec,
    value: Int,
): String =
    when {
        spec.id == ClimateControlId.TEMPERATURE_DISPLAY_UNITS && value == VehicleUnit.CELSIUS -> "Celsius"
        spec.id == ClimateControlId.TEMPERATURE_DISPLAY_UNITS && value == VehicleUnit.FAHRENHEIT -> "Fahrenheit"
        spec.id == ClimateControlId.FAN_DIRECTION -> "Direction $value"
        else -> value.toString()
    }

private fun zoneTitle(
    spec: ControlSpec,
    areaId: Int,
): String =
    when (spec.id) {
        ClimateControlId.FRONT_DEFROSTER -> "Front windshield"
        ClimateControlId.REAR_DEFROSTER -> "Rear windshield"
        ClimateControlId.MAX_DEFROST -> "All climate zones"
        else -> seatAreaTitle(areaId)
    }

private fun seatAreaTitle(areaId: Int): String {
    if (areaId == 0) return "Global"
    val seats =
        listOf(
            VehicleAreaSeat.SEAT_ROW_1_LEFT to "Driver",
            VehicleAreaSeat.SEAT_ROW_1_CENTER to "Front center",
            VehicleAreaSeat.SEAT_ROW_1_RIGHT to "Front passenger",
            VehicleAreaSeat.SEAT_ROW_2_LEFT to "Rear left",
            VehicleAreaSeat.SEAT_ROW_2_CENTER to "Rear center",
            VehicleAreaSeat.SEAT_ROW_2_RIGHT to "Rear right",
            VehicleAreaSeat.SEAT_ROW_3_LEFT to "Third row left",
            VehicleAreaSeat.SEAT_ROW_3_CENTER to "Third row center",
            VehicleAreaSeat.SEAT_ROW_3_RIGHT to "Third row right",
        ).filter { (mask, _) -> areaId and mask != 0 }.map { it.second }
    return seats.takeIf(List<String>::isNotEmpty)?.joinToString(" + ")
        ?: "Area 0x${areaId.toString(16)}"
}

private data class ControlSpec(
    val id: ClimateControlId,
    val propertyId: Int,
    val title: String,
    val section: String,
    val kind: ClimateControlKind,
    val controllerKey: String = id.name,
    val supportsArea: (Int) -> Boolean = { true },
)

private val CONTROL_SPECS =
    listOf(
        ControlSpec(ClimateControlId.POWER, VehiclePropertyIds.HVAC_POWER_ON, "Climate power", "System", ClimateControlKind.TOGGLE),
        ControlSpec(ClimateControlId.AUTO, VehiclePropertyIds.HVAC_AUTO_ON, "Automatic climate", "System", ClimateControlKind.TOGGLE),
        ControlSpec(ClimateControlId.DUAL, VehiclePropertyIds.HVAC_DUAL_ON, "Dual / Sync", "System", ClimateControlKind.TOGGLE),
        ControlSpec(
            ClimateControlId.TEMPERATURE_DISPLAY_UNITS,
            VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS,
            "Temperature units",
            "System",
            ClimateControlKind.INT_OPTIONS,
        ),
        ControlSpec(
            ClimateControlId.TEMPERATURE_SET,
            VehiclePropertyIds.HVAC_TEMPERATURE_SET,
            "Set temperature",
            "Temperature",
            ClimateControlKind.FLOAT_RANGE,
        ),
        ControlSpec(
            ClimateControlId.TEMPERATURE_CURRENT,
            VehiclePropertyIds.HVAC_TEMPERATURE_CURRENT,
            "Current temperature",
            "Temperature",
            ClimateControlKind.READ_ONLY_FLOAT,
        ),
        ControlSpec(ClimateControlId.FAN_SPEED, VehiclePropertyIds.HVAC_FAN_SPEED, "Fan speed", "Airflow", ClimateControlKind.INT_RANGE),
        ControlSpec(
            ClimateControlId.FAN_DIRECTION,
            VehiclePropertyIds.HVAC_FAN_DIRECTION,
            "Fan direction",
            "Airflow",
            ClimateControlKind.INT_OPTIONS,
        ),
        ControlSpec(ClimateControlId.AC, VehiclePropertyIds.HVAC_AC_ON, "A/C", "Airflow", ClimateControlKind.TOGGLE),
        ControlSpec(ClimateControlId.MAX_AC, VehiclePropertyIds.HVAC_MAX_AC_ON, "Max A/C", "Airflow", ClimateControlKind.TOGGLE),
        ControlSpec(
            ClimateControlId.RECIRCULATION,
            VehiclePropertyIds.HVAC_RECIRC_ON,
            "Recirculation",
            "Airflow",
            ClimateControlKind.TOGGLE,
        ),
        ControlSpec(
            ClimateControlId.AUTO_RECIRCULATION,
            VehiclePropertyIds.HVAC_AUTO_RECIRC_ON,
            "Automatic recirculation",
            "Airflow",
            ClimateControlKind.TOGGLE,
        ),
        ControlSpec(
            ClimateControlId.FRONT_DEFROSTER,
            VehiclePropertyIds.HVAC_DEFROSTER,
            "Front defroster",
            "Defrost and heat",
            ClimateControlKind.TOGGLE,
            "DEFROSTER",
        ) {
            it ==
                VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD
        },
        ControlSpec(
            ClimateControlId.REAR_DEFROSTER,
            VehiclePropertyIds.HVAC_DEFROSTER,
            "Rear defroster",
            "Defrost and heat",
            ClimateControlKind.TOGGLE,
            "DEFROSTER",
        ) {
            it ==
                VehicleAreaWindow.WINDOW_REAR_WINDSHIELD
        },
        ControlSpec(
            ClimateControlId.MAX_DEFROST,
            VehiclePropertyIds.HVAC_MAX_DEFROST_ON,
            "Max defrost",
            "Defrost and heat",
            ClimateControlKind.TOGGLE,
        ),
        ControlSpec(
            ClimateControlId.MIRROR_HEAT,
            VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT,
            "Mirror heat",
            "Defrost and heat",
            ClimateControlKind.INT_RANGE,
        ),
        ControlSpec(
            ClimateControlId.SEAT_TEMPERATURE,
            VehiclePropertyIds.HVAC_SEAT_TEMPERATURE,
            "Seat heating / cooling",
            "Seat comfort",
            ClimateControlKind.INT_RANGE,
        ),
        ControlSpec(
            ClimateControlId.SEAT_VENTILATION,
            VehiclePropertyIds.HVAC_SEAT_VENTILATION,
            "Seat ventilation",
            "Seat comfort",
            ClimateControlKind.INT_RANGE,
        ),
        ControlSpec(
            ClimateControlId.STEERING_WHEEL_HEAT,
            VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT,
            "Steering-wheel heat",
            "Seat comfort",
            ClimateControlKind.INT_RANGE,
        ),
    )
