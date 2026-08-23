package com.android.car.settings.core.vehicle

import kotlinx.coroutines.flow.Flow

interface VehiclePropertyClient {
    suspend fun <T : Any> capability(
        spec: VehiclePropertySpec<T>,
        timeoutMillis: Long = DEFAULT_VEHICLE_OPERATION_TIMEOUT_MS,
    ): VehiclePropertyResult<VehiclePropertyCapability<T>>

    suspend fun discoverCapabilities(
        specs: Collection<VehiclePropertySpec<*>>,
        timeoutMillis: Long = DEFAULT_VEHICLE_OPERATION_TIMEOUT_MS,
    ): Map<Int, VehiclePropertyResult<VehiclePropertyCapability<*>>>

    suspend fun <T : Any> get(
        spec: VehiclePropertySpec<T>,
        area: VehiclePropertyArea = VehiclePropertyArea.GLOBAL,
        timeoutMillis: Long = DEFAULT_VEHICLE_OPERATION_TIMEOUT_MS,
    ): VehiclePropertyResult<VehiclePropertyValue<T>>

    fun <T : Any> observe(
        spec: VehiclePropertySpec<T>,
        areas: Set<VehiclePropertyArea> = emptySet(),
        updateRateHz: Float = DEFAULT_VEHICLE_UPDATE_RATE_HZ,
    ): Flow<VehiclePropertyEvent<T>>

    fun <T : Any> set(
        spec: VehiclePropertySpec<T>,
        area: VehiclePropertyArea,
        value: T,
        acknowledgementTimeoutMillis: Long = DEFAULT_VEHICLE_WRITE_TIMEOUT_MS,
    ): Flow<VehiclePropertyWriteResult<T>>

    suspend fun getBoolean(
        propertyId: Int,
        areaId: Int = 0,
        timeoutMillis: Long = DEFAULT_VEHICLE_OPERATION_TIMEOUT_MS,
    ): VehiclePropertyResult<VehiclePropertyValue<Boolean>> =
        get(VehiclePropertySpec.boolean(propertyId), VehiclePropertyArea(areaId), timeoutMillis)

    suspend fun getInt(
        propertyId: Int,
        areaId: Int = 0,
        timeoutMillis: Long = DEFAULT_VEHICLE_OPERATION_TIMEOUT_MS,
    ): VehiclePropertyResult<VehiclePropertyValue<Int>> =
        get(VehiclePropertySpec.int(propertyId), VehiclePropertyArea(areaId), timeoutMillis)

    suspend fun getFloat(
        propertyId: Int,
        areaId: Int = 0,
        timeoutMillis: Long = DEFAULT_VEHICLE_OPERATION_TIMEOUT_MS,
    ): VehiclePropertyResult<VehiclePropertyValue<Float>> =
        get(VehiclePropertySpec.float(propertyId), VehiclePropertyArea(areaId), timeoutMillis)

    fun setBoolean(
        propertyId: Int,
        areaId: Int,
        value: Boolean,
        acknowledgementTimeoutMillis: Long = DEFAULT_VEHICLE_WRITE_TIMEOUT_MS,
    ): Flow<VehiclePropertyWriteResult<Boolean>> =
        set(
            VehiclePropertySpec.boolean(propertyId),
            VehiclePropertyArea(areaId),
            value,
            acknowledgementTimeoutMillis,
        )

    fun setInt(
        propertyId: Int,
        areaId: Int,
        value: Int,
        acknowledgementTimeoutMillis: Long = DEFAULT_VEHICLE_WRITE_TIMEOUT_MS,
    ): Flow<VehiclePropertyWriteResult<Int>> =
        set(
            VehiclePropertySpec.int(propertyId),
            VehiclePropertyArea(areaId),
            value,
            acknowledgementTimeoutMillis,
        )

    fun setFloat(
        propertyId: Int,
        areaId: Int,
        value: Float,
        acknowledgementTimeoutMillis: Long = DEFAULT_VEHICLE_WRITE_TIMEOUT_MS,
    ): Flow<VehiclePropertyWriteResult<Float>> =
        set(
            VehiclePropertySpec.float(propertyId),
            VehiclePropertyArea(areaId),
            value,
            acknowledgementTimeoutMillis,
        )
}

const val DEFAULT_VEHICLE_OPERATION_TIMEOUT_MS = 3_000L
const val DEFAULT_VEHICLE_WRITE_TIMEOUT_MS = 2_000L
const val DEFAULT_VEHICLE_UPDATE_RATE_HZ = 1f
