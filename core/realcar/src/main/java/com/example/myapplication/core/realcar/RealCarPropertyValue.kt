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
    value: Any?,
    val status: Int,
    val timestampNanos: Long,
    private val valueTypeName: String = value?.javaClass?.simpleName ?: "null",
) {
    private val snapshotValue: Any? = defensiveCopy(value)

    /**
     * Trả bản sao với kiểu mảng để caller không thể sửa cache nội bộ.
     *
     * Returns a defensive copy for array values so callers cannot mutate the internal cache.
     */
    val value: Any?
        get() = defensiveCopy(snapshotValue)

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
        return when (rawValue) {
            is IntArray -> rawValue.copyOf()
            is Array<*> ->
                rawValue
                    .map {
                        (it as? Number)?.toInt()
                            ?: throw typeMismatch("IntArray")
                    }.toIntArray()

            else -> throw typeMismatch("IntArray")
        }
    }

    fun asLong(): Long {
        val rawValue = requireAvailable().value
        return when (rawValue) {
            is Long -> rawValue
            is Number -> rawValue.toLong()
            else -> throw typeMismatch("Long")
        }
    }

    fun asFloatArray(): FloatArray {
        val rawValue = requireAvailable().value
        return when (rawValue) {
            is FloatArray -> rawValue.copyOf()
            is Array<*> ->
                rawValue
                    .map {
                        (it as? Number)?.toFloat()
                            ?: throw typeMismatch("FloatArray")
                    }.toFloatArray()

            else -> throw typeMismatch("FloatArray")
        }
    }

    fun asLongArray(): LongArray {
        val rawValue = requireAvailable().value
        return when (rawValue) {
            is LongArray -> rawValue.copyOf()
            is Array<*> ->
                rawValue
                    .map {
                        (it as? Number)?.toLong()
                            ?: throw typeMismatch("LongArray")
                    }.toLongArray()

            else -> throw typeMismatch("LongArray")
        }
    }

    fun asByteArray(): ByteArray {
        val rawValue = requireAvailable().value
        return when (rawValue) {
            is ByteArray -> rawValue.copyOf()
            is Array<*> ->
                rawValue
                    .map {
                        (it as? Number)?.toByte()
                            ?: throw typeMismatch("ByteArray")
                    }.toByteArray()

            else -> throw typeMismatch("ByteArray")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun asMixed(): Array<Any?> =
        (requireAvailable().value as? Array<*>)
            ?.map(::defensiveCopy)
            ?.toTypedArray()
            ?: throw typeMismatch("Array<Any?>")

    fun valueTypeName(): String = valueTypeName

    private fun typeMismatch(expectedType: String) =
        RealCarPropertyException.TypeMismatch(
            propertyId = propertyId,
            areaId = areaId,
            expectedType = expectedType,
            actualType = valueTypeName(),
        )

    companion object {
        const val GLOBAL_AREA_ID = 0

        fun from(rawValue: CarPropertyValue<*>): RealCarPropertyValue =
            RealCarPropertyValue(
                propertyId = rawValue.propertyId,
                areaId = rawValue.areaId,
                value = rawValue.value,
                status = rawValue.propertyStatus,
                timestampNanos = rawValue.timestamp,
                valueTypeName = rawValue.value?.javaClass?.simpleName ?: "null",
            )

        fun remote(
            propertyId: Int,
            areaId: Int,
            value: Any?,
            timestampNanos: Long,
        ): RealCarPropertyValue =
            RealCarPropertyValue(
                propertyId = propertyId,
                areaId = areaId,
                value = value,
                status = CarPropertyValue.STATUS_AVAILABLE,
                timestampNanos = timestampNanos,
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

        internal fun defensiveCopy(value: Any?): Any? =
            when (value) {
                is ByteArray -> value.copyOf()
                is IntArray -> value.copyOf()
                is LongArray -> value.copyOf()
                is FloatArray -> value.copyOf()
                is DoubleArray -> value.copyOf()
                is BooleanArray -> value.copyOf()
                is ShortArray -> value.copyOf()
                is CharArray -> value.copyOf()
                is Array<*> -> value.map(::defensiveCopy).toTypedArray()
                else -> value
            }
    }
}
