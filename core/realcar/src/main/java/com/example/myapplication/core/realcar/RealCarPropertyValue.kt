@file:Suppress("DEPRECATION")

package com.example.myapplication.core.realcar

import android.car.hardware.CarPropertyValue
import android.os.SystemClock

/**
 * Snapshot của một vehicle property thật sau khi đọc từ `CarPropertyManager`.
 *
 * Wrapper giữ nguyên `propertyId`, `areaId`, `status`, `timestamp` và value từ framework,
 * sau đó cung cấp các hàm `as...` để caller đọc đúng kiểu. Mỗi hàm `as...` đều kiểm tra
 * trạng thái `STATUS_AVAILABLE` trước khi trả dữ liệu, vì trên xe thật property có thể
 * đang `UNAVAILABLE` dù callback vẫn được phát ra.
 */
class RealCarPropertyValue(
    val propertyId: Int,
    val areaId: Int,
    val value: Any?,
    val status: Int,
    val timestampNanos: Long,
    private val valueTypeName: String = value?.javaClass?.simpleName ?: "null",
) {
    val isAvailable: Boolean
        get() = status == CarPropertyValue.STATUS_AVAILABLE

    /**
     * Kiểm tra property đang khả dụng trước khi tầng gọi dùng value.
     *
     * Vehicle HAL có thể trả status unavailable trong lúc ECU chưa publish dữ liệu, xe
     * vừa boot, hoặc sensor tạm lỗi. Thay vì trả null mơ hồ, wrapper ném lỗi chuẩn hóa để
     * caller có thể hiển thị stale data hoặc retry.
     */
    fun requireAvailable(): RealCarPropertyValue {
        if (!isAvailable) {
            throw RealCarPropertyException.PropertyUnavailable(
                propertyId = propertyId,
                areaId = areaId,
                status = status,
            )
        }
        return this
    }

    fun asFloat(): Float {
        val rawValue = requireAvailable().value
        return when (rawValue) {
            is Float -> rawValue
            is Number -> rawValue.toFloat()
            else ->
                throw RealCarPropertyException.TypeMismatch(
                    propertyId = propertyId,
                    areaId = areaId,
                    expectedType = "Float",
                    actualType = valueTypeName(),
                )
        }
    }

    fun asInt(): Int {
        val rawValue = requireAvailable().value
        return when (rawValue) {
            is Int -> rawValue
            is Number -> rawValue.toInt()
            else ->
                throw RealCarPropertyException.TypeMismatch(
                    propertyId = propertyId,
                    areaId = areaId,
                    expectedType = "Int",
                    actualType = valueTypeName(),
                )
        }
    }

    fun asBoolean(): Boolean {
        val rawValue = requireAvailable().value
        return rawValue as? Boolean
            ?: throw RealCarPropertyException.TypeMismatch(
                propertyId = propertyId,
                areaId = areaId,
                expectedType = "Boolean",
                actualType = valueTypeName(),
            )
    }

    fun asString(): String {
        val rawValue = requireAvailable().value
        return rawValue as? String
            ?: throw RealCarPropertyException.TypeMismatch(
                propertyId = propertyId,
                areaId = areaId,
                expectedType = "String",
                actualType = valueTypeName(),
            )
    }

    fun asIntArray(): IntArray {
        val rawValue = requireAvailable().value
        return rawValue as? IntArray
            ?: throw RealCarPropertyException.TypeMismatch(
                propertyId = propertyId,
                areaId = areaId,
                expectedType = "IntArray",
                actualType = valueTypeName(),
            )
    }

    fun valueTypeName(): String = valueTypeName

    companion object {
        const val GLOBAL_AREA_ID = 0

        fun from(rawValue: CarPropertyValue<*>): RealCarPropertyValue =
            RealCarPropertyValue(
                propertyId = rawValue.propertyId,
                areaId = rawValue.areaId,
                value = rawValue.value,
                status = rawValue.status,
                timestampNanos = rawValue.timestamp,
                valueTypeName = rawValue.value?.javaClass?.simpleName ?: "null",
            )

        fun local(
            propertyId: Int,
            areaId: Int,
            value: Any?,
        ): RealCarPropertyValue =
            RealCarPropertyValue(
                propertyId = propertyId,
                areaId = areaId,
                value = value,
                status = CarPropertyValue.STATUS_AVAILABLE,
                timestampNanos = SystemClock.elapsedRealtimeNanos(),
            )
    }
}
