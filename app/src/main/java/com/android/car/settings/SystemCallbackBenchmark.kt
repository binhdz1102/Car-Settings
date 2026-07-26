package com.android.car.settings

import android.content.Context
import android.os.Process
import android.util.Log
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackConfig
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackEmitter
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackKey
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackRegistry
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.system.measureNanoTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

data class CallbackPathMetrics(
    val externalRegistrations: Int,
    val attachP50Millis: Double,
    val attachP95Millis: Double,
    val detachP50Millis: Double,
    val platformCallbacksP50: Long,
    val consumerDeliveriesP50: Long,
    val processCpuP50Millis: Long,
    val droppedEvents: Long,
)

data class SystemCallbackBenchmarkResult(
    val scenarioId: String,
    val displayName: String,
    val automotiveUse: String,
    val consumers: Int,
    val observationMillis: Long,
    val direct: CallbackPathMetrics? = null,
    val local: CallbackPathMetrics? = null,
    val failure: String? = null,
) {
    val registrationReduction: String
        get() =
            if (direct == null || local == null) {
                "n/a"
            } else {
                "${direct.externalRegistrations}:${local.externalRegistrations}"
            }

    val attachRatio: Double?
        get() {
            val directValue = direct?.attachP50Millis ?: return null
            val localValue = local?.attachP50Millis ?: return null
            if (localValue == 0.0) return null
            return directValue / localValue
        }

    fun logLine(): String {
        if (failure != null) {
            return "SYSTEM_CALLBACK_BENCHMARK FAIL api=$displayName reason=$failure"
        }
        val directMetrics = checkNotNull(direct)
        val localMetrics = checkNotNull(local)
        return buildString {
            append("SYSTEM_CALLBACK_BENCHMARK PASS api=$displayName consumers=$consumers ")
            append("windowMs=$observationMillis ")
            append(
                "direct={apiRegistrations=${directMetrics.externalRegistrations}," +
                    "attachP50Ms=${directMetrics.attachP50Millis.format(3)}," +
                    "attachP95Ms=${directMetrics.attachP95Millis.format(3)}," +
                    "detachP50Ms=${directMetrics.detachP50Millis.format(3)}," +
                    "platformCallbacksP50=${directMetrics.platformCallbacksP50}," +
                    "deliveriesP50=${directMetrics.consumerDeliveriesP50}," +
                    "cpuP50Ms=${directMetrics.processCpuP50Millis}} ",
            )
            append(
                "local={apiRegistrations=${localMetrics.externalRegistrations}," +
                    "attachP50Ms=${localMetrics.attachP50Millis.format(3)}," +
                    "attachP95Ms=${localMetrics.attachP95Millis.format(3)}," +
                    "detachP50Ms=${localMetrics.detachP50Millis.format(3)}," +
                    "platformCallbacksP50=${localMetrics.platformCallbacksP50}," +
                    "deliveriesP50=${localMetrics.consumerDeliveriesP50}," +
                    "cpuP50Ms=${localMetrics.processCpuP50Millis}," +
                    "drops=${localMetrics.droppedEvents}} " +
                    "registrationReduction=$registrationReduction " +
                    "attachRatio=${attachRatio?.format(2) ?: "n/a"}x",
            )
        }
    }
}

data class SystemCallbackBenchmarkConfig(
    val consumers: Int = 32,
    val measuredRounds: Int = 5,
    val warmupConsumers: Int = 4,
    val observationMillis: Long = 300L,
    val lifecycleTimeoutMillis: Long = 5_000L,
) {
    init {
        require(consumers > 0)
        require(measuredRounds > 0)
        require(warmupConsumers > 0)
        require(observationMillis > 0L)
    }
}

/**
 * End-to-end device benchmark. The "direct" path creates N actual system registrations. The
 * LocalCallback path creates N collectors backed by one call to the system registration API.
 * Some Android managers already coalesce Binder/native work internally, so the result deliberately
 * reports observable API registrations and callback invocations rather than claiming Binder-call
 * counts that cannot be inferred from application code.
 *
 * These are application-level measurements, not microbenchmark claims: callbacks are real and the
 * current emulator/service state is intentionally part of the result.
 */
class SystemCallbackBenchmarkRunner(
    context: Context,
    private val config: SystemCallbackBenchmarkConfig = SystemCallbackBenchmarkConfig(),
) : AutoCloseable {
    private val catalog = AutomotiveSystemCallbackCatalog(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val scenarios: List<SystemCallbackScenario>
        get() = catalog.scenarios

    suspend fun runAll(
        onResult: (SystemCallbackBenchmarkResult) -> Unit = {},
    ): List<SystemCallbackBenchmarkResult> =
        withContext(Dispatchers.Default) {
            scenarios.map { scenario ->
                val result = runScenario(scenario)
                Log.i(TAG, result.logLine())
                onResult(result)
                result
            }
        }

    suspend fun runScenario(
        scenario: SystemCallbackScenario,
    ): SystemCallbackBenchmarkResult =
        runCatching {
            // Small warm-up removes most class loading and first binder lookup from measured data.
            runDirectTrial(scenario, config.warmupConsumers, observationMillis = 50L)
            runLocalTrial(scenario, config.warmupConsumers, observationMillis = 50L)

            val directTrials = mutableListOf<PathTrial>()
            val localTrials = mutableListOf<PathTrial>()
            repeat(config.measuredRounds) { round ->
                // Alternate order to reduce systematic bias from thermal/load drift.
                if (round % 2 == 0) {
                    directTrials +=
                        runDirectTrial(scenario, config.consumers, config.observationMillis)
                    localTrials +=
                        runLocalTrial(scenario, config.consumers, config.observationMillis)
                } else {
                    localTrials +=
                        runLocalTrial(scenario, config.consumers, config.observationMillis)
                    directTrials +=
                        runDirectTrial(scenario, config.consumers, config.observationMillis)
                }
            }

            SystemCallbackBenchmarkResult(
                scenarioId = scenario.id,
                displayName = scenario.displayName,
                automotiveUse = scenario.automotiveUse,
                consumers = config.consumers,
                observationMillis = config.observationMillis,
                direct =
                    directTrials.toMetrics(
                        externalRegistrations = config.consumers,
                        local = false,
                    ),
                local =
                    localTrials.toMetrics(
                        externalRegistrations = 1,
                        local = true,
                    ),
            )
        }.getOrElse { failure ->
            Log.e(TAG, "Benchmark failed for ${scenario.displayName}", failure)
            SystemCallbackBenchmarkResult(
                scenarioId = scenario.id,
                displayName = scenario.displayName,
                automotiveUse = scenario.automotiveUse,
                consumers = config.consumers,
                observationMillis = config.observationMillis,
                failure = "${failure::class.java.simpleName}: ${failure.message}",
            )
        }

    private suspend fun runDirectTrial(
        scenario: SystemCallbackScenario,
        consumers: Int,
        observationMillis: Long,
    ): PathTrial {
        val platformCallbacks = AtomicLong()
        val registrations = ArrayList<AutoCloseable>(consumers)
        val emitter =
            object : LocalCallbackEmitter<SystemCallbackEvent> {
                override fun emit(value: SystemCallbackEvent) {
                    platformCallbacks.incrementAndGet()
                }

                override fun fail(cause: Throwable) {
                    throw cause
                }
            }

        val attachNanos =
            try {
                measureNanoTime {
                    repeat(consumers) {
                        registrations += scenario.newSource().register(emitter)
                    }
                }
            } catch (failure: Throwable) {
                registrations.asReversed().forEach { runCatching(it::close) }
                throw failure
            }

        // Initial-state callbacks are part of attach cost, not the steady observation window.
        delay(ATTACH_EVENT_DRAIN_MILLIS)
        platformCallbacks.set(0L)
        val cpuStart = Process.getElapsedCpuTime()
        delay(observationMillis)
        val cpuMillis = Process.getElapsedCpuTime() - cpuStart
        val callbacks = platformCallbacks.get()
        val detachNanos =
            measureNanoTime {
                registrations.asReversed().forEach(AutoCloseable::close)
            }
        // Drain callback messages posted immediately before unregister so the following path does
        // not inherit most of the previous path's scheduler load.
        delay(QUIESCE_MILLIS)
        return PathTrial(
            attachNanos = attachNanos,
            detachNanos = detachNanos,
            platformCallbacks = callbacks,
            consumerDeliveries = callbacks,
            cpuMillis = cpuMillis,
        )
    }

    private suspend fun runLocalTrial(
        scenario: SystemCallbackScenario,
        consumers: Int,
        observationMillis: Long,
    ): PathTrial {
        val registry = LocalCallbackRegistry()
        val callback =
            registry.getOrCreate(
                key = LocalCallbackKey.create("benchmark:${scenario.id}"),
                config =
                    LocalCallbackConfig.events(
                        sourceBufferCapacity = SOURCE_BUFFER_CAPACITY,
                        subscriberBufferCapacity = SUBSCRIBER_BUFFER_CAPACITY,
                    ),
                sourceFactory = scenario.newSource,
            )
        val deliveries = AtomicLong()
        val jobs = ArrayList<Job>(consumers)

        try {
            val attachNanos =
                measureNanoTime {
                    repeat(consumers) {
                        jobs +=
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                callback.events.collect {
                                    deliveries.incrementAndGet()
                                }
                            }
                    }
                    withTimeout(config.lifecycleTimeoutMillis) {
                        while (
                            callback.lifecycle.value.subscriberCount != consumers ||
                            !callback.lifecycle.value.isRegistered
                        ) {
                            delay(1L)
                        }
                    }
                }

            // Drain initial state emitted during registration before starting the steady window.
            delay(ATTACH_EVENT_DRAIN_MILLIS)
            deliveries.set(0L)
            val before = callback.snapshot()
            val cpuStart = Process.getElapsedCpuTime()
            delay(observationMillis)
            val cpuMillis = Process.getElapsedCpuTime() - cpuStart
            var afterObservation = callback.snapshot()
            // snapshot() is intentionally lock-light. Wait briefly for the fan-out worker to
            // account for every source event observed at the sampling boundary, including drops.
            withTimeoutOrNull(SNAPSHOT_COHERENCE_TIMEOUT_MILLIS) {
                while (true) {
                    afterObservation = callback.snapshot()
                    val sourceEvents =
                        afterObservation.sourceEvents - before.sourceEvents
                    val droppedSourceEvents =
                        afterObservation.droppedSourceEvents - before.droppedSourceEvents
                    val handledDeliveries =
                        (afterObservation.enqueuedDeliveries - before.enqueuedDeliveries) +
                            (afterObservation.droppedDeliveries - before.droppedDeliveries)
                    val expectedDeliveries =
                        (sourceEvents - droppedSourceEvents) * consumers
                    if (handledDeliveries >= expectedDeliveries) break
                    delay(1L)
                }
            }
            val detachNanos =
                measureNanoTime {
                    jobs.forEach { it.cancel() }
                    jobs.forEach { it.join() }
                    withTimeout(config.lifecycleTimeoutMillis) {
                        while (callback.lifecycle.value.isRegistered) {
                            delay(1L)
                        }
                    }
                }

            return PathTrial(
                attachNanos = attachNanos,
                detachNanos = detachNanos,
                platformCallbacks = afterObservation.sourceEvents - before.sourceEvents,
                consumerDeliveries =
                    afterObservation.enqueuedDeliveries - before.enqueuedDeliveries,
                cpuMillis = cpuMillis,
                droppedSourceEvents =
                    afterObservation.droppedSourceEvents - before.droppedSourceEvents,
                droppedDeliveries =
                    afterObservation.droppedDeliveries - before.droppedDeliveries,
            )
        } finally {
            jobs.forEach { runCatching { it.cancelAndJoin() } }
            registry.close()
            delay(QUIESCE_MILLIS)
        }
    }

    override fun close() {
        scope.cancel()
        catalog.close()
    }

    private data class PathTrial(
        val attachNanos: Long,
        val detachNanos: Long,
        val platformCallbacks: Long,
        val consumerDeliveries: Long,
        val cpuMillis: Long,
        val droppedSourceEvents: Long = 0L,
        val droppedDeliveries: Long = 0L,
    )

    private fun List<PathTrial>.toMetrics(
        externalRegistrations: Int,
        local: Boolean,
    ) = CallbackPathMetrics(
        externalRegistrations = externalRegistrations,
        attachP50Millis = map { it.attachNanos }.percentile(0.50) / NANOS_PER_MILLI,
        attachP95Millis = map { it.attachNanos }.percentile(0.95) / NANOS_PER_MILLI,
        detachP50Millis = map { it.detachNanos }.percentile(0.50) / NANOS_PER_MILLI,
        platformCallbacksP50 = map { it.platformCallbacks }.percentile(0.50),
        consumerDeliveriesP50 = map { it.consumerDeliveries }.percentile(0.50),
        processCpuP50Millis = map { it.cpuMillis }.percentile(0.50),
        droppedEvents =
            if (local) {
                sumOf { it.droppedSourceEvents + it.droppedDeliveries }
            } else {
                0L
            },
    )

    private fun List<Long>.percentile(fraction: Double): Long {
        check(isNotEmpty())
        val sorted = sorted()
        val index = (ceil(fraction * sorted.size).toInt() - 1).coerceIn(sorted.indices)
        return sorted[index]
    }

    private companion object {
        const val TAG = "SystemCallbackBenchmark"
        const val NANOS_PER_MILLI = 1_000_000.0
        const val SOURCE_BUFFER_CAPACITY = 2_048
        const val SUBSCRIBER_BUFFER_CAPACITY = 512
        const val ATTACH_EVENT_DRAIN_MILLIS = 50L
        const val SNAPSHOT_COHERENCE_TIMEOUT_MILLIS = 250L
        const val QUIESCE_MILLIS = 25L
    }
}

private fun Double.format(digits: Int): String =
    String.format(Locale.US, "%.${digits}f", this)
