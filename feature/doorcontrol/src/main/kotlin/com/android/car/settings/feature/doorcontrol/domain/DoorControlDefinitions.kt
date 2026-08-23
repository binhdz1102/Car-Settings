package com.android.car.settings.feature.doorcontrol.domain

import android.car.VehiclePropertyIds
import com.android.car.settings.core.vehicle.VehiclePropertyValueType

enum class DoorControlId {
    DOOR_LOCK,
    CHILD_LOCK,
    DOOR_POSITION,
    DOOR_MOVEMENT,
    WINDOW_LOCK,
    WINDOW_POSITION,
    WINDOW_MOVEMENT,
    MIRROR_LOCK,
    MIRROR_FOLD,
    MIRROR_AUTO_FOLD,
    MIRROR_AUTO_TILT,
    MIRROR_VERTICAL,
    MIRROR_HORIZONTAL,
}

enum class DoorControlKind { SWITCH, RANGE, ENUM }

enum class DoorControlSection { DOORS, WINDOWS, MIRRORS }

data class DoorControlDefinition(
    val id: DoorControlId,
    val propertyId: Int,
    val valueType: VehiclePropertyValueType,
    val kind: DoorControlKind,
    val section: DoorControlSection,
)

val DOOR_CONTROL_DEFINITIONS =
    listOf(
        bool(DoorControlId.DOOR_LOCK, VehiclePropertyIds.DOOR_LOCK, DoorControlSection.DOORS),
        bool(DoorControlId.CHILD_LOCK, VehiclePropertyIds.DOOR_CHILD_LOCK_ENABLED, DoorControlSection.DOORS),
        int(DoorControlId.DOOR_POSITION, VehiclePropertyIds.DOOR_POS, DoorControlKind.RANGE, DoorControlSection.DOORS),
        int(DoorControlId.DOOR_MOVEMENT, VehiclePropertyIds.DOOR_MOVE, DoorControlKind.ENUM, DoorControlSection.DOORS),
        bool(DoorControlId.WINDOW_LOCK, VehiclePropertyIds.WINDOW_LOCK, DoorControlSection.WINDOWS),
        int(DoorControlId.WINDOW_POSITION, VehiclePropertyIds.WINDOW_POS, DoorControlKind.RANGE, DoorControlSection.WINDOWS),
        int(DoorControlId.WINDOW_MOVEMENT, VehiclePropertyIds.WINDOW_MOVE, DoorControlKind.ENUM, DoorControlSection.WINDOWS),
        bool(DoorControlId.MIRROR_LOCK, VehiclePropertyIds.MIRROR_LOCK, DoorControlSection.MIRRORS),
        bool(DoorControlId.MIRROR_FOLD, VehiclePropertyIds.MIRROR_FOLD, DoorControlSection.MIRRORS),
        bool(DoorControlId.MIRROR_AUTO_FOLD, VehiclePropertyIds.MIRROR_AUTO_FOLD_ENABLED, DoorControlSection.MIRRORS),
        bool(DoorControlId.MIRROR_AUTO_TILT, VehiclePropertyIds.MIRROR_AUTO_TILT_ENABLED, DoorControlSection.MIRRORS),
        int(DoorControlId.MIRROR_VERTICAL, VehiclePropertyIds.MIRROR_Z_POS, DoorControlKind.RANGE, DoorControlSection.MIRRORS),
        int(DoorControlId.MIRROR_HORIZONTAL, VehiclePropertyIds.MIRROR_Y_POS, DoorControlKind.RANGE, DoorControlSection.MIRRORS),
    )

private fun bool(
    id: DoorControlId,
    propertyId: Int,
    section: DoorControlSection,
) = DoorControlDefinition(id, propertyId, VehiclePropertyValueType.BOOLEAN, DoorControlKind.SWITCH, section)

private fun int(
    id: DoorControlId,
    propertyId: Int,
    kind: DoorControlKind,
    section: DoorControlSection,
) = DoorControlDefinition(id, propertyId, VehiclePropertyValueType.INT, kind, section)
