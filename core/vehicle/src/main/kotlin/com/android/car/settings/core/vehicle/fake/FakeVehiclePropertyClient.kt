@file:Suppress("ReturnCount")

package com.android.car.settings.core.vehicle.fake

import com.android.car.settings.core.vehicle.DEFAULT_VEHICLE_OPERATION_TIMEOUT_MS
import com.android.car.settings.core.vehicle.VehiclePropertyArea
import com.android.car.settings.core.vehicle.VehiclePropertyCapability
import com.android.car.settings.core.vehicle.VehiclePropertyClient
import com.android.car.settings.core.vehicle.VehiclePropertyError
import com.android.car.settings.core.vehicle.VehiclePropertyEvent
import com.android.car.settings.core.vehicle.VehiclePropertyOperation
import com.android.car.settings.core.vehicle.VehiclePropertyResult
import com.android.car.settings.core.vehicle.VehiclePropertySpec
import com.android.car.settings.core.vehicle.VehiclePropertyStatus
import com.android.car.settings.core.vehicle.VehiclePropertyValue
import com.android.car.settings.core.vehicle.VehiclePropertyWriteResult
import com.android.car.settings.core.vehicle.VehicleWriteConfirmation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

enum class FakeVehicleWriteBehavior {
    ACKNOWLEDGE_BY_CALLBACK,
    ACKNOWLEDGE_BY_READBACK,
    TIMEOUT,
    ERROR,
}

data class FakeVehicleWrite<T : Any>(
    val spec: VehiclePropertySpec<T>,
    val area: VehiclePropertyArea,
    val value: T,
)

/**
 * Deterministic in-memory client for repository, ViewModel and Compose tests.
 *
 * This fake is opt-in and is never bound by the production Hilt module.
 */
class FakeVehiclePropertyClient(
    capabilities: Collection<VehiclePropertyCapability<*>> = emptyList(),
    values: Collection<VehiclePropertyValue<*>> = emptyList(),
) : VehiclePropertyClient {
    private val lock = Any()
    private val capabilitiesById = capabilities.associateBy { it.spec.propertyId }.toMutableMap()
    private val valuesByKey =
        values.associateBy { PropertyKey(it.spec.propertyId, it.area.areaId) }.toMutableMap()
    private val eventFlows = mutableMapOf<PropertyKey, MutableSharedFlow<VehiclePropertyEvent<Any>>>()
    private val writeBehaviors = mutableMapOf<PropertyKey, FakeVehicleWriteBehavior>()
    private val writeErrors = mutableMapOf<PropertyKey, VehiclePropertyError>()
    private val mutableWrites = mutableListOf<FakeVehicleWrite<*>>()

    val writes: List<FakeVehicleWrite<*>>
        get() = synchronized(lock) { mutableWrites.toList() }

    override suspend fun <T : Any> capability(
        spec: VehiclePropertySpec<T>,
        timeoutMillis: Long,
    ): VehiclePropertyResult<VehiclePropertyCapability<T>> {
        val capability =
            synchronized(lock) { capabilitiesById[spec.propertyId] }
                ?: return VehiclePropertyResult.Failure(
                    VehiclePropertyError.Unsupported(spec.propertyId),
                )
        if (capability.spec.valueType != spec.valueType) {
            return VehiclePropertyResult.Failure(
                VehiclePropertyError.TypeMismatch(
                    spec.propertyId,
                    spec.valueType,
                    capability.spec.valueType.name,
                ),
            )
        }
        @Suppress("UNCHECKED_CAST")
        return VehiclePropertyResult.Success(capability as VehiclePropertyCapability<T>)
    }

    override suspend fun discoverCapabilities(
        specs: Collection<VehiclePropertySpec<*>>,
        timeoutMillis: Long,
    ): Map<Int, VehiclePropertyResult<VehiclePropertyCapability<*>>> =
        buildMap {
            specs.distinctBy { it.propertyId }.forEach { spec ->
                @Suppress("UNCHECKED_CAST")
                put(spec.propertyId, capability(spec as VehiclePropertySpec<Any>, timeoutMillis))
            }
        }

    override suspend fun <T : Any> get(
        spec: VehiclePropertySpec<T>,
        area: VehiclePropertyArea,
        timeoutMillis: Long,
    ): VehiclePropertyResult<VehiclePropertyValue<T>> {
        when (val capability = capability(spec, timeoutMillis)) {
            is VehiclePropertyResult.Failure -> return capability
            is VehiclePropertyResult.Success -> {
                if (capability.value.area(area) == null) {
                    return VehiclePropertyResult.Failure(
                        VehiclePropertyError.InvalidArea(spec.propertyId, area),
                    )
                }
            }
        }
        val value =
            synchronized(lock) { valuesByKey[PropertyKey(spec.propertyId, area.areaId)] }
                ?: return VehiclePropertyResult.Failure(
                    VehiclePropertyError.Unavailable(spec.propertyId, area, retryable = true),
                )
        @Suppress("UNCHECKED_CAST")
        return VehiclePropertyResult.Success(value as VehiclePropertyValue<T>)
    }

    override fun <T : Any> observe(
        spec: VehiclePropertySpec<T>,
        areas: Set<VehiclePropertyArea>,
        updateRateHz: Float,
    ): Flow<VehiclePropertyEvent<T>> {
        val selectedAreas =
            if (areas.isEmpty()) {
                synchronized(lock) {
                    capabilitiesById[spec.propertyId]
                        ?.areas
                        ?.mapTo(linkedSetOf()) { it.area }
                        .orEmpty()
                }
            } else {
                areas
            }
        return flow {
            selectedAreas.forEach { area ->
                val key = PropertyKey(spec.propertyId, area.areaId)
                val current = synchronized(lock) { valuesByKey[key] }
                if (current != null) {
                    @Suppress("UNCHECKED_CAST")
                    emit(VehiclePropertyEvent.ValueChanged(current as VehiclePropertyValue<T>))
                }
            }
            val streams = selectedAreas.map { eventsFor(PropertyKey(spec.propertyId, it.areaId)) }
            if (streams.size == 1) {
                emitAll(streams.single().map { it.castEvent() })
            } else if (streams.isNotEmpty()) {
                emitAll(
                    kotlinx.coroutines.flow
                        .merge(*streams.toTypedArray())
                        .map { it.castEvent() },
                )
            }
        }
    }

    override fun <T : Any> set(
        spec: VehiclePropertySpec<T>,
        area: VehiclePropertyArea,
        value: T,
        acknowledgementTimeoutMillis: Long,
    ): Flow<VehiclePropertyWriteResult<T>> =
        flow {
            emit(VehiclePropertyWriteResult.Pending(spec, area, value))
            val capability = capability(spec, DEFAULT_VEHICLE_OPERATION_TIMEOUT_MS)
            if (capability is VehiclePropertyResult.Failure) {
                emit(VehiclePropertyWriteResult.Error(spec, area, value, capability.error))
                return@flow
            }
            capability as VehiclePropertyResult.Success
            val areaCapability = capability.value.area(area)
            if (areaCapability == null) {
                emit(
                    VehiclePropertyWriteResult.Error(
                        spec,
                        area,
                        value,
                        VehiclePropertyError.InvalidArea(spec.propertyId, area),
                    ),
                )
                return@flow
            }
            if (!areaCapability.access.canWrite) {
                emit(
                    VehiclePropertyWriteResult.Error(
                        spec,
                        area,
                        value,
                        VehiclePropertyError.PermissionDenied(spec.propertyId, area),
                    ),
                )
                return@flow
            }

            val key = PropertyKey(spec.propertyId, area.areaId)
            synchronized(lock) { mutableWrites += FakeVehicleWrite(spec, area, value) }
            when (synchronized(lock) { writeBehaviors[key] } ?: FakeVehicleWriteBehavior.ACKNOWLEDGE_BY_CALLBACK) {
                FakeVehicleWriteBehavior.TIMEOUT -> {
                    delay(acknowledgementTimeoutMillis.coerceAtLeast(0L))
                    emit(
                        VehiclePropertyWriteResult.Error(
                            spec,
                            area,
                            value,
                            VehiclePropertyError.Timeout(
                                VehiclePropertyOperation.WRITE,
                                acknowledgementTimeoutMillis,
                                spec.propertyId,
                                area,
                            ),
                        ),
                    )
                }
                FakeVehicleWriteBehavior.ERROR ->
                    emit(
                        VehiclePropertyWriteResult.Error(
                            spec,
                            area,
                            value,
                            synchronized(lock) { writeErrors[key] }
                                ?: VehiclePropertyError.WriteRejected(spec.propertyId, area, -1),
                        ),
                    )
                FakeVehicleWriteBehavior.ACKNOWLEDGE_BY_CALLBACK,
                FakeVehicleWriteBehavior.ACKNOWLEDGE_BY_READBACK,
                -> {
                    val updated =
                        VehiclePropertyValue(
                            spec,
                            area,
                            value,
                            VehiclePropertyStatus.AVAILABLE,
                            nextTimestamp(),
                        )
                    synchronized(lock) { valuesByKey[key] = updated }
                    val behavior = synchronized(lock) { writeBehaviors[key] }
                    if (behavior != FakeVehicleWriteBehavior.ACKNOWLEDGE_BY_READBACK) {
                        eventsFor(key).emitAny(VehiclePropertyEvent.ValueChanged(updated))
                    }
                    emit(
                        VehiclePropertyWriteResult.Confirmed(
                            updated,
                            if (behavior == FakeVehicleWriteBehavior.ACKNOWLEDGE_BY_READBACK) {
                                VehicleWriteConfirmation.READBACK
                            } else {
                                VehicleWriteConfirmation.CALLBACK
                            },
                        ),
                    )
                }
            }
        }

    fun addCapability(capability: VehiclePropertyCapability<*>) {
        synchronized(lock) { capabilitiesById[capability.spec.propertyId] = capability }
    }

    fun setWriteBehavior(
        propertyId: Int,
        areaId: Int,
        behavior: FakeVehicleWriteBehavior,
        error: VehiclePropertyError? = null,
    ) {
        val key = PropertyKey(propertyId, areaId)
        synchronized(lock) {
            writeBehaviors[key] = behavior
            if (error == null) writeErrors.remove(key) else writeErrors[key] = error
        }
    }

    fun <T : Any> emitExternal(
        spec: VehiclePropertySpec<T>,
        area: VehiclePropertyArea,
        value: T,
        status: VehiclePropertyStatus = VehiclePropertyStatus.AVAILABLE,
        timestampNanos: Long = nextTimestamp(),
    ) {
        val key = PropertyKey(spec.propertyId, area.areaId)
        val update = VehiclePropertyValue(spec, area, value, status, timestampNanos)
        synchronized(lock) { valuesByKey[key] = update }
        eventsFor(key).emitAny(VehiclePropertyEvent.ValueChanged(update))
    }

    fun emitExternalError(
        propertyId: Int,
        area: VehiclePropertyArea,
        error: VehiclePropertyError,
    ) {
        eventsFor(PropertyKey(propertyId, area.areaId)).emitAny(VehiclePropertyEvent.Error(error))
    }

    private fun eventsFor(key: PropertyKey): MutableSharedFlow<VehiclePropertyEvent<Any>> =
        synchronized(lock) {
            eventFlows.getOrPut(key) { MutableSharedFlow(extraBufferCapacity = EVENT_BUFFER_CAPACITY) }
        }

    private fun nextTimestamp(): Long = synchronized(lock) { timestampNanos++ }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> VehiclePropertyEvent<Any>.castEvent(): VehiclePropertyEvent<T> = this as VehiclePropertyEvent<T>

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> MutableSharedFlow<VehiclePropertyEvent<Any>>.emitAny(event: VehiclePropertyEvent<T>) {
        check(tryEmit(event as VehiclePropertyEvent<Any>)) { "Fake vehicle event buffer is full" }
    }

    private data class PropertyKey(
        val propertyId: Int,
        val areaId: Int,
    )

    private var timestampNanos = 1L
}

private const val EVENT_BUFFER_CAPACITY = 64
