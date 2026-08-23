package com.android.car.settings.feature.vehiclelighting.domain

import android.car.VehiclePropertyIds

enum class VehicleLightingId {
    HEADLIGHTS,
    HIGH_BEAM,
    FOG_LIGHTS,
    HAZARD_LIGHTS,
    CABIN_LIGHTS,
    READING_LIGHTS,
    STEERING_WHEEL_LIGHTS,
    FOOTWELL_LIGHTS,
}

enum class VehicleLightingSection { EXTERIOR, SAFETY, INTERIOR }

data class VehicleLightingDefinition(
    val id: VehicleLightingId,
    val propertyId: Int,
    val section: VehicleLightingSection,
)

val VEHICLE_LIGHTING_DEFINITIONS =
    listOf(
        VehicleLightingDefinition(VehicleLightingId.HEADLIGHTS, VehiclePropertyIds.HEADLIGHTS_SWITCH, VehicleLightingSection.EXTERIOR),
        VehicleLightingDefinition(VehicleLightingId.HIGH_BEAM, VehiclePropertyIds.HIGH_BEAM_LIGHTS_SWITCH, VehicleLightingSection.EXTERIOR),
        VehicleLightingDefinition(VehicleLightingId.FOG_LIGHTS, VehiclePropertyIds.FOG_LIGHTS_SWITCH, VehicleLightingSection.EXTERIOR),
        VehicleLightingDefinition(VehicleLightingId.HAZARD_LIGHTS, VehiclePropertyIds.HAZARD_LIGHTS_SWITCH, VehicleLightingSection.SAFETY),
        VehicleLightingDefinition(VehicleLightingId.CABIN_LIGHTS, VehiclePropertyIds.CABIN_LIGHTS_SWITCH, VehicleLightingSection.INTERIOR),
        VehicleLightingDefinition(
            VehicleLightingId.READING_LIGHTS,
            VehiclePropertyIds.READING_LIGHTS_SWITCH,
            VehicleLightingSection.INTERIOR,
        ),
        VehicleLightingDefinition(
            VehicleLightingId.STEERING_WHEEL_LIGHTS,
            VehiclePropertyIds.STEERING_WHEEL_LIGHTS_SWITCH,
            VehicleLightingSection.INTERIOR,
        ),
        VehicleLightingDefinition(
            VehicleLightingId.FOOTWELL_LIGHTS,
            VehiclePropertyIds.SEAT_FOOTWELL_LIGHTS_SWITCH,
            VehicleLightingSection.INTERIOR,
        ),
    )
