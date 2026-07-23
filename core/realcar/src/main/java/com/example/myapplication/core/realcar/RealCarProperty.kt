package com.example.myapplication.core.realcar

/**
 * Khóa property có kiểu dữ liệu, dùng chung cho đọc, ghi và observe.
 *
 * A type-safe property key shared by read, write, and observe operations.
 *
 * Ví dụ / Example:
 * ```
 * val driverTemperature = RealCarProperty.float(
 *     propertyId = VehiclePropertyIds.HVAC_TEMPERATURE_SET,
 *     areaId = VehicleAreaSeat.SEAT_ROW_1_LEFT,
 * )
 * val result = manager.read(driverTemperature)
 * ```
 *
 * [valueClass] được kiểm tra với `CarPropertyConfig.propertyType` trước khi gọi framework.
 * Điều này biến lỗi sai kiểu thành [RealCarPropertyException.TypeMismatch] rõ nghĩa thay vì
 * một `IllegalArgumentException` khó chẩn đoán từ binder.
 *
 * [valueClass] is checked against `CarPropertyConfig.propertyType` before the framework call,
 * producing a descriptive [RealCarPropertyException.TypeMismatch].
 */
class RealCarProperty<T : Any> private constructor(
    val propertyId: Int,
    val areaId: Int,
    val valueClass: Class<T>,
) {
    fun withArea(areaId: Int): RealCarProperty<T> =
        RealCarProperty(
            propertyId = propertyId,
            areaId = areaId,
            valueClass = valueClass,
        )

    fun asReadRequest(): RealCarPropertyReadRequest =
        RealCarPropertyReadRequest(
            propertyId = propertyId,
            areaId = areaId,
            expectedType = valueClass,
        )

    fun writeRequest(
        value: T,
        waitForPropertyUpdate: Boolean = true,
        updateRateHz: Float = RealCarPropertyManager.SENSOR_RATE_ONCHANGE,
    ): RealCarPropertyWriteRequest =
        RealCarPropertyWriteRequest(
            propertyId = propertyId,
            areaId = areaId,
            value = value,
            expectedType = valueClass,
            waitForPropertyUpdate = waitForPropertyUpdate,
            updateRateHz = updateRateHz,
        )

    override fun toString(): String = "${RealVehiclePropertyIds.nameOf(propertyId)}[$areaId]:${valueClass.simpleName}"

    companion object {
        fun float(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarProperty<Float> = RealCarProperty(propertyId, areaId, Float::class.javaObjectType)

        fun int(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarProperty<Int> = RealCarProperty(propertyId, areaId, Int::class.javaObjectType)

        fun boolean(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarProperty<Boolean> = RealCarProperty(propertyId, areaId, Boolean::class.javaObjectType)

        fun long(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarProperty<Long> = RealCarProperty(propertyId, areaId, Long::class.javaObjectType)

        fun string(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarProperty<String> = RealCarProperty(propertyId, areaId, String::class.java)

        fun intArray(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarProperty<IntArray> = RealCarProperty(propertyId, areaId, IntArray::class.java)

        fun floatArray(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarProperty<FloatArray> = RealCarProperty(propertyId, areaId, FloatArray::class.java)

        fun longArray(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarProperty<LongArray> = RealCarProperty(propertyId, areaId, LongArray::class.java)

        fun byteArray(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarProperty<ByteArray> = RealCarProperty(propertyId, areaId, ByteArray::class.java)

        fun mixed(
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarProperty<Array<Any?>> =
            RealCarProperty(
                propertyId = propertyId,
                areaId = areaId,
                valueClass = Array<Any?>::class.java,
            )

        fun <T : Any> of(
            valueClass: Class<T>,
            propertyId: Int,
            areaId: Int = RealCarPropertyValue.GLOBAL_AREA_ID,
        ): RealCarProperty<T> = RealCarProperty(propertyId, areaId, valueClass)
    }
}
