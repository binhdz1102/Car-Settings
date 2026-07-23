package com.example.myapplication.core.realcar

import android.car.VehicleGear as PlatformVehicleGear

/**
 * Helper chuyển mã số gear thật của Android Automotive thành nhãn dễ hiển thị.
 *
 * `CURRENT_GEAR` và `GEAR_SELECTION` trên vehicle HAL trả về `Int`, không phải chuỗi
 * `P/R/N/D` như fake service. Caller nên lưu dữ liệu gốc dạng số để không mất thông tin,
 * còn khi render UI thì dùng [shortNameOf] hoặc [nameOf].
 */
object RealVehicleGear {
    const val UNKNOWN = PlatformVehicleGear.GEAR_UNKNOWN
    const val NEUTRAL = PlatformVehicleGear.GEAR_NEUTRAL
    const val REVERSE = PlatformVehicleGear.GEAR_REVERSE
    const val PARK = PlatformVehicleGear.GEAR_PARK
    const val DRIVE = PlatformVehicleGear.GEAR_DRIVE

    fun shortNameOf(gear: Int): String =
        when (gear) {
            PARK -> "P"
            REVERSE -> "R"
            NEUTRAL -> "N"
            DRIVE -> "D"
            UNKNOWN -> "--"
            else -> gear.toString()
        }

    fun nameOf(gear: Int): String = PlatformVehicleGear.toString(gear)
}
