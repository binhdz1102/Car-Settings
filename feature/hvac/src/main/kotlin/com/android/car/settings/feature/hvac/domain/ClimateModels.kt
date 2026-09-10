package com.android.car.settings.feature.hvac.domain

import com.android.car.settings.core.vehicle.VehiclePropertyAreaType

/** A vehicle area discovered from the VHAL instead of assumed from a specific car layout. */
data class ClimateZone(
    val areaId: Int,
    val title: String,
    val areaType: VehiclePropertyAreaType = VehiclePropertyAreaType.UNKNOWN,
)

enum class ClimateControlId {
    POWER,
    TEMPERATURE_SET,
    TEMPERATURE_CURRENT,
    TEMPERATURE_DISPLAY_UNITS,
    FAN_SPEED,
    FAN_DIRECTION,
    AC,
    MAX_AC,
    AUTO,
    RECIRCULATION,
    AUTO_RECIRCULATION,
    DUAL,
    FRONT_DEFROSTER,
    REAR_DEFROSTER,
    MAX_DEFROST,
    MIRROR_HEAT,
    SEAT_TEMPERATURE,
    SEAT_VENTILATION,
    STEERING_WHEEL_HEAT,
}

enum class ClimateControlKind {
    TOGGLE,
    FLOAT_RANGE,
    INT_RANGE,
    INT_OPTIONS,
    READ_ONLY_FLOAT,
}

enum class ClimateValueStatus {
    AVAILABLE,
    PENDING,
    UNAVAILABLE,
    ERROR,
}

/**
 * Runtime capability of a property in one vehicle area. The UI must not render a control that
 * is absent from this list.
 */
data class ClimateCapability(
    val id: ClimateControlId,
    val propertyId: Int,
    val zone: ClimateZone,
    val kind: ClimateControlKind,
    val writable: Boolean,
    val readable: Boolean = true,
    val areaType: VehiclePropertyAreaType = zone.areaType,
    val min: Float? = null,
    val max: Float? = null,
    val step: Float? = null,
    val options: List<Int> = emptyList(),
    val optionLabels: Map<Int, String> = emptyMap(),
)

data class ClimateControl(
    val key: String,
    val capability: ClimateCapability,
    val title: String,
    val section: String,
    val status: ClimateValueStatus = ClimateValueStatus.UNAVAILABLE,
    val unavailableReason: String? = null,
    val booleanValue: Boolean? = null,
    val intValue: Int? = null,
    val floatValue: Float? = null,
    val observedBooleanValue: Boolean? = null,
    val observedIntValue: Int? = null,
    val observedFloatValue: Float? = null,
    val observedTimestampNanos: Long? = null,
)

data class ClimateState(
    val connected: Boolean = false,
    val uxRestricted: Boolean = false,
    val controls: List<ClimateControl> = emptyList(),
    val lastError: String? = null,
)

data class ClimateCommandResult(
    val key: String,
    val accepted: Boolean,
    val message: String? = null,
)

fun ClimateControl.displayValue(): String =
    when (capability.kind) {
        ClimateControlKind.TOGGLE -> if (booleanValue == true) "On" else "Off"
        ClimateControlKind.FLOAT_RANGE,
        ClimateControlKind.READ_ONLY_FLOAT,
        -> floatValue?.let { "%.1f".format(it) } ?: "--"
        ClimateControlKind.INT_RANGE,
        ClimateControlKind.INT_OPTIONS,
        -> intValue?.toString() ?: "--"
    }
