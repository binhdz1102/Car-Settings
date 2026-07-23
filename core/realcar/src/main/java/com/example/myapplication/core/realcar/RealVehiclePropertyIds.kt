package com.example.myapplication.core.realcar

import android.car.VehiclePropertyIds as PlatformVehiclePropertyIds

/**
 * Các vehicle property thật thường dùng bởi launcher.
 *
 * Không dùng lại ID tự định nghĩa của `carfake` ở đây. Trên môi trường AAOS thật, ID phải
 * là hằng số chuẩn từ `android.car.VehiclePropertyIds` để CarService định tuyến đúng đến
 * vehicle HAL. Lưu ý `ODOMETER` thật là `PERF_ODOMETER` và có kiểu `Float` theo km.
 */
object RealVehiclePropertyIds {
    const val SPEED = PlatformVehiclePropertyIds.PERF_VEHICLE_SPEED
    const val SPEED_DISPLAY = PlatformVehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY
    const val GEAR = PlatformVehiclePropertyIds.CURRENT_GEAR
    const val GEAR_SELECTION = PlatformVehiclePropertyIds.GEAR_SELECTION
    const val ODOMETER = PlatformVehiclePropertyIds.PERF_ODOMETER
    const val INFO_MAKE = PlatformVehiclePropertyIds.INFO_MAKE
    const val INFO_FUEL_TYPE = PlatformVehiclePropertyIds.INFO_FUEL_TYPE
    const val WHEEL_TICK = PlatformVehiclePropertyIds.WHEEL_TICK
    const val VHAL_HEARTBEAT = 0x11500F33
    const val OBD2_LIVE_FRAME = 0x11E00D00
    const val HVAC_TEMPERATURE_SET = PlatformVehiclePropertyIds.HVAC_TEMPERATURE_SET
    const val HVAC_FAN_SPEED = PlatformVehiclePropertyIds.HVAC_FAN_SPEED
    const val HVAC_AC_ON = PlatformVehiclePropertyIds.HVAC_AC_ON
    const val HVAC_TEMPERATURE_VALUE_SUGGESTION =
        PlatformVehiclePropertyIds.HVAC_TEMPERATURE_VALUE_SUGGESTION

    /** Sample vendor BYTES property available on the AOSP automotive emulator. */
    const val EMULATOR_VENDOR_BYTES = 0x21702A12

    /** Sample vendor MIXED property available on the AOSP automotive emulator. */
    const val EMULATOR_VENDOR_MIXED = 0x21E01111

    val launcherDashboardPropertyIds =
        listOf(
            SPEED,
            GEAR,
            ODOMETER,
        )

    fun nameOf(propertyId: Int): String =
        runCatching { PlatformVehiclePropertyIds.toString(propertyId) }
            .getOrNull()
            ?.takeUnless { it.isBlank() }
            ?: "Unknown($propertyId)"
}
