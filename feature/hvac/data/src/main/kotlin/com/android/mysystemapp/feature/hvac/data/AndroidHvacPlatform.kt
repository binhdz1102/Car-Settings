package com.android.car.settings.feature.hvac.data

import android.car.Car
import android.car.VehicleAreaSeat
import android.car.VehicleAreaWindow
import android.car.VehiclePropertyIds
import android.car.VehicleUnit
import android.car.hardware.CarPropertyConfig
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.feature.hvac.domain.ClimateCapability
import com.android.car.settings.feature.hvac.domain.ClimateControl
import com.android.car.settings.feature.hvac.domain.ClimateControlId
import com.android.car.settings.feature.hvac.domain.ClimateControlKind
import com.android.car.settings.feature.hvac.domain.ClimateState
import com.android.car.settings.feature.hvac.domain.ClimateValueStatus
import com.android.car.settings.feature.hvac.domain.ClimateZone
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * VHAL-backed implementation. It discovers only properties and areas advertised by the current
 * vehicle, so the same APK can run on different Automotive configurations without fake controls.
 */
@Singleton
internal class AndroidHvacPlatform @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : HvacPlatform {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(ClimateState())
    private val registeredProperties = mutableSetOf<Int>()
    private val pendingValues = mutableMapOf<String, PendingValue>()

    @Volatile private var carPropertyManager: CarPropertyManager? = null
    private var car: Car? = null

    override val state: StateFlow<ClimateState> = mutableState.asStateFlow()

    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            scope.launch { refreshState() }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            mutableState.update { current ->
                current.copy(
                    controls = current.controls.map { control ->
                        if (control.capability.propertyId == propertyId &&
                            control.capability.zone.areaId == areaId
                        ) {
                            control.copy(
                                status = ClimateValueStatus.ERROR,
                                unavailableReason = "The vehicle rejected this climate command",
                            )
                        } else {
                            control
                        }
                    },
                )
            }
            scope.launch { delay(PENDING_WRITE_TIMEOUT_MS); refreshState() }
        }
    }

    init {
        car = Car.createCar(
            context,
            /* handler= */ null,
            Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
        ) { connectedCar, ready ->
            carPropertyManager = if (ready) {
                connectedCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            } else {
                null
            }
            scope.launch {
                if (ready) subscribeToVehicleChanges()
                refreshState()
            }
        }
    }

    override suspend fun refresh(): ActionResult = withContext(dispatcher) { execute { refreshState() } }

    override suspend fun setBoolean(key: String, value: Boolean): ActionResult =
        mutate(key, value) { manager, control ->
            manager.setBooleanProperty(control.capability.propertyId, control.capability.zone.areaId, value)
        }

    override suspend fun setInt(key: String, value: Int): ActionResult =
        mutate(key, value) { manager, control ->
            manager.setIntProperty(control.capability.propertyId, control.capability.zone.areaId, value)
        }

    override suspend fun setFloat(key: String, value: Float): ActionResult =
        mutate(key, value) { manager, control ->
            manager.setFloatProperty(control.capability.propertyId, control.capability.zone.areaId, value)
        }

    private suspend fun mutate(
        key: String,
        value: Any,
        write: (CarPropertyManager, ClimateControl) -> Unit,
    ): ActionResult = withContext(dispatcher) {
        try {
            val control = mutableState.value.controls.firstOrNull { it.key == key }
                ?: error("This climate control is no longer available")
            check(control.capability.writable) { "This climate control is read only" }
            check(control.status != ClimateValueStatus.UNAVAILABLE) {
                control.unavailableReason ?: "This climate control is unavailable"
            }
            val manager = carPropertyManager ?: error("Vehicle climate service is not connected")
            pendingValues[key] = PendingValue(value, System.currentTimeMillis())
            applyOptimisticValue(key, value)
            write(manager, control)
            scope.launch {
                delay(PENDING_WRITE_TIMEOUT_MS)
                refreshState()
            }
            ActionResult.Success
        } catch (throwable: Throwable) {
            pendingValues.remove(key)
            ActionResult.Failure(throwable.message ?: "Unable to update vehicle climate", throwable)
        }
    }

    private fun applyOptimisticValue(key: String, value: Any) {
        mutableState.update { current ->
            current.copy(
                controls = current.controls.map { control ->
                    if (control.key != key) control else when (value) {
                        is Boolean -> control.copy(
                            booleanValue = value,
                            status = ClimateValueStatus.PENDING,
                            unavailableReason = null,
                        )
                        is Int -> control.copy(
                            intValue = value,
                            status = ClimateValueStatus.PENDING,
                            unavailableReason = null,
                        )
                        is Float -> control.copy(
                            floatValue = value,
                            status = ClimateValueStatus.PENDING,
                            unavailableReason = null,
                        )
                        else -> control
                    }
                },
            )
        }
    }

    private fun subscribeToVehicleChanges() {
        val manager = carPropertyManager ?: return
        CONTROL_SPECS.map(ControlSpec::propertyId).distinct().forEach { propertyId ->
            if (registeredProperties.add(propertyId)) {
                runCatching {
                    manager.registerCallback(
                        propertyCallback,
                        propertyId,
                        CarPropertyManager.SENSOR_RATE_ONCHANGE,
                    )
                }
            }
        }
    }

    private fun refreshState() {
        val manager = carPropertyManager
        if (manager == null) {
            mutableState.value = ClimateState(
                connected = false,
                lastError = "Vehicle climate service is not connected",
            )
            return
        }
        try {
            val configs = manager.propertyList.associateBy { it.propertyId }
            val controls = CONTROL_SPECS.flatMap { spec ->
                val config = configs[spec.propertyId] ?: return@flatMap emptyList()
                val areaIds = config.areaIds.filter { areaId -> spec.supportsArea(areaId) }
                areaIds.map { areaId -> buildControl(manager, config, spec, areaId) }
            }
            mutableState.value = ClimateState(connected = true, controls = controls)
        } catch (throwable: Throwable) {
            mutableState.value = mutableState.value.copy(
                connected = true,
                lastError = throwable.message ?: "Unable to read vehicle climate state",
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildControl(
        manager: CarPropertyManager,
        config: CarPropertyConfig<*>,
        spec: ControlSpec,
        areaId: Int,
    ): ClimateControl {
        val capability = ClimateCapability(
            id = spec.id,
            propertyId = spec.propertyId,
            zone = ClimateZone(areaId, zoneTitle(spec, areaId)),
            kind = spec.kind,
            writable = config.access and CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE != 0,
            min = (config.getMinValue(areaId) as? Number)?.toFloat(),
            max = (config.getMaxValue(areaId) as? Number)?.toFloat(),
            options = optionsFor(manager, config, spec, areaId),
            optionLabels = optionLabelsFor(manager, config, spec, areaId),
        )
        val key = "${spec.id.name}:$areaId"
        val actual = runCatching { manager.getProperty<Any>(spec.propertyId, areaId) }.getOrNull()
        val base = if (actual == null) {
            ClimateControl(
                key = key,
                capability = capability,
                title = spec.title,
                section = spec.section,
                status = ClimateValueStatus.ERROR,
                unavailableReason = "Unable to read this vehicle property",
            )
        } else if (actual.status != CarPropertyValue.STATUS_AVAILABLE) {
            ClimateControl(
                key = key,
                capability = capability,
                title = spec.title,
                section = spec.section,
                status = ClimateValueStatus.UNAVAILABLE,
                unavailableReason = "Unavailable in the current vehicle state",
            )
        } else {
            controlFromValue(key, capability, spec, actual.value)
        }
        return mergePendingValue(base)
    }

    private fun controlFromValue(
        key: String,
        capability: ClimateCapability,
        spec: ControlSpec,
        value: Any?,
    ): ClimateControl = when (spec.kind) {
        ClimateControlKind.TOGGLE -> ClimateControl(
            key = key,
            capability = capability,
            title = spec.title,
            section = spec.section,
            status = ClimateValueStatus.AVAILABLE,
            booleanValue = value as? Boolean,
        )
        ClimateControlKind.FLOAT_RANGE,
        ClimateControlKind.READ_ONLY_FLOAT -> ClimateControl(
            key = key,
            capability = capability,
            title = spec.title,
            section = spec.section,
            status = ClimateValueStatus.AVAILABLE,
            floatValue = (value as? Number)?.toFloat(),
        )
        ClimateControlKind.INT_RANGE,
        ClimateControlKind.INT_OPTIONS -> ClimateControl(
            key = key,
            capability = capability,
            title = spec.title,
            section = spec.section,
            status = ClimateValueStatus.AVAILABLE,
            intValue = (value as? Number)?.toInt(),
        )
    }

    private fun mergePendingValue(control: ClimateControl): ClimateControl {
        val pending = pendingValues[control.key] ?: return control
        if (matches(control, pending.value)) {
            pendingValues.remove(control.key)
            return control
        }
        if (System.currentTimeMillis() - pending.startedAtMs > PENDING_WRITE_TIMEOUT_MS) {
            pendingValues.remove(control.key)
            return control
        }
        return when (val value = pending.value) {
            is Boolean -> control.copy(booleanValue = value, status = ClimateValueStatus.PENDING)
            is Int -> control.copy(intValue = value, status = ClimateValueStatus.PENDING)
            is Float -> control.copy(floatValue = value, status = ClimateValueStatus.PENDING)
            else -> control
        }
    }

    private fun matches(control: ClimateControl, value: Any): Boolean = when (value) {
        is Boolean -> control.booleanValue == value
        is Int -> control.intValue == value
        is Float -> control.floatValue?.let { kotlin.math.abs(it - value) < 0.01f } == true
        else -> false
    }

    private fun optionsFor(
        manager: CarPropertyManager,
        config: CarPropertyConfig<*>,
        spec: ControlSpec,
        areaId: Int,
    ): List<Int> = when (spec.id) {
        ClimateControlId.FAN_DIRECTION -> runCatching {
            manager.getIntArrayProperty(VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE, areaId).toList()
        }.getOrDefault(emptyList())
        ClimateControlId.TEMPERATURE_DISPLAY_UNITS -> config.configArray.filterIsInstance<Int>()
        else -> emptyList()
    }

    private fun optionLabelsFor(
        manager: CarPropertyManager,
        config: CarPropertyConfig<*>,
        spec: ControlSpec,
        areaId: Int,
    ): Map<Int, String> = optionsFor(manager, config, spec, areaId).associateWith { value ->
        when {
            spec.id == ClimateControlId.TEMPERATURE_DISPLAY_UNITS && value == VehicleUnit.CELSIUS -> "Celsius"
            spec.id == ClimateControlId.TEMPERATURE_DISPLAY_UNITS && value == VehicleUnit.FAHRENHEIT -> "Fahrenheit"
            spec.id == ClimateControlId.FAN_DIRECTION -> "Direction $value"
            else -> value.toString()
        }
    }

    private fun zoneTitle(spec: ControlSpec, areaId: Int): String = when (spec.id) {
        ClimateControlId.FRONT_DEFROSTER -> "Front windshield"
        ClimateControlId.REAR_DEFROSTER -> "Rear windshield"
        ClimateControlId.MAX_DEFROST -> "All climate zones"
        else -> seatAreaTitle(areaId)
    }

    private fun seatAreaTitle(areaId: Int): String {
        if (areaId == 0) return "Global"
        val seats = listOf(
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
        return seats.takeIf(List<String>::isNotEmpty)?.joinToString(" + ") ?: "Area 0x${areaId.toString(16)}"
    }

    private fun execute(block: () -> Unit): ActionResult = try {
        block()
        ActionResult.Success
    } catch (throwable: Throwable) {
        ActionResult.Failure(throwable.message ?: "Unable to read vehicle climate", throwable)
    }

    private data class PendingValue(
        val value: Any,
        val startedAtMs: Long,
    )

    private data class ControlSpec(
        val id: ClimateControlId,
        val propertyId: Int,
        val title: String,
        val section: String,
        val kind: ClimateControlKind,
        val supportsArea: (Int) -> Boolean = { true },
    )

    private companion object {
        const val PENDING_WRITE_TIMEOUT_MS = 1_500L

        val CONTROL_SPECS = listOf(
            ControlSpec(ClimateControlId.POWER, VehiclePropertyIds.HVAC_POWER_ON, "Climate power", "System", ClimateControlKind.TOGGLE),
            ControlSpec(ClimateControlId.AUTO, VehiclePropertyIds.HVAC_AUTO_ON, "Automatic climate", "System", ClimateControlKind.TOGGLE),
            ControlSpec(ClimateControlId.DUAL, VehiclePropertyIds.HVAC_DUAL_ON, "Dual / Sync", "System", ClimateControlKind.TOGGLE),
            ControlSpec(ClimateControlId.TEMPERATURE_DISPLAY_UNITS, VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS, "Temperature units", "System", ClimateControlKind.INT_OPTIONS),
            ControlSpec(ClimateControlId.TEMPERATURE_SET, VehiclePropertyIds.HVAC_TEMPERATURE_SET, "Set temperature", "Temperature", ClimateControlKind.FLOAT_RANGE),
            ControlSpec(ClimateControlId.TEMPERATURE_CURRENT, VehiclePropertyIds.HVAC_TEMPERATURE_CURRENT, "Current temperature", "Temperature", ClimateControlKind.READ_ONLY_FLOAT),
            ControlSpec(ClimateControlId.FAN_SPEED, VehiclePropertyIds.HVAC_FAN_SPEED, "Fan speed", "Airflow", ClimateControlKind.INT_RANGE),
            ControlSpec(ClimateControlId.FAN_DIRECTION, VehiclePropertyIds.HVAC_FAN_DIRECTION, "Fan direction", "Airflow", ClimateControlKind.INT_OPTIONS),
            ControlSpec(ClimateControlId.AC, VehiclePropertyIds.HVAC_AC_ON, "A/C", "Airflow", ClimateControlKind.TOGGLE),
            ControlSpec(ClimateControlId.MAX_AC, VehiclePropertyIds.HVAC_MAX_AC_ON, "Max A/C", "Airflow", ClimateControlKind.TOGGLE),
            ControlSpec(ClimateControlId.RECIRCULATION, VehiclePropertyIds.HVAC_RECIRC_ON, "Recirculation", "Airflow", ClimateControlKind.TOGGLE),
            ControlSpec(ClimateControlId.AUTO_RECIRCULATION, VehiclePropertyIds.HVAC_AUTO_RECIRC_ON, "Automatic recirculation", "Airflow", ClimateControlKind.TOGGLE),
            ControlSpec(
                ClimateControlId.FRONT_DEFROSTER,
                VehiclePropertyIds.HVAC_DEFROSTER,
                "Front defroster",
                "Defrost and heat",
                ClimateControlKind.TOGGLE,
            ) { it == VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD },
            ControlSpec(
                ClimateControlId.REAR_DEFROSTER,
                VehiclePropertyIds.HVAC_DEFROSTER,
                "Rear defroster",
                "Defrost and heat",
                ClimateControlKind.TOGGLE,
            ) { it == VehicleAreaWindow.WINDOW_REAR_WINDSHIELD },
            ControlSpec(ClimateControlId.MAX_DEFROST, VehiclePropertyIds.HVAC_MAX_DEFROST_ON, "Max defrost", "Defrost and heat", ClimateControlKind.TOGGLE),
            ControlSpec(ClimateControlId.MIRROR_HEAT, VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT, "Mirror heat", "Defrost and heat", ClimateControlKind.TOGGLE),
            ControlSpec(ClimateControlId.SEAT_TEMPERATURE, VehiclePropertyIds.HVAC_SEAT_TEMPERATURE, "Seat heating / cooling", "Seat comfort", ClimateControlKind.INT_RANGE),
            ControlSpec(ClimateControlId.SEAT_VENTILATION, VehiclePropertyIds.HVAC_SEAT_VENTILATION, "Seat ventilation", "Seat comfort", ClimateControlKind.INT_RANGE),
            ControlSpec(ClimateControlId.STEERING_WHEEL_HEAT, VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT, "Steering-wheel heat", "Seat comfort", ClimateControlKind.INT_RANGE),
        )
    }
}
