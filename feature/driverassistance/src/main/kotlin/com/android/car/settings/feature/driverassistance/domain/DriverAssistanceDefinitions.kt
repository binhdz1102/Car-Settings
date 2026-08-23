package com.android.car.settings.feature.driverassistance.domain

import android.car.VehiclePropertyIds
import com.android.car.settings.core.vehicle.VehiclePropertyValueType

enum class DriverAssistanceId {
    ELECTRONIC_STABILITY_CONTROL,
    AUTOMATIC_EMERGENCY_BRAKING,
    FORWARD_COLLISION_WARNING,
    BLIND_SPOT_WARNING,
    LANE_DEPARTURE_WARNING,
    LANE_KEEP_ASSIST,
    LANE_CENTERING_ASSIST,
    EMERGENCY_LANE_KEEP_ASSIST,
    CRUISE_CONTROL,
    HANDS_ON_DETECTION,
    DRIVER_DROWSINESS_ATTENTION_SYSTEM,
    DRIVER_DROWSINESS_ATTENTION_WARNING,
    DRIVER_DISTRACTION_SYSTEM,
    DRIVER_DISTRACTION_WARNING,
    LOW_SPEED_COLLISION_WARNING,
    CROSS_TRAFFIC_MONITORING,
    LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING,
    LANE_WARNING_SENSITIVITY,
    STEERING_INTERVENTION_STRENGTH,
    LANE_ALERT_MODE,
    COLLISION_WARNING_TIMING,
    ACC_ACCELERATION_PROFILE,
    SPEED_LIMIT_WARNING,
    SPEED_LIMIT_OFFSET,
    WARNING_VOLUME,
    FRONT_PARKING_ASSISTANCE,
    REAR_PARKING_ASSISTANCE,
    PARKING_WARNING_VOLUME,
    SAFE_EXIT_ASSIST,
    DRIVER_ASSISTANCE_PROFILE,
}

enum class DriverAssistanceSource {
    SYSTEM,
    VENDOR,
}

enum class DriverAssistanceControlKind {
    SWITCH,
    INTEGER_RANGE,
    INTEGER_ENUM,
}

data class DriverAssistanceDefinition(
    val id: DriverAssistanceId,
    val propertyId: Int,
    val valueType: VehiclePropertyValueType,
    val controlKind: DriverAssistanceControlKind,
    val source: DriverAssistanceSource,
    val section: DriverAssistanceSection,
    val uxRestricted: Boolean = false,
)

enum class DriverAssistanceSection {
    VEHICLE_STABILITY,
    COLLISION_AVOIDANCE,
    LANE_SUPPORT,
    DRIVER_MONITORING,
    CRUISE_AND_SPEED,
    PARKING_AND_EXIT,
    PROFILE_AND_FEEDBACK,
}

/**
 * Complete IDs derived locally as VENDOR | GLOBAL | value-type | low-ID. The matching project
 * VHAL config is the source of defaults, ranges and supported enum values.
 */
object VendorDriverAssistancePropertyIds {
    const val LANE_WARNING_SENSITIVITY = 0x21406000
    const val STEERING_INTERVENTION_STRENGTH = 0x21406001
    const val LANE_ALERT_MODE = 0x21406002
    const val COLLISION_WARNING_TIMING = 0x21406003
    const val ACC_ACCELERATION_PROFILE = 0x21406004
    const val SPEED_LIMIT_WARNING_ENABLED = 0x21206005
    const val SPEED_LIMIT_OFFSET = 0x21406006
    const val DRIVER_WARNING_VOLUME = 0x21406007
    const val FRONT_PARKING_ASSIST_ENABLED = 0x21206008
    const val REAR_PARKING_ASSIST_ENABLED = 0x21206009
    const val PARKING_WARNING_VOLUME = 0x2140600A
    const val SAFE_EXIT_ASSIST_ENABLED = 0x2120600B
    const val DRIVER_ASSISTANCE_PROFILE = 0x2140600C
}

val DRIVER_ASSISTANCE_DEFINITIONS: List<DriverAssistanceDefinition> =
    listOf(
        systemSwitch(
            DriverAssistanceId.ELECTRONIC_STABILITY_CONTROL,
            VehiclePropertyIds.ELECTRONIC_STABILITY_CONTROL_ENABLED,
            DriverAssistanceSection.VEHICLE_STABILITY,
        ),
        systemSwitch(
            DriverAssistanceId.AUTOMATIC_EMERGENCY_BRAKING,
            VehiclePropertyIds.AUTOMATIC_EMERGENCY_BRAKING_ENABLED,
            DriverAssistanceSection.COLLISION_AVOIDANCE,
        ),
        systemSwitch(
            DriverAssistanceId.FORWARD_COLLISION_WARNING,
            VehiclePropertyIds.FORWARD_COLLISION_WARNING_ENABLED,
            DriverAssistanceSection.COLLISION_AVOIDANCE,
        ),
        systemSwitch(
            DriverAssistanceId.BLIND_SPOT_WARNING,
            VehiclePropertyIds.BLIND_SPOT_WARNING_ENABLED,
            DriverAssistanceSection.COLLISION_AVOIDANCE,
        ),
        systemSwitch(
            DriverAssistanceId.LANE_DEPARTURE_WARNING,
            VehiclePropertyIds.LANE_DEPARTURE_WARNING_ENABLED,
            DriverAssistanceSection.LANE_SUPPORT,
        ),
        systemSwitch(
            DriverAssistanceId.LANE_KEEP_ASSIST,
            VehiclePropertyIds.LANE_KEEP_ASSIST_ENABLED,
            DriverAssistanceSection.LANE_SUPPORT,
        ),
        systemSwitch(
            DriverAssistanceId.LANE_CENTERING_ASSIST,
            VehiclePropertyIds.LANE_CENTERING_ASSIST_ENABLED,
            DriverAssistanceSection.LANE_SUPPORT,
        ),
        systemSwitch(
            DriverAssistanceId.EMERGENCY_LANE_KEEP_ASSIST,
            VehiclePropertyIds.EMERGENCY_LANE_KEEP_ASSIST_ENABLED,
            DriverAssistanceSection.LANE_SUPPORT,
        ),
        systemSwitch(
            DriverAssistanceId.CRUISE_CONTROL,
            VehiclePropertyIds.CRUISE_CONTROL_ENABLED,
            DriverAssistanceSection.CRUISE_AND_SPEED,
        ),
        systemSwitch(
            DriverAssistanceId.HANDS_ON_DETECTION,
            VehiclePropertyIds.HANDS_ON_DETECTION_ENABLED,
            DriverAssistanceSection.DRIVER_MONITORING,
        ),
        systemSwitch(
            DriverAssistanceId.DRIVER_DROWSINESS_ATTENTION_SYSTEM,
            VehiclePropertyIds.DRIVER_DROWSINESS_ATTENTION_SYSTEM_ENABLED,
            DriverAssistanceSection.DRIVER_MONITORING,
        ),
        systemSwitch(
            DriverAssistanceId.DRIVER_DROWSINESS_ATTENTION_WARNING,
            VehiclePropertyIds.DRIVER_DROWSINESS_ATTENTION_WARNING_ENABLED,
            DriverAssistanceSection.DRIVER_MONITORING,
        ),
        systemSwitch(
            DriverAssistanceId.DRIVER_DISTRACTION_SYSTEM,
            VehiclePropertyIds.DRIVER_DISTRACTION_SYSTEM_ENABLED,
            DriverAssistanceSection.DRIVER_MONITORING,
        ),
        systemSwitch(
            DriverAssistanceId.DRIVER_DISTRACTION_WARNING,
            VehiclePropertyIds.DRIVER_DISTRACTION_WARNING_ENABLED,
            DriverAssistanceSection.DRIVER_MONITORING,
        ),
        systemSwitch(
            DriverAssistanceId.LOW_SPEED_COLLISION_WARNING,
            VehiclePropertyIds.LOW_SPEED_COLLISION_WARNING_ENABLED,
            DriverAssistanceSection.COLLISION_AVOIDANCE,
        ),
        systemSwitch(
            DriverAssistanceId.CROSS_TRAFFIC_MONITORING,
            VehiclePropertyIds.CROSS_TRAFFIC_MONITORING_ENABLED,
            DriverAssistanceSection.COLLISION_AVOIDANCE,
        ),
        systemSwitch(
            DriverAssistanceId.LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING,
            VehiclePropertyIds.LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING_ENABLED,
            DriverAssistanceSection.COLLISION_AVOIDANCE,
        ),
        vendorInt(
            DriverAssistanceId.LANE_WARNING_SENSITIVITY,
            VendorDriverAssistancePropertyIds.LANE_WARNING_SENSITIVITY,
            DriverAssistanceControlKind.INTEGER_ENUM,
            DriverAssistanceSection.LANE_SUPPORT,
            uxRestricted = true,
        ),
        vendorInt(
            DriverAssistanceId.STEERING_INTERVENTION_STRENGTH,
            VendorDriverAssistancePropertyIds.STEERING_INTERVENTION_STRENGTH,
            DriverAssistanceControlKind.INTEGER_ENUM,
            DriverAssistanceSection.LANE_SUPPORT,
            uxRestricted = true,
        ),
        vendorInt(
            DriverAssistanceId.LANE_ALERT_MODE,
            VendorDriverAssistancePropertyIds.LANE_ALERT_MODE,
            DriverAssistanceControlKind.INTEGER_ENUM,
            DriverAssistanceSection.LANE_SUPPORT,
            uxRestricted = true,
        ),
        vendorInt(
            DriverAssistanceId.COLLISION_WARNING_TIMING,
            VendorDriverAssistancePropertyIds.COLLISION_WARNING_TIMING,
            DriverAssistanceControlKind.INTEGER_ENUM,
            DriverAssistanceSection.COLLISION_AVOIDANCE,
            uxRestricted = true,
        ),
        vendorInt(
            DriverAssistanceId.ACC_ACCELERATION_PROFILE,
            VendorDriverAssistancePropertyIds.ACC_ACCELERATION_PROFILE,
            DriverAssistanceControlKind.INTEGER_ENUM,
            DriverAssistanceSection.CRUISE_AND_SPEED,
            uxRestricted = true,
        ),
        vendorSwitch(
            DriverAssistanceId.SPEED_LIMIT_WARNING,
            VendorDriverAssistancePropertyIds.SPEED_LIMIT_WARNING_ENABLED,
            DriverAssistanceSection.CRUISE_AND_SPEED,
        ),
        vendorInt(
            DriverAssistanceId.SPEED_LIMIT_OFFSET,
            VendorDriverAssistancePropertyIds.SPEED_LIMIT_OFFSET,
            DriverAssistanceControlKind.INTEGER_RANGE,
            DriverAssistanceSection.CRUISE_AND_SPEED,
        ),
        vendorInt(
            DriverAssistanceId.WARNING_VOLUME,
            VendorDriverAssistancePropertyIds.DRIVER_WARNING_VOLUME,
            DriverAssistanceControlKind.INTEGER_RANGE,
            DriverAssistanceSection.PROFILE_AND_FEEDBACK,
        ),
        vendorSwitch(
            DriverAssistanceId.FRONT_PARKING_ASSISTANCE,
            VendorDriverAssistancePropertyIds.FRONT_PARKING_ASSIST_ENABLED,
            DriverAssistanceSection.PARKING_AND_EXIT,
        ),
        vendorSwitch(
            DriverAssistanceId.REAR_PARKING_ASSISTANCE,
            VendorDriverAssistancePropertyIds.REAR_PARKING_ASSIST_ENABLED,
            DriverAssistanceSection.PARKING_AND_EXIT,
        ),
        vendorInt(
            DriverAssistanceId.PARKING_WARNING_VOLUME,
            VendorDriverAssistancePropertyIds.PARKING_WARNING_VOLUME,
            DriverAssistanceControlKind.INTEGER_RANGE,
            DriverAssistanceSection.PARKING_AND_EXIT,
        ),
        vendorSwitch(
            DriverAssistanceId.SAFE_EXIT_ASSIST,
            VendorDriverAssistancePropertyIds.SAFE_EXIT_ASSIST_ENABLED,
            DriverAssistanceSection.PARKING_AND_EXIT,
        ),
        vendorInt(
            DriverAssistanceId.DRIVER_ASSISTANCE_PROFILE,
            VendorDriverAssistancePropertyIds.DRIVER_ASSISTANCE_PROFILE,
            DriverAssistanceControlKind.INTEGER_ENUM,
            DriverAssistanceSection.PROFILE_AND_FEEDBACK,
            uxRestricted = true,
        ),
    )

private fun systemSwitch(
    id: DriverAssistanceId,
    propertyId: Int,
    section: DriverAssistanceSection,
) = DriverAssistanceDefinition(
    id = id,
    propertyId = propertyId,
    valueType = VehiclePropertyValueType.BOOLEAN,
    controlKind = DriverAssistanceControlKind.SWITCH,
    source = DriverAssistanceSource.SYSTEM,
    section = section,
    uxRestricted = true,
)

private fun vendorSwitch(
    id: DriverAssistanceId,
    propertyId: Int,
    section: DriverAssistanceSection,
) = DriverAssistanceDefinition(
    id = id,
    propertyId = propertyId,
    valueType = VehiclePropertyValueType.BOOLEAN,
    controlKind = DriverAssistanceControlKind.SWITCH,
    source = DriverAssistanceSource.VENDOR,
    section = section,
    uxRestricted = true,
)

private fun vendorInt(
    id: DriverAssistanceId,
    propertyId: Int,
    controlKind: DriverAssistanceControlKind,
    section: DriverAssistanceSection,
    uxRestricted: Boolean = true,
) = DriverAssistanceDefinition(
    id = id,
    propertyId = propertyId,
    valueType = VehiclePropertyValueType.INT,
    controlKind = controlKind,
    source = DriverAssistanceSource.VENDOR,
    section = section,
    uxRestricted = uxRestricted,
)
