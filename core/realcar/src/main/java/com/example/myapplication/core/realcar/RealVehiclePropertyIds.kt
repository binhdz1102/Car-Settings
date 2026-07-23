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
