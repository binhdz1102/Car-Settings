@file:Suppress(
    "LargeClass",
    "RethrowCaughtException",
    "ReturnCount",
    "SwallowedException",
)

package com.android.car.settings.core.vehicle

import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.core.vehicle.internal.PlatformAreaConfig
import com.android.car.settings.core.vehicle.internal.PlatformPropertyConfig
import com.android.car.settings.core.vehicle.internal.PlatformPropertyEvent
import com.android.car.settings.core.vehicle.internal.PlatformPropertyValue
import com.android.car.settings.core.vehicle.internal.PlatformSetErrorCode
import com.android.car.settings.core.vehicle.internal.PlatformVehicleException
import com.android.car.settings.core.vehicle.internal.PlatformVehicleSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
internal class DefaultVehiclePropertyClient
    @Inject
    constructor(
        private val connection: DefaultVehiclePropertyConnection,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : VehiclePropertyClient {
        override suspend fun <T : Any> capability(
            spec: VehiclePropertySpec<T>,
            timeoutMillis: Long,
        ): VehiclePropertyResult<VehiclePropertyCapability<T>> {
            val result =
                callPlatform(
                    operation = VehiclePropertyOperation.CAPABILITY,
                    propertyId = spec.propertyId,
                    area = null,
                    timeoutMillis = timeoutMillis,
                ) { session ->
                    session.properties.getConfig(spec.propertyId)
                }
            return when (result) {
                is VehiclePropertyResult.Failure -> result
                is VehiclePropertyResult.Success -> {
                    val config = result.value
                    if (config == null) {
                        VehiclePropertyResult.Failure(
                            VehiclePropertyError.Unsupported(spec.propertyId),
                        )
                    } else {
                        config.toCapability(spec)
                    }
                }
            }
        }

        override suspend fun discoverCapabilities(
            specs: Collection<VehiclePropertySpec<*>>,
            timeoutMillis: Long,
        ): Map<Int, VehiclePropertyResult<VehiclePropertyCapability<*>>> {
            val discovered = linkedMapOf<Int, VehiclePropertyResult<VehiclePropertyCapability<*>>>()
            specs.distinctBy { it.propertyId }.forEach { spec ->
                @Suppress("UNCHECKED_CAST")
                val typedSpec = spec as VehiclePropertySpec<Any>
                discovered[spec.propertyId] = capability(typedSpec, timeoutMillis)
            }
            return discovered
        }

        override suspend fun <T : Any> get(
            spec: VehiclePropertySpec<T>,
            area: VehiclePropertyArea,
            timeoutMillis: Long,
        ): VehiclePropertyResult<VehiclePropertyValue<T>> {
            val capabilityResult = capability(spec, timeoutMillis)
            val areaCapability =
                when (capabilityResult) {
                    is VehiclePropertyResult.Failure -> return capabilityResult
                    is VehiclePropertyResult.Success -> {
                        capabilityResult.value.area(area)
                            ?: return VehiclePropertyResult.Failure(
                                VehiclePropertyError.InvalidArea(spec.propertyId, area),
                            )
                    }
                }
            if (!areaCapability.access.canRead) {
                return VehiclePropertyResult.Failure(
                    VehiclePropertyError.PermissionDenied(
                        propertyId = spec.propertyId,
                        area = area,
                        description = "Vehicle property area is not readable",
                    ),
                )
            }

            val result =
                callPlatform(
                    operation = VehiclePropertyOperation.READ,
                    propertyId = spec.propertyId,
                    area = area,
                    timeoutMillis = timeoutMillis,
                ) { session ->
                    session.properties.get(spec.valueType, spec.propertyId, area.areaId)
                }
            return when (result) {
                is VehiclePropertyResult.Failure -> result
                is VehiclePropertyResult.Success -> result.value.toTypedValue(spec)
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun <T : Any> observe(
            spec: VehiclePropertySpec<T>,
            areas: Set<VehiclePropertyArea>,
            updateRateHz: Float,
        ): Flow<VehiclePropertyEvent<T>> {
            connection.connect()
            return connection.sessions.flatMapLatest { session ->
                if (session == null) {
                    flowOf(
                        VehiclePropertyEvent.Error(
                            VehiclePropertyError.ServiceUnavailable(
                                operation = VehiclePropertyOperation.SUBSCRIBE,
                                propertyId = spec.propertyId,
                                description = "Waiting for the shared CarService connection",
                            ),
                        ),
                    )
                } else {
                    observeOnSession(session, spec, areas, updateRateHz)
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
                if (acknowledgementTimeoutMillis <= 0) {
                    emit(
                        VehiclePropertyWriteResult.Error(
                            spec,
                            area,
                            value,
                            VehiclePropertyError.InvalidValue(
                                spec.propertyId,
                                area,
                                "Acknowledgement timeout must be positive",
                            ),
                        ),
                    )
                    return@flow
                }

                // A write acknowledgement is a single user-visible operation. Do not give
                // capability discovery, service connection, subscription, set, callback and
                // read-back each their own two-second allowance: that would make one request
                // appear pending for many seconds. This outer deadline also cancels every
                // nested platform call if a prior phase has consumed its budget.
                val deadlineNanos =
                    System.nanoTime() + acknowledgementTimeoutMillis * NANOS_PER_MILLISECOND
                val result =
                    try {
                        withTimeout(acknowledgementTimeoutMillis) {
                            val capabilityResult =
                                capability(
                                    spec,
                                    remainingTimeoutMillis(deadlineNanos),
                                )
                            val areaCapability =
                                when (capabilityResult) {
                                    is VehiclePropertyResult.Failure ->
                                        return@withTimeout writeError(
                                            spec,
                                            area,
                                            value,
                                            capabilityResult.error,
                                        )
                                    is VehiclePropertyResult.Success ->
                                        capabilityResult.value.area(area)
                                            ?: return@withTimeout writeError(
                                                spec,
                                                area,
                                                value,
                                                VehiclePropertyError.InvalidArea(spec.propertyId, area),
                                            )
                                }
                            if (!areaCapability.access.canWrite) {
                                return@withTimeout writeError(
                                    spec,
                                    area,
                                    value,
                                    VehiclePropertyError.PermissionDenied(
                                        propertyId = spec.propertyId,
                                        area = area,
                                        description = "Vehicle property area is not writable",
                                    ),
                                )
                            }
                            validateValue(spec, areaCapability, value)?.let { error ->
                                return@withTimeout writeError(spec, area, value, error)
                            }

                            val session =
                                when (
                                    val sessionResult =
                                        awaitSession(
                                            operation = VehiclePropertyOperation.WRITE,
                                            propertyId = spec.propertyId,
                                            area = area,
                                            timeoutMillis = remainingTimeoutMillis(deadlineNanos),
                                        )
                                ) {
                                    is VehiclePropertyResult.Failure ->
                                        return@withTimeout writeError(
                                            spec,
                                            area,
                                            value,
                                            sessionResult.error,
                                        )
                                    is VehiclePropertyResult.Success -> sessionResult.value
                                }
                            performWrite(
                                session,
                                spec,
                                area,
                                value,
                                canRead = areaCapability.access.canRead,
                                timeoutMillis = acknowledgementTimeoutMillis,
                                deadlineNanos = deadlineNanos,
                            )
                        }
                    } catch (_: TimeoutCancellationException) {
                        writeError(
                            spec,
                            area,
                            value,
                            timeoutError(
                                VehiclePropertyOperation.WRITE,
                                spec,
                                area,
                                acknowledgementTimeoutMillis,
                            ),
                        )
                    }
                emit(result)
            }

        private fun <T : Any> observeOnSession(
            session: PlatformVehicleSession,
            spec: VehiclePropertySpec<T>,
            areas: Set<VehiclePropertyArea>,
            updateRateHz: Float,
        ): Flow<VehiclePropertyEvent<T>> =
            callbackFlow {
                val subscription =
                    try {
                        session.properties.subscribe(
                            propertyId = spec.propertyId,
                            areaIds = areas.mapTo(linkedSetOf()) { it.areaId },
                            updateRateHz = updateRateHz,
                        ) { event ->
                            trySend(event.toTypedEvent(spec))
                        }
                    } catch (error: RuntimeException) {
                        trySend(
                            VehiclePropertyEvent.Error(
                                mapPlatformError(
                                    error,
                                    VehiclePropertyOperation.SUBSCRIBE,
                                    spec.propertyId,
                                    areas.singleOrNull(),
                                ),
                            ),
                        )
                        close()
                        return@callbackFlow
                    }
                awaitClose {
                    try {
                        subscription.close()
                    } catch (_: RuntimeException) {
                        // A dead binder must not turn collector cancellation into a crash.
                    }
                }
            }

        private fun rethrowCancellation(cancellation: CancellationException): Nothing = throw cancellation

        private suspend fun <T : Any> performWriteOnly(
            session: PlatformVehicleSession,
            spec: VehiclePropertySpec<T>,
            area: VehiclePropertyArea,
            requestedValue: T,
            timeoutMillis: Long,
        ): VehiclePropertyWriteResult<T> {
            try {
                withTimeout(timeoutMillis) {
                    withContext(dispatcher) {
                        session.properties.set(
                            spec.valueType,
                            spec.propertyId,
                            area.areaId,
                            requestedValue,
                        )
                    }
                }
            } catch (error: TimeoutCancellationException) {
                return writeError(
                    spec,
                    area,
                    requestedValue,
                    timeoutError(VehiclePropertyOperation.WRITE, spec, area, timeoutMillis),
                )
            } catch (cancellation: CancellationException) {
                rethrowCancellation(cancellation)
            } catch (error: RuntimeException) {
                return writeError(
                    spec,
                    area,
                    requestedValue,
                    mapPlatformError(
                        error,
                        VehiclePropertyOperation.WRITE,
                        spec.propertyId,
                        area,
                    ),
                )
            }
            // WRITE-only properties cannot be subscribed or read back by contract. A
            // successful CarPropertyManager set is therefore the only acknowledgement the
            // caller can receive; keep it explicit and avoid a guaranteed permission error.
            return VehiclePropertyWriteResult.Confirmed(
                VehiclePropertyValue(
                    spec = spec,
                    area = area,
                    value = requestedValue,
                    status = VehiclePropertyStatus.AVAILABLE,
                    timestampNanos = System.nanoTime(),
                ),
                VehicleWriteConfirmation.CALLBACK,
            )
        }

        private suspend fun <T : Any> performWrite(
            session: PlatformVehicleSession,
            spec: VehiclePropertySpec<T>,
            area: VehiclePropertyArea,
            requestedValue: T,
            canRead: Boolean,
            timeoutMillis: Long,
            deadlineNanos: Long,
        ): VehiclePropertyWriteResult<T> {
            if (!canRead) {
                return performWriteOnly(
                    session = session,
                    spec = spec,
                    area = area,
                    requestedValue = requestedValue,
                    timeoutMillis = timeoutMillis,
                )
            }
            // Preserve a bounded portion of the one acknowledgement deadline for the read-back
            // fallback. Without this reservation a silent callback can consume the entire budget
            // and make a successful, immediately readable write look like a rejection.
            val readbackBudgetMillis =
                (timeoutMillis / 4).coerceIn(1L, READBACK_TIMEOUT_MILLIS)
            val callbackBudgetMillis = (timeoutMillis - readbackBudgetMillis).coerceAtLeast(1L)
            val callbackEvents = Channel<PlatformPropertyEvent>(Channel.UNLIMITED)
            val subscription =
                try {
                    withTimeout(remainingTimeoutMillis(deadlineNanos)) {
                        withContext(dispatcher) {
                            session.properties.subscribe(
                                propertyId = spec.propertyId,
                                areaIds = setOf(area.areaId),
                                updateRateHz = DEFAULT_VEHICLE_UPDATE_RATE_HZ,
                            ) { event ->
                                callbackEvents.trySend(event)
                            }
                        }
                    }
                } catch (error: TimeoutCancellationException) {
                    return writeError(
                        spec,
                        area,
                        requestedValue,
                        timeoutError(VehiclePropertyOperation.SUBSCRIBE, spec, area, timeoutMillis),
                    )
                } catch (cancellation: CancellationException) {
                    rethrowCancellation(cancellation)
                } catch (error: RuntimeException) {
                    return writeError(
                        spec,
                        area,
                        requestedValue,
                        mapPlatformError(
                            error,
                            VehiclePropertyOperation.SUBSCRIBE,
                            spec.propertyId,
                            area,
                        ),
                    )
                }

            try {
                try {
                    withTimeout(remainingTimeoutMillis(deadlineNanos)) {
                        withContext(dispatcher) {
                            session.properties.set(
                                spec.valueType,
                                spec.propertyId,
                                area.areaId,
                                requestedValue,
                            )
                        }
                    }
                } catch (error: TimeoutCancellationException) {
                    return writeError(
                        spec,
                        area,
                        requestedValue,
                        timeoutError(VehiclePropertyOperation.WRITE, spec, area, timeoutMillis),
                    )
                } catch (cancellation: CancellationException) {
                    rethrowCancellation(cancellation)
                } catch (error: RuntimeException) {
                    return writeError(
                        spec,
                        area,
                        requestedValue,
                        mapPlatformError(
                            error,
                            VehiclePropertyOperation.WRITE,
                            spec.propertyId,
                            area,
                        ),
                    )
                }

                return when (
                    val acknowledgement =
                        waitForAcknowledgement(
                            callbackEvents,
                            spec,
                            area,
                            requestedValue,
                            minOf(callbackBudgetMillis, remainingTimeoutMillis(deadlineNanos)),
                        )
                ) {
                    is WriteAcknowledgement.Confirmed ->
                        VehiclePropertyWriteResult.Confirmed(
                            acknowledgement.value,
                            VehicleWriteConfirmation.CALLBACK,
                        )
                    is WriteAcknowledgement.Failed ->
                        writeError(spec, area, requestedValue, acknowledgement.error)
                    WriteAcknowledgement.TimedOut ->
                        confirmByReadback(
                            session,
                            spec,
                            area,
                            requestedValue,
                            minOf(readbackBudgetMillis, remainingTimeoutMillis(deadlineNanos)),
                            timeoutMillis,
                        )
                }
            } finally {
                callbackEvents.close()
                withContext(NonCancellable + dispatcher) {
                    try {
                        subscription.close()
                    } catch (_: RuntimeException) {
                        // Cleanup is best effort when CarService has died.
                    }
                }
            }
        }

        private suspend fun <T : Any> waitForAcknowledgement(
            callbackEvents: Channel<PlatformPropertyEvent>,
            spec: VehiclePropertySpec<T>,
            area: VehiclePropertyArea,
            requestedValue: T,
            timeoutMillis: Long,
        ): WriteAcknowledgement<T> =
            withTimeoutOrNull(timeoutMillis) {
                while (true) {
                    when (val event = callbackEvents.receive()) {
                        is PlatformPropertyEvent.Error -> {
                            if (event.propertyId == spec.propertyId && event.areaId == area.areaId) {
                                return@withTimeoutOrNull WriteAcknowledgement.Failed(
                                    mapSetErrorCode(spec.propertyId, area, event.errorCode),
                                )
                            }
                        }
                        is PlatformPropertyEvent.Changed -> {
                            if (event.value.propertyId != spec.propertyId ||
                                event.value.areaId != area.areaId
                            ) {
                                continue
                            }
                            if (event.value.status == VehiclePropertyStatus.UNAVAILABLE) {
                                return@withTimeoutOrNull WriteAcknowledgement.Failed(
                                    VehiclePropertyError.Unavailable(
                                        spec.propertyId,
                                        area,
                                        retryable = true,
                                    ),
                                )
                            }
                            if (event.value.status == VehiclePropertyStatus.ERROR) {
                                return@withTimeoutOrNull WriteAcknowledgement.Failed(
                                    VehiclePropertyError.ServiceUnavailable(
                                        VehiclePropertyOperation.WRITE,
                                        spec.propertyId,
                                        area,
                                        "Vehicle callback reported an error status",
                                    ),
                                )
                            }
                            val typed = event.value.toTypedValue(spec)
                            if (typed is VehiclePropertyResult.Failure) {
                                return@withTimeoutOrNull WriteAcknowledgement.Failed(typed.error)
                            }
                            typed as VehiclePropertyResult.Success
                            val actual = typed.value.value
                            if (actual != null && valuesEqual(spec.valueType, requestedValue, actual)) {
                                return@withTimeoutOrNull WriteAcknowledgement.Confirmed(typed.value)
                            }
                        }
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                WriteAcknowledgement.TimedOut
            } ?: WriteAcknowledgement.TimedOut

        private suspend fun <T : Any> confirmByReadback(
            session: PlatformVehicleSession,
            spec: VehiclePropertySpec<T>,
            area: VehiclePropertyArea,
            requestedValue: T,
            readbackTimeoutMillis: Long,
            acknowledgementTimeoutMillis: Long,
        ): VehiclePropertyWriteResult<T> {
            val readback =
                try {
                    withTimeout(readbackTimeoutMillis) {
                        withContext(dispatcher) {
                            session.properties.get(spec.valueType, spec.propertyId, area.areaId)
                        }
                    }
                } catch (error: TimeoutCancellationException) {
                    return writeError(
                        spec,
                        area,
                        requestedValue,
                        timeoutError(
                            VehiclePropertyOperation.WRITE,
                            spec,
                            area,
                            acknowledgementTimeoutMillis,
                        ),
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: RuntimeException) {
                    return writeError(
                        spec,
                        area,
                        requestedValue,
                        mapPlatformError(
                            error,
                            VehiclePropertyOperation.READ,
                            spec.propertyId,
                            area,
                        ),
                    )
                }
            val typed = readback.toTypedValue(spec)
            if (typed is VehiclePropertyResult.Failure) {
                return writeError(spec, area, requestedValue, typed.error)
            }
            typed as VehiclePropertyResult.Success
            val actual = typed.value.value
            return if (actual != null && valuesEqual(spec.valueType, requestedValue, actual)) {
                VehiclePropertyWriteResult.Confirmed(
                    typed.value,
                    VehicleWriteConfirmation.READBACK,
                )
            } else {
                writeError(
                    spec,
                    area,
                    requestedValue,
                    timeoutError(
                        VehiclePropertyOperation.WRITE,
                        spec,
                        area,
                        acknowledgementTimeoutMillis,
                    ),
                )
            }
        }

        private suspend fun <T> callPlatform(
            operation: VehiclePropertyOperation,
            propertyId: Int?,
            area: VehiclePropertyArea?,
            timeoutMillis: Long,
            block: suspend (PlatformVehicleSession) -> T,
        ): VehiclePropertyResult<T> {
            val sessionResult = awaitSession(operation, propertyId, area, timeoutMillis)
            val session =
                when (sessionResult) {
                    is VehiclePropertyResult.Failure -> return sessionResult
                    is VehiclePropertyResult.Success -> sessionResult.value
                }
            return try {
                val value =
                    withTimeout(timeoutMillis) {
                        withContext(dispatcher) { block(session) }
                    }
                VehiclePropertyResult.Success(value)
            } catch (error: TimeoutCancellationException) {
                VehiclePropertyResult.Failure(
                    VehiclePropertyError.Timeout(operation, timeoutMillis, propertyId, area),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: RuntimeException) {
                VehiclePropertyResult.Failure(
                    mapPlatformError(error, operation, propertyId, area),
                )
            }
        }

        private suspend fun awaitSession(
            operation: VehiclePropertyOperation,
            propertyId: Int?,
            area: VehiclePropertyArea?,
            timeoutMillis: Long,
        ): VehiclePropertyResult<PlatformVehicleSession> {
            connection.connect()
            return try {
                val session =
                    withTimeout(timeoutMillis) {
                        connection.sessions.mapNotNull { it }.first()
                    }
                VehiclePropertyResult.Success(session)
            } catch (error: TimeoutCancellationException) {
                VehiclePropertyResult.Failure(
                    VehiclePropertyError.Timeout(operation, timeoutMillis, propertyId, area),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
        }

        private fun mapPlatformError(
            error: RuntimeException,
            operation: VehiclePropertyOperation,
            propertyId: Int?,
            area: VehiclePropertyArea?,
        ): VehiclePropertyError =
            when (error) {
                is PlatformVehicleException.PermissionDenied ->
                    VehiclePropertyError.PermissionDenied(propertyId, area, error.message.orEmpty())
                is PlatformVehicleException.Unavailable ->
                    VehiclePropertyError.Unavailable(
                        propertyId = requireNotNull(propertyId),
                        area = requireNotNull(area),
                        retryable = error.retryable,
                        detailCode = error.detailCode,
                        description = error.message.orEmpty(),
                    )
                is PlatformVehicleException.InvalidArgument ->
                    if (operation == VehiclePropertyOperation.WRITE && area != null) {
                        VehiclePropertyError.InvalidValue(
                            requireNotNull(propertyId),
                            area,
                            error.message.orEmpty(),
                        )
                    } else {
                        VehiclePropertyError.Unsupported(
                            requireNotNull(propertyId),
                            area,
                            error.message.orEmpty(),
                        )
                    }
                is PlatformVehicleException.Service ->
                    VehiclePropertyError.ServiceUnavailable(
                        operation,
                        propertyId,
                        area,
                        error.message.orEmpty(),
                    )
                else ->
                    VehiclePropertyError.ServiceUnavailable(
                        operation,
                        propertyId,
                        area,
                        error.message ?: error.javaClass.simpleName,
                    )
            }

        private fun mapSetErrorCode(
            propertyId: Int,
            area: VehiclePropertyArea,
            errorCode: Int,
        ): VehiclePropertyError =
            when (errorCode) {
                PlatformSetErrorCode.TRY_AGAIN ->
                    VehiclePropertyError.Unavailable(propertyId, area, retryable = true)
                PlatformSetErrorCode.PROPERTY_NOT_AVAILABLE ->
                    VehiclePropertyError.Unavailable(propertyId, area, retryable = false)
                PlatformSetErrorCode.ACCESS_DENIED ->
                    VehiclePropertyError.PermissionDenied(propertyId, area)
                PlatformSetErrorCode.INVALID_ARGUMENT ->
                    VehiclePropertyError.InvalidValue(propertyId, area)
                else -> VehiclePropertyError.WriteRejected(propertyId, area, errorCode)
            }

        private fun <T : Any> PlatformPropertyConfig.toCapability(
            spec: VehiclePropertySpec<T>,
        ): VehiclePropertyResult<VehiclePropertyCapability<T>> {
            if (valueType != spec.valueType) {
                return VehiclePropertyResult.Failure(
                    VehiclePropertyError.TypeMismatch(
                        propertyId = spec.propertyId,
                        expected = spec.valueType,
                        actualTypeName = platformTypeName,
                    ),
                )
            }
            val typedAreas = mutableListOf<VehiclePropertyAreaCapability<T>>()
            for (areaConfig in areas) {
                val converted = areaConfig.toTypedCapability(spec, access)
                if (converted is VehiclePropertyResult.Failure) return converted
                converted as VehiclePropertyResult.Success
                typedAreas += converted.value
            }
            return VehiclePropertyResult.Success(
                VehiclePropertyCapability(
                    spec = spec,
                    access = access,
                    changeMode = changeMode,
                    areaType = areaType,
                    areas = typedAreas,
                    minSampleRateHz = minSampleRateHz,
                    maxSampleRateHz = maxSampleRateHz,
                    configArray = configArray,
                ),
            )
        }

        private fun <T : Any> PlatformAreaConfig.toTypedCapability(
            spec: VehiclePropertySpec<T>,
            propertyAccess: VehiclePropertyAccess,
        ): VehiclePropertyResult<VehiclePropertyAreaCapability<T>> {
            val min = castOptionalConfigValue(spec, minValue)
            if (min is VehiclePropertyResult.Failure) return min
            val max = castOptionalConfigValue(spec, maxValue)
            if (max is VehiclePropertyResult.Failure) return max
            val enumValues = mutableListOf<T>()
            supportedEnumValues.forEach { raw ->
                val typed = castConfigValue(spec, raw)
                if (typed is VehiclePropertyResult.Failure) return typed
                typed as VehiclePropertyResult.Success
                enumValues += typed.value
            }
            return VehiclePropertyResult.Success(
                VehiclePropertyAreaCapability(
                    area = VehiclePropertyArea(areaId),
                    access = if (access == VehiclePropertyAccess.NONE) propertyAccess else access,
                    minValue = (min as VehiclePropertyResult.Success).value,
                    maxValue = (max as VehiclePropertyResult.Success).value,
                    supportedEnumValues = enumValues,
                ),
            )
        }

        private fun <T : Any> PlatformPropertyValue.toTypedValue(
            spec: VehiclePropertySpec<T>,
        ): VehiclePropertyResult<VehiclePropertyValue<T>> {
            if (status == VehiclePropertyStatus.UNAVAILABLE) {
                return VehiclePropertyResult.Failure(
                    VehiclePropertyError.Unavailable(
                        propertyId = spec.propertyId,
                        area = VehiclePropertyArea(areaId),
                        retryable = true,
                    ),
                )
            }
            if (status == VehiclePropertyStatus.ERROR) {
                return VehiclePropertyResult.Failure(
                    VehiclePropertyError.ServiceUnavailable(
                        operation = VehiclePropertyOperation.READ,
                        propertyId = spec.propertyId,
                        area = VehiclePropertyArea(areaId),
                        description = "Vehicle property has error status",
                    ),
                )
            }
            val typedValue =
                value?.let { raw ->
                    val cast = castConfigValue(spec, raw)
                    if (cast is VehiclePropertyResult.Failure) return cast
                    (cast as VehiclePropertyResult.Success).value
                }
            return VehiclePropertyResult.Success(
                VehiclePropertyValue(
                    spec = spec,
                    area = VehiclePropertyArea(areaId),
                    value = typedValue,
                    status = status,
                    timestampNanos = timestampNanos,
                ),
            )
        }

        private fun <T : Any> PlatformPropertyEvent.toTypedEvent(spec: VehiclePropertySpec<T>): VehiclePropertyEvent<T> =
            when (this) {
                is PlatformPropertyEvent.Error ->
                    VehiclePropertyEvent.Error(
                        mapSetErrorCode(propertyId, VehiclePropertyArea(areaId), errorCode),
                    )
                is PlatformPropertyEvent.Changed ->
                    when (val typed = value.toTypedValue(spec)) {
                        is VehiclePropertyResult.Failure -> VehiclePropertyEvent.Error(typed.error)
                        is VehiclePropertyResult.Success -> VehiclePropertyEvent.ValueChanged(typed.value)
                    }
            }

        private fun <T : Any> castOptionalConfigValue(
            spec: VehiclePropertySpec<T>,
            raw: Any?,
        ): VehiclePropertyResult<T?> =
            if (raw == null) {
                VehiclePropertyResult.Success(null)
            } else {
                when (val cast = castConfigValue(spec, raw)) {
                    is VehiclePropertyResult.Failure -> cast
                    is VehiclePropertyResult.Success -> VehiclePropertyResult.Success(cast.value)
                }
            }

        private fun <T : Any> castConfigValue(
            spec: VehiclePropertySpec<T>,
            raw: Any,
        ): VehiclePropertyResult<T> {
            val compatible =
                when (spec.valueType) {
                    VehiclePropertyValueType.BOOLEAN -> raw is Boolean
                    VehiclePropertyValueType.INT -> raw is Int
                    VehiclePropertyValueType.FLOAT -> raw is Float
                }
            if (!compatible) {
                return VehiclePropertyResult.Failure(
                    VehiclePropertyError.TypeMismatch(
                        propertyId = spec.propertyId,
                        expected = spec.valueType,
                        actualTypeName = raw.javaClass.name,
                    ),
                )
            }
            @Suppress("UNCHECKED_CAST")
            return VehiclePropertyResult.Success(raw as T)
        }

        private fun <T : Any> validateValue(
            spec: VehiclePropertySpec<T>,
            areaCapability: VehiclePropertyAreaCapability<T>,
            value: T,
        ): VehiclePropertyError? {
            if (areaCapability.supportedEnumValues.isNotEmpty() &&
                value !in areaCapability.supportedEnumValues
            ) {
                return VehiclePropertyError.InvalidValue(spec.propertyId, areaCapability.area)
            }
            val belowMinimum =
                when (spec.valueType) {
                    VehiclePropertyValueType.BOOLEAN -> false
                    VehiclePropertyValueType.INT ->
                        areaCapability.minValue?.let { (value as Int) < (it as Int) } == true
                    VehiclePropertyValueType.FLOAT ->
                        areaCapability.minValue?.let { (value as Float) < (it as Float) } == true
                }
            val aboveMaximum =
                when (spec.valueType) {
                    VehiclePropertyValueType.BOOLEAN -> false
                    VehiclePropertyValueType.INT ->
                        areaCapability.maxValue?.let { (value as Int) > (it as Int) } == true
                    VehiclePropertyValueType.FLOAT ->
                        areaCapability.maxValue?.let { (value as Float) > (it as Float) } == true
                }
            return if (belowMinimum || aboveMaximum) {
                VehiclePropertyError.InvalidValue(spec.propertyId, areaCapability.area)
            } else {
                null
            }
        }

        private fun valuesEqual(
            type: VehiclePropertyValueType,
            expected: Any,
            actual: Any,
        ): Boolean =
            if (type == VehiclePropertyValueType.FLOAT && expected is Float && actual is Float) {
                abs(expected - actual) <= FLOAT_ACK_EPSILON
            } else {
                expected == actual
            }

        private fun <T : Any> writeError(
            spec: VehiclePropertySpec<T>,
            area: VehiclePropertyArea,
            value: T,
            error: VehiclePropertyError,
        ) = VehiclePropertyWriteResult.Error(spec, area, value, error)

        private fun timeoutError(
            operation: VehiclePropertyOperation,
            spec: VehiclePropertySpec<*>,
            area: VehiclePropertyArea,
            timeoutMillis: Long,
        ) = VehiclePropertyError.Timeout(operation, timeoutMillis, spec.propertyId, area)

        private fun remainingTimeoutMillis(deadlineNanos: Long): Long =
            (
                (deadlineNanos - System.nanoTime() + NANOS_PER_MILLISECOND - 1L) /
                    NANOS_PER_MILLISECOND
            ).coerceAtLeast(1L)

        private sealed interface WriteAcknowledgement<out T : Any> {
            data class Confirmed<T : Any>(
                val value: VehiclePropertyValue<T>,
            ) : WriteAcknowledgement<T>

            data class Failed(
                val error: VehiclePropertyError,
            ) : WriteAcknowledgement<Nothing>

            data object TimedOut : WriteAcknowledgement<Nothing>
        }
    }

private const val READBACK_TIMEOUT_MILLIS = 500L
private const val FLOAT_ACK_EPSILON = 0.001f
private const val NANOS_PER_MILLISECOND = 1_000_000L
