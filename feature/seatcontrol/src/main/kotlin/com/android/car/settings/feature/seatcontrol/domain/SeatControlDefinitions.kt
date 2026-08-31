package com.android.car.settings.feature.seatcontrol.domain

import android.car.VehiclePropertyIds
import com.android.car.settings.core.vehicle.VehiclePropertyValueType

enum class SeatControlId {
    FORE_AFT,
    HEIGHT,
    DEPTH,
    TILT,
    BACKREST_PRIMARY,
    BACKREST_SECONDARY,
    LUMBAR_FORE_AFT,
    LUMBAR_VERTICAL,
    LUMBAR_SIDE_SUPPORT,
    CUSHION_SIDE_SUPPORT,
    HEADREST_HEIGHT,
    HEADREST_ANGLE,
    HEADREST_FORE_AFT,
    EASY_ACCESS,
    OCCUPANCY,
    SEAT_BELT,
    MEMORY_RECALL,
    MEMORY_SAVE,
    STEERING_WHEEL_DEPTH,
    STEERING_WHEEL_HEIGHT,
}

enum class SeatControlKind { SWITCH, RANGE, SLOT_ACTION, STATUS }

enum class SeatControlSection { SEAT_POSITION, SUPPORT_AND_HEADREST, MEMORY_AND_ACCESS, STATUS, STEERING_WHEEL }

data class SeatControlDefinition(
    val id: SeatControlId,
    val propertyId: Int,
    val valueType: VehiclePropertyValueType,
    val kind: SeatControlKind,
    val section: SeatControlSection,
    val requiresUnrestrictedUx: Boolean = kind != SeatControlKind.STATUS,
)

val SEAT_CONTROL_DEFINITIONS =
    listOf(
        intRange(SeatControlId.FORE_AFT, VehiclePropertyIds.SEAT_FORE_AFT_POS, SeatControlSection.SEAT_POSITION),
        intRange(SeatControlId.HEIGHT, VehiclePropertyIds.SEAT_HEIGHT_POS, SeatControlSection.SEAT_POSITION),
        intRange(SeatControlId.DEPTH, VehiclePropertyIds.SEAT_DEPTH_POS, SeatControlSection.SEAT_POSITION),
        intRange(SeatControlId.TILT, VehiclePropertyIds.SEAT_TILT_POS, SeatControlSection.SEAT_POSITION),
        intRange(SeatControlId.BACKREST_PRIMARY, VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_POS, SeatControlSection.SEAT_POSITION),
        intRange(SeatControlId.BACKREST_SECONDARY, VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_POS, SeatControlSection.SEAT_POSITION),
        intRange(SeatControlId.LUMBAR_FORE_AFT, VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_POS, SeatControlSection.SUPPORT_AND_HEADREST),
        intRange(SeatControlId.LUMBAR_VERTICAL, VehiclePropertyIds.SEAT_LUMBAR_VERTICAL_POS, SeatControlSection.SUPPORT_AND_HEADREST),
        intRange(
            SeatControlId.LUMBAR_SIDE_SUPPORT,
            VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_POS,
            SeatControlSection.SUPPORT_AND_HEADREST,
        ),
        intRange(
            SeatControlId.CUSHION_SIDE_SUPPORT,
            VehiclePropertyIds.SEAT_CUSHION_SIDE_SUPPORT_POS,
            SeatControlSection.SUPPORT_AND_HEADREST,
        ),
        intRange(SeatControlId.HEADREST_HEIGHT, VehiclePropertyIds.SEAT_HEADREST_HEIGHT_POS_V2, SeatControlSection.SUPPORT_AND_HEADREST),
        intRange(SeatControlId.HEADREST_ANGLE, VehiclePropertyIds.SEAT_HEADREST_ANGLE_POS, SeatControlSection.SUPPORT_AND_HEADREST),
        intRange(SeatControlId.HEADREST_FORE_AFT, VehiclePropertyIds.SEAT_HEADREST_FORE_AFT_POS, SeatControlSection.SUPPORT_AND_HEADREST),
        SeatControlDefinition(
            SeatControlId.EASY_ACCESS,
            VehiclePropertyIds.SEAT_EASY_ACCESS_ENABLED,
            VehiclePropertyValueType.BOOLEAN,
            SeatControlKind.SWITCH,
            SeatControlSection.MEMORY_AND_ACCESS,
        ),
        status(SeatControlId.OCCUPANCY, VehiclePropertyIds.SEAT_OCCUPANCY, VehiclePropertyValueType.INT),
        status(SeatControlId.SEAT_BELT, VehiclePropertyIds.SEAT_BELT_BUCKLED, VehiclePropertyValueType.BOOLEAN),
        slotAction(SeatControlId.MEMORY_RECALL, VehiclePropertyIds.SEAT_MEMORY_SELECT),
        slotAction(SeatControlId.MEMORY_SAVE, VehiclePropertyIds.SEAT_MEMORY_SET),
        intRange(SeatControlId.STEERING_WHEEL_DEPTH, VehiclePropertyIds.STEERING_WHEEL_DEPTH_POS, SeatControlSection.STEERING_WHEEL),
        intRange(SeatControlId.STEERING_WHEEL_HEIGHT, VehiclePropertyIds.STEERING_WHEEL_HEIGHT_POS, SeatControlSection.STEERING_WHEEL),
    )

private fun intRange(
    id: SeatControlId,
    propertyId: Int,
    section: SeatControlSection,
) = SeatControlDefinition(id, propertyId, VehiclePropertyValueType.INT, SeatControlKind.RANGE, section)

private fun status(
    id: SeatControlId,
    propertyId: Int,
    type: VehiclePropertyValueType,
) = SeatControlDefinition(id, propertyId, type, SeatControlKind.STATUS, SeatControlSection.STATUS)

private fun slotAction(
    id: SeatControlId,
    propertyId: Int,
) = SeatControlDefinition(
    id,
    propertyId,
    VehiclePropertyValueType.INT,
    SeatControlKind.SLOT_ACTION,
    SeatControlSection.MEMORY_AND_ACCESS,
)
