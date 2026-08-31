@file:Suppress("LargeClass", "TooManyFunctions")

package com.android.car.settings.core.vehicle

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

private const val VEHICLE_LOG_TAG = "MySystemVehicle"

/** A feature-owned stable key paired with one typed VHAL property. */
data class VehicleFeatureDefinition(
    val key: String,
    val spec: VehiclePropertySpec<*>,
    val requiresUnrestrictedUx: Boolean = false,
) {
    init {
        require(key.isNotBlank()) { "Vehicle feature keys must not be blank" }
    }
}

data class VehicleFeatureAreaState(
    val area: VehiclePropertyArea,
    val access: VehiclePropertyAccess,
    val minValue: Any? = null,
    val maxValue: Any? = null,
    val supportedEnumValues: List<Any> = emptyList(),
    val value: Any? = null,
    /** Last value acknowledged by CarService/VHAL; used as the rollback target. */
    val confirmedValue: Any? = null,
    /** Latest optimistic request while [pending] is true. */
    val requestedValue: Any? = null,
    /** Monotonic platform timestamp used to reject out-of-order callbacks. */
    val confirmedTimestampNanos: Long = Long.MIN_VALUE,
    val status: VehiclePropertyStatus = VehiclePropertyStatus.UNAVAILABLE,
    val pending: Boolean = false,
    val error: VehiclePropertyError? = null,
)

data class VehicleFeatureControlState(
    val definition: VehicleFeatureDefinition,
    val supported: Boolean = false,
    val access: VehiclePropertyAccess = VehiclePropertyAccess.NONE,
    val changeMode: VehiclePropertyChangeMode = VehiclePropertyChangeMode.UNKNOWN,
    val areaType: VehiclePropertyAreaType = VehiclePropertyAreaType.UNKNOWN,
    val configArray: List<Int> = emptyList(),
    val areas: List<VehicleFeatureAreaState> = emptyList(),
    val error: VehiclePropertyError? = null,
) {
    fun area(areaId: Int): VehicleFeatureAreaState? = areas.firstOrNull { it.area.areaId == areaId }
}

data class VehicleFeatureState(
    val loading: Boolean = true,
    val connection: VehicleConnectionState = VehicleConnectionState.Disconnected(),
    val uxPolicy: VehicleUxPolicyState = VehicleUxPolicyState.Unrestricted,
    val controls: List<VehicleFeatureControlState> = emptyList(),
) {
    fun control(key: String): VehicleFeatureControlState? = controls.firstOrNull { it.definition.key == key }
}

/** Creates feature-scoped state holders while all of them share the same Hilt Car connection. */
@Singleton
class VehicleFeatureControllerFactory
    @Inject
    constructor(
        private val client: VehiclePropertyClient,
        private val connection: VehiclePropertyConnection,
        private val uxPolicy: VehicleUxPolicy,
    ) {
        fun create(
            scope: CoroutineScope,
            definitions: List<VehicleFeatureDefinition>,
        ): VehicleFeatureController =
            VehicleFeatureController(
                scope = scope,
                definitions = definitions,
                client = client,
                connection = connection,
                uxPolicy = uxPolicy,
            )
    }

/**
 * Capability-driven feature state. It never supplies local production fallback values: a value is
 * shown only after a successful CarPropertyManager read or callback.
 */
class VehicleFeatureController internal constructor(
    private val scope: CoroutineScope,
    definitions: List<VehicleFeatureDefinition>,
    private val client: VehiclePropertyClient,
    private val connection: VehiclePropertyConnection,
    uxPolicy: VehicleUxPolicy,
) {
    private val uniqueDefinitions = definitions.distinctBy { it.key }
    private val mutableState =
        MutableStateFlow(
            VehicleFeatureState(
                controls = uniqueDefinitions.map(::VehicleFeatureControlState),
                connection = connection.state.value,
                uxPolicy = uxPolicy.state.value,
            ),
        )
    val state: StateFlow<VehicleFeatureState> = mutableState.asStateFlow()

    private var refreshJob: Job? = null
    private var lastConnectedGeneration: Long? = null
    private val subscriptionJobs = mutableListOf<Job>()
    private val writeJobs = mutableMapOf<WriteKey, Job>()
    private val activeWrites = mutableMapOf<WriteKey, ActiveWrite>()
    private val writeLock = Any()
    private var nextWriteGeneration = 0L

    init {
        require(uniqueDefinitions.size == definitions.size) { "Vehicle feature keys must be unique" }
        require(
            uniqueDefinitions.map { it.spec.propertyId }.distinct().size == uniqueDefinitions.size,
        ) { "Vehicle property IDs must be unique within a feature" }

        connection.connect()
        scope.launch {
            connection.state.collect { connectionState ->
                mutableState.update { it.copy(connection = connectionState) }
                if (connectionState is VehicleConnectionState.Connected &&
                    connectionState.generation != lastConnectedGeneration
                ) {
                    lastConnectedGeneration = connectionState.generation
                    refresh()
                } else if (connectionState !is VehicleConnectionState.Connected) {
                    transitionToDisconnectedState(connectionState)
                }
            }
        }
        scope.launch {
            uxPolicy.state.collect { state -> mutableState.update { it.copy(uxPolicy = state) } }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        cancelWrites()
        refreshJob =
            scope.launch {
                if (connection.state.value !is VehicleConnectionState.Connected) {
                    transitionToDisconnectedState(connection.state.value)
                    return@launch
                }
                mutableState.update { current ->
                    current.copy(
                        loading = true,
                        controls =
                            current.controls.map {
                                it.copy(error = null, areas = it.areas.map { area -> area.copy(error = null) })
                            },
                    )
                }
                try {
                    val discovered = client.discoverCapabilities(uniqueDefinitions.map { it.spec })
                    val controls =
                        uniqueDefinitions.map { definition ->
                            when (val result = discovered[definition.spec.propertyId]) {
                                is VehiclePropertyResult.Success -> result.value.toControlState(definition)
                                is VehiclePropertyResult.Failure ->
                                    VehicleFeatureControlState(
                                        definition = definition,
                                        error = result.error,
                                    )
                                null ->
                                    VehicleFeatureControlState(
                                        definition = definition,
                                        error = VehiclePropertyError.Unsupported(definition.spec.propertyId),
                                    )
                            }
                        }
                    controls.filter { !it.supported }.forEach { control ->
                        control.error?.let { logFailure("capability fallback", it) }
                    }
                    mutableState.update { it.copy(loading = false, controls = controls) }
                    restartSubscriptions(controls)
                    controls.filter { it.supported && it.access.canRead }.forEach { control ->
                        control.areas.filter { it.access.canRead }.forEach { area ->
                            read(control.definition, area.area)
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    logError("Capability refresh failed; showing safe unavailable state", error)
                    val fallback =
                        VehiclePropertyError.ServiceUnavailable(
                            operation = VehiclePropertyOperation.CAPABILITY,
                            description = "Vehicle capability discovery failed",
                        )
                    mutableState.update { current ->
                        current.copy(
                            loading = false,
                            controls =
                                current.controls.map { control ->
                                    control.copy(
                                        error = fallback,
                                        areas =
                                            control.areas.map { area ->
                                                area.copy(
                                                    value = null,
                                                    status = VehiclePropertyStatus.UNAVAILABLE,
                                                    pending = false,
                                                    error = fallback,
                                                )
                                            },
                                    )
                                },
                        )
                    }
                }
            }
    }

    fun reconnect() {
        connection.reconnect()
    }

    fun setBoolean(
        key: String,
        areaId: Int,
        value: Boolean,
    ) = set(key, areaId, VehiclePropertyValueType.BOOLEAN, value)

    fun setInt(
        key: String,
        areaId: Int,
        value: Int,
    ) = set(key, areaId, VehiclePropertyValueType.INT, value)

    fun setFloat(
        key: String,
        areaId: Int,
        value: Float,
    ) = set(key, areaId, VehiclePropertyValueType.FLOAT, value)

    @Suppress("ReturnCount")
    private fun set(
        key: String,
        areaId: Int,
        expectedType: VehiclePropertyValueType,
        value: Any,
    ) {
        val definition = uniqueDefinitions.firstOrNull { it.key == key } ?: return
        val area = VehiclePropertyArea(areaId)
        if (definition.spec.valueType != expectedType) {
            rejectWrite(
                definition,
                area,
                VehiclePropertyError.TypeMismatch(
                    definition.spec.propertyId,
                    definition.spec.valueType,
                    expectedType.name,
                    area,
                ),
            )
            return
        }
        val control = state.value.control(key)
        val rejection =
            when {
                connection.state.value !is VehicleConnectionState.Connected ->
                    VehiclePropertyError.ServiceUnavailable(
                        operation = VehiclePropertyOperation.WRITE,
                        propertyId = definition.spec.propertyId,
                        area = area,
                        description = "Vehicle service is not connected",
                    )
                control?.supported != true -> VehiclePropertyError.Unsupported(definition.spec.propertyId, area)
                control.area(areaId) == null -> VehiclePropertyError.InvalidArea(definition.spec.propertyId, area)
                control.area(areaId)?.access?.canWrite != true ->
                    VehiclePropertyError.WriteNotAllowed(definition.spec.propertyId, area)
                definition.requiresUnrestrictedUx && state.value.uxPolicy !is VehicleUxPolicyState.Unrestricted ->
                    VehiclePropertyError.UxRestricted(definition.spec.propertyId, area)
                else -> null
            }
        if (rejection != null) {
            rejectWrite(definition, area, rejection)
            return
        }

        val writeKey = WriteKey(definition.spec.propertyId, areaId)
        val previousJob: Job?
        val generation: Long
        synchronized(writeLock) {
            generation = ++nextWriteGeneration
            previousJob = writeJobs.remove(writeKey)
            activeWrites[writeKey] = ActiveWrite(generation, value)
            updateArea(definition.spec.propertyId, area) { current ->
                current.copy(
                    value = value,
                    confirmedValue = current.confirmedValue ?: current.value,
                    requestedValue = value,
                    pending = true,
                    error = null,
                )
            }
        }

        previousJob?.cancel()
        val writeJob =
            scope.launch {
                if (!isActiveWrite(writeKey, generation)) return@launch
                try {
                    withTimeout(DEFAULT_VEHICLE_WRITE_TIMEOUT_MS) {
                        val results =
                            when (expectedType) {
                                VehiclePropertyValueType.BOOLEAN ->
                                    client.setBoolean(definition.spec.propertyId, areaId, value as Boolean)
                                VehiclePropertyValueType.INT ->
                                    client.setInt(definition.spec.propertyId, areaId, value as Int)
                                VehiclePropertyValueType.FLOAT ->
                                    client.setFloat(definition.spec.propertyId, areaId, value as Float)
                            }
                        results.collect { result -> handleWriteResult(writeKey, generation, result) }
                    }
                } catch (_: TimeoutCancellationException) {
                    handleWriteResult(
                        writeKey,
                        generation,
                        VehiclePropertyWriteResult.Error(
                            spec = typedSpec(definition.spec, expectedType),
                            area = area,
                            requestedValue = value,
                            error =
                                VehiclePropertyError.Timeout(
                                    operation = VehiclePropertyOperation.WRITE,
                                    timeoutMillis = DEFAULT_VEHICLE_WRITE_TIMEOUT_MS,
                                    propertyId = definition.spec.propertyId,
                                    area = area,
                                ),
                        ),
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    handleWriteResult(
                        writeKey,
                        generation,
                        VehiclePropertyWriteResult.Error(
                            spec = typedSpec(definition.spec, expectedType),
                            area = area,
                            requestedValue = value,
                            error =
                                VehiclePropertyError.ServiceUnavailable(
                                    operation = VehiclePropertyOperation.WRITE,
                                    propertyId = definition.spec.propertyId,
                                    area = area,
                                    description = error.message ?: error.javaClass.simpleName,
                                ),
                        ),
                    )
                }
            }
        synchronized(writeLock) {
            if (activeWrites[writeKey]?.generation == generation) {
                writeJobs[writeKey] = writeJob
            } else {
                writeJob.cancel()
            }
        }
    }

    private fun restartSubscriptions(controls: List<VehicleFeatureControlState>) {
        stopSubscriptions()
        controls.filter { it.supported && it.access.canRead }.forEach { control ->
            val definition = control.definition
            val areas = control.areas.mapTo(linkedSetOf()) { it.area }
            val job =
                when (definition.spec.valueType) {
                    VehiclePropertyValueType.BOOLEAN ->
                        scope.launch {
                            client
                                .observe(VehiclePropertySpec.boolean(definition.spec.propertyId), areas)
                                .collect(::handleEvent)
                        }
                    VehiclePropertyValueType.INT ->
                        scope.launch {
                            client
                                .observe(VehiclePropertySpec.int(definition.spec.propertyId), areas)
                                .collect(::handleEvent)
                        }
                    VehiclePropertyValueType.FLOAT ->
                        scope.launch {
                            client
                                .observe(VehiclePropertySpec.float(definition.spec.propertyId), areas)
                                .collect(::handleEvent)
                        }
                }
            subscriptionJobs += job
        }
    }

    private suspend fun read(
        definition: VehicleFeatureDefinition,
        area: VehiclePropertyArea,
    ) {
        when (definition.spec.valueType) {
            VehiclePropertyValueType.BOOLEAN ->
                handleReadResult(client.getBoolean(definition.spec.propertyId, area.areaId))
            VehiclePropertyValueType.INT ->
                handleReadResult(client.getInt(definition.spec.propertyId, area.areaId))
            VehiclePropertyValueType.FLOAT ->
                handleReadResult(client.getFloat(definition.spec.propertyId, area.areaId))
        }
    }

    private fun handleReadResult(result: VehiclePropertyResult<VehiclePropertyValue<*>>) {
        when (result) {
            is VehiclePropertyResult.Success -> updateValue(result.value)
            is VehiclePropertyResult.Failure -> {
                logFailure("read", result.error)
                updateError(result.error)
            }
        }
    }

    private fun handleEvent(event: VehiclePropertyEvent<*>) {
        when (event) {
            is VehiclePropertyEvent.ValueChanged -> updateValue(event.value)
            is VehiclePropertyEvent.Error -> {
                logFailure("subscription", event.error)
                updateError(event.error)
            }
        }
    }

    private fun handleWriteResult(
        writeKey: WriteKey,
        generation: Long,
        result: VehiclePropertyWriteResult<*>,
    ) {
        synchronized(writeLock) {
            val active = activeWrites[writeKey]
            if (active?.generation != generation) {
                logWarning(
                    "Ignoring stale write result property=0x${writeKey.propertyId.toUInt().toString(16)} " +
                        "area=${writeKey.areaId} generation=$generation latest=${active?.generation}",
                )
                return
            }
            when (result) {
                is VehiclePropertyWriteResult.Pending ->
                    updateArea(result.spec.propertyId, result.area) {
                        it.copy(
                            value = active.requestedValue,
                            requestedValue = active.requestedValue,
                            pending = true,
                            error = null,
                        )
                    }
                is VehiclePropertyWriteResult.Confirmed -> {
                    finishWriteLocked(writeKey, generation)
                    updateArea(result.value.spec.propertyId, result.value.area) {
                        it.copy(
                            value = result.value.value,
                            confirmedValue = result.value.value,
                            requestedValue = null,
                            confirmedTimestampNanos =
                                maxOf(it.confirmedTimestampNanos, result.value.timestampNanos),
                            status = result.value.status,
                            pending = false,
                            error = null,
                        )
                    }
                }
                is VehiclePropertyWriteResult.Error -> {
                    logFailure("write", result.error)
                    finishWriteLocked(writeKey, generation)
                    updateArea(result.spec.propertyId, result.area) {
                        it.copy(
                            value = it.confirmedValue,
                            requestedValue = null,
                            pending = false,
                            error = result.error,
                        )
                    }
                }
            }
        }
    }

    private fun rejectWrite(
        definition: VehicleFeatureDefinition,
        area: VehiclePropertyArea,
        error: VehiclePropertyError,
    ) {
        logFailure("write prevented", error)
        if (state.value.control(definition.key)?.area(area.areaId) != null) {
            updateArea(definition.spec.propertyId, area) { it.copy(pending = false, error = error) }
        } else {
            updateControlError(definition.spec.propertyId, error)
        }
    }

    private fun transitionToDisconnectedState(connectionState: VehicleConnectionState) {
        refreshJob?.cancel()
        cancelWrites()
        stopSubscriptions()
        val error =
            when (connectionState) {
                is VehicleConnectionState.Disconnected ->
                    connectionState.reason
                        ?: VehiclePropertyError.ServiceUnavailable(
                            VehiclePropertyOperation.CONNECT,
                            description = "Vehicle service is disconnected",
                        )
                is VehicleConnectionState.RetryScheduled -> connectionState.reason
                is VehicleConnectionState.Connecting ->
                    VehiclePropertyError.ServiceUnavailable(
                        VehiclePropertyOperation.CONNECT,
                        description = "Vehicle service is connecting",
                    )
                is VehicleConnectionState.Connected -> return
            }
        logWarning("Vehicle connection unavailable: ${error.description}")
        mutableState.update { current ->
            current.copy(
                loading = false,
                controls =
                    current.controls.map { control ->
                        control.copy(
                            error = error,
                            areas =
                                control.areas.map { area ->
                                    area.copy(
                                        value = null,
                                        confirmedValue = null,
                                        requestedValue = null,
                                        confirmedTimestampNanos = Long.MIN_VALUE,
                                        status = VehiclePropertyStatus.UNAVAILABLE,
                                        pending = false,
                                        error = error,
                                    )
                                },
                        )
                    },
            )
        }
    }

    private fun stopSubscriptions() {
        subscriptionJobs.forEach(Job::cancel)
        subscriptionJobs.clear()
    }

    private fun cancelWrites() {
        val jobs =
            synchronized(writeLock) {
                val activeJobs = writeJobs.values.toList()
                writeJobs.clear()
                activeWrites.clear()
                mutableState.update { current ->
                    current.copy(
                        controls =
                            current.controls.map { control ->
                                control.copy(
                                    areas =
                                        control.areas.map { area ->
                                            if (area.pending || area.requestedValue != null) {
                                                area.copy(
                                                    value = area.confirmedValue,
                                                    requestedValue = null,
                                                    pending = false,
                                                )
                                            } else {
                                                area
                                            }
                                        },
                                )
                            },
                    )
                }
                activeJobs
            }
        jobs.forEach(Job::cancel)
    }

    private fun finishWriteLocked(
        key: WriteKey,
        generation: Long,
    ) {
        if (activeWrites[key]?.generation == generation) {
            activeWrites.remove(key)
            writeJobs.remove(key)
        }
    }

    private fun isActiveWrite(
        key: WriteKey,
        generation: Long,
    ): Boolean = synchronized(writeLock) { activeWrites[key]?.generation == generation }

    private fun updateControlError(
        propertyId: Int,
        error: VehiclePropertyError,
    ) {
        mutableState.update { current ->
            current.copy(
                controls =
                    current.controls.map { control ->
                        if (control.definition.spec.propertyId == propertyId) {
                            control.copy(error = error)
                        } else {
                            control
                        }
                    },
            )
        }
    }

    private fun logFailure(
        stage: String,
        error: VehiclePropertyError,
    ) {
        val property = error.propertyId?.let { "0x${it.toUInt().toString(16)}" } ?: "none"
        val area = error.area?.areaId?.toString() ?: "none"
        logWarning("$stage property=$property area=$area: ${error.description}")
    }

    private fun updateValue(value: VehiclePropertyValue<*>) {
        val key = WriteKey(value.spec.propertyId, value.area.areaId)
        synchronized(writeLock) {
            val activeWrite = activeWrites[key]
            updateArea(value.spec.propertyId, value.area) { current ->
                when {
                    value.timestampNanos < current.confirmedTimestampNanos -> current
                    // Keep the requested value visible while the write flow owns acknowledgement,
                    // but remember every newer platform event as the truthful rollback target.
                    activeWrite != null ->
                        current.copy(
                            value = activeWrite.requestedValue,
                            confirmedValue = value.value,
                            confirmedTimestampNanos = value.timestampNanos,
                            status = value.status,
                            requestedValue = activeWrite.requestedValue,
                            pending = true,
                            error = null,
                        )
                    else -> {
                        current.copy(
                            value = value.value,
                            confirmedValue = value.value,
                            requestedValue = null,
                            confirmedTimestampNanos = value.timestampNanos,
                            status = value.status,
                            pending = false,
                            error = null,
                        )
                    }
                }
            }
        }
    }

    private fun updateError(error: VehiclePropertyError) {
        // Keep the active-write check and the error mutation in one transaction.  A
        // subscription error may arrive on an IO collector at the same time that a caller starts
        // a new write; checking under a short-lived lock and updating afterwards could clear the
        // optimistic request that won the latest-wins race.
        synchronized(writeLock) {
            val propertyId = error.propertyId ?: return
            val area = error.area
            if (area != null && activeWrites.containsKey(WriteKey(propertyId, area.areaId))) {
                logWarning(
                    "Deferring subscription/read error while latest write is pending property=" +
                        "0x${propertyId.toUInt().toString(16)} area=${area.areaId}: ${error.description}",
                )
                return
            }
            if (area == null) {
                mutableState.update { current ->
                    current.copy(
                        controls =
                            current.controls.map { control ->
                                if (control.definition.spec.propertyId == propertyId) {
                                    control.copy(error = error)
                                } else {
                                    control
                                }
                            },
                    )
                }
            } else {
                updateArea(propertyId, area) { state ->
                    state.copy(
                        status =
                            if (error is VehiclePropertyError.Unavailable) {
                                VehiclePropertyStatus.UNAVAILABLE
                            } else {
                                VehiclePropertyStatus.ERROR
                            },
                        pending = false,
                        requestedValue = null,
                        error = error,
                    )
                }
            }
        }
    }

    private fun updateArea(
        propertyId: Int,
        area: VehiclePropertyArea,
        transform: (VehicleFeatureAreaState) -> VehicleFeatureAreaState,
    ) {
        mutableState.update { current ->
            current.copy(
                controls =
                    current.controls.map { control ->
                        if (control.definition.spec.propertyId == propertyId) {
                            control.copy(
                                error = null,
                                areas =
                                    control.areas.map { areaState ->
                                        if (areaState.area == area) transform(areaState) else areaState
                                    },
                            )
                        } else {
                            control
                        }
                    },
            )
        }
    }
}

private data class WriteKey(
    val propertyId: Int,
    val areaId: Int,
)

private data class ActiveWrite(
    val generation: Long,
    val requestedValue: Any,
)

@Suppress("UNCHECKED_CAST")
private fun typedSpec(
    spec: VehiclePropertySpec<*>,
    expectedType: VehiclePropertyValueType,
): VehiclePropertySpec<Any> {
    check(spec.valueType == expectedType)
    return spec as VehiclePropertySpec<Any>
}

private fun VehiclePropertyCapability<*>.toControlState(definition: VehicleFeatureDefinition): VehicleFeatureControlState =
    VehicleFeatureControlState(
        definition = definition,
        supported = true,
        access = access,
        changeMode = changeMode,
        areaType = areaType,
        configArray = configArray,
        areas =
            areas.map { area ->
                VehicleFeatureAreaState(
                    area = area.area,
                    access = area.access,
                    minValue = area.minValue,
                    maxValue = area.maxValue,
                    supportedEnumValues = area.supportedEnumValues,
                    // A WRITE-only capability has no current value to mark AVAILABLE through a
                    // GET, but the capability itself is sufficient to render its action editor.
                    status =
                        if (area.access.canWrite && !area.access.canRead) {
                            VehiclePropertyStatus.AVAILABLE
                        } else {
                            VehiclePropertyStatus.UNAVAILABLE
                        },
                )
            },
    )

private fun logWarning(message: String) {
    if (isAndroidRuntime()) {
        Log.w(VEHICLE_LOG_TAG, message)
    } else {
        System.err.println("W/$VEHICLE_LOG_TAG: $message")
    }
}

private fun logError(
    message: String,
    error: Throwable,
) {
    if (isAndroidRuntime()) {
        Log.e(VEHICLE_LOG_TAG, message, error)
    } else {
        System.err.println("E/$VEHICLE_LOG_TAG: $message: ${error.message}")
    }
}

private fun isAndroidRuntime(): Boolean {
    val vmName = System.getProperty("java.vm.name").orEmpty()
    return vmName == "Dalvik" || vmName == "ART"
}
