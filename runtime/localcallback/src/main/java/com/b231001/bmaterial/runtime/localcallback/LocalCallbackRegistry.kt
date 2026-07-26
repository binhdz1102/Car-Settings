package com.b231001.bmaterial.runtime.localcallback

import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Owns shared callback registrations for an application, feature, screen, or service scope.
 *
 * Keys are identity-based and registrations are reference-counted by active Flow collectors.
 * Registration and delayed unregistration run on [maintenanceContext]. External callback delivery
 * may arrive on any thread and is serialized into a consistent order for all local subscribers.
 */
public class LocalCallbackRegistry(
    public val defaultConfig: LocalCallbackConfig = LocalCallbackConfig(),
    maintenanceContext: CoroutineContext = Dispatchers.Default
) : AutoCloseable {
    private val lock = Any()
    private val entries = LinkedHashMap<LocalCallbackKey<*>, LocalCallbackEntry<*>>()
    private val registryJob = SupervisorJob()
    private val maintenanceScope = CoroutineScope(
        maintenanceContext.minusKey(Job) + registryJob
    )

    @Volatile
    public var isClosed: Boolean = false
        private set

    /**
     * Returns the existing shared callback for [key], or creates it atomically.
     *
     * [sourceFactory] must only construct an adapter; it must not register the external callback.
     * Reusing a key with a different configuration is rejected to avoid hidden policy changes.
     */
    public fun <T> getOrCreate(
        key: LocalCallbackKey<T>,
        config: LocalCallbackConfig = defaultConfig,
        sourceFactory: () -> LocalCallbackSource<T>
    ): LocalCallback<T> = synchronized(lock) {
        check(!isClosed) { "LocalCallbackRegistry is closed" }

        val existing = entries[key]
        if (existing != null) {
            require(existing.config == config) {
                "LocalCallbackKey '${key.name}' is already bound with a different configuration"
            }
            @Suppress("UNCHECKED_CAST")
            return@synchronized (existing as LocalCallbackEntry<T>).callback
        }

        val entry = LocalCallbackEntry(
            key = key,
            config = config,
            source = sourceFactory(),
            maintenanceScope = maintenanceScope
        )
        entries[key] = entry
        entry.callback
    }

    /** Closes and removes one binding. Existing collectors terminate with a close exception. */
    public fun remove(key: LocalCallbackKey<*>): Boolean {
        val removed = synchronized(lock) {
            entries.remove(key)
        } ?: return false
        removed.close("LocalCallbackKey '${key.name}' was removed")
        return true
    }

    public fun contains(key: LocalCallbackKey<*>): Boolean = synchronized(lock) {
        entries.containsKey(key)
    }

    public fun snapshots(): List<LocalCallbackSnapshot> = synchronized(lock) {
        entries.values.toList()
    }.map { it.snapshot() }

    public override fun close() {
        val toClose = synchronized(lock) {
            if (isClosed) return
            isClosed = true
            entries.values.toList().also { entries.clear() }
        }
        toClose.forEach { it.close("LocalCallbackRegistry was closed") }
        maintenanceScope.cancel()
    }
}

internal class LocalCallbackEntry<T>(
    internal val key: LocalCallbackKey<T>,
    internal val config: LocalCallbackConfig,
    private val source: LocalCallbackSource<T>,
    private val maintenanceScope: CoroutineScope
) {
    private val stateLock = Any()
    private val sourceOperationLock = Any()
    private val subscribers = LinkedHashMap<Long, Channel<T>>()
    private var nextSubscriberId = 0L
    private var generation = 0L
    private var starting = false
    private var registration: LocalCallbackRegistration? = null
    private var stopJob: Job? = null
    private var closed = false
    private var hasLatest = false
    private var latest: Any? = null
    private var lastFailure: String? = null

    private val registrationAttempts = AtomicLong()
    private val successfulRegistrations = AtomicLong()
    private val unregistrations = AtomicLong()
    private val unregistrationFailures = AtomicLong()
    private val registrationFailures = AtomicLong()
    private val fatalSourceFailures = AtomicLong()
    private val sourceEvents = AtomicLong()
    private val droppedSourceEvents = AtomicLong()
    private val enqueuedDeliveries = AtomicLong()
    private val droppedDeliveries = AtomicLong()
    private val replayDeliveries = AtomicLong()

    private val mutableLifecycle = MutableStateFlow(
        LocalCallbackLifecycle(
            status = LocalCallbackStatus.IDLE,
            subscriberCount = 0,
            isRegistered = false
        )
    )

    internal val lifecycle: StateFlow<LocalCallbackLifecycle> = mutableLifecycle.asStateFlow()
    internal val callback: LocalCallback<T> = LocalCallback(this)
    private val ingress = Channel<IngressValue<T>>(capacity = config.sourceBufferCapacity)
    private val ingressJob = maintenanceScope.launch {
        for (incoming in ingress) {
            dispatchFromWorker(incoming.generation, incoming.value)
        }
    }

    internal val events: Flow<T> = flow {
        val mailbox = Channel<T>(capacity = config.subscriberBufferCapacity)
        val subscriberId = attach(mailbox)
        try {
            for (value in mailbox) {
                emit(value)
            }
        } finally {
            mailbox.cancel()
            detach(subscriberId)
        }
    }

    private fun attach(mailbox: Channel<T>): Long {
        var generationToStart: Long? = null
        val subscriberId = synchronized(stateLock) {
            if (closed) {
                mailbox.close(
                    LocalCallbackClosedException(
                        "LocalCallbackKey '${key.name}' is closed"
                    )
                )
                return@synchronized CLOSED_SUBSCRIBER_ID
            }

            stopJob?.cancel()
            stopJob = null
            val id = ++nextSubscriberId
            subscribers[id] = mailbox
            if (config.replayLatest && hasLatest) {
                @Suppress("UNCHECKED_CAST")
                val replayed = mailbox.trySend(latest as T)
                if (replayed.isSuccess) {
                    replayDeliveries.incrementAndGet()
                }
            }

            if (registration == null && !starting) {
                starting = true
                generation += 1L
                generationToStart = generation
            }
            updateLifecycleLocked()
            id
        }

        generationToStart?.let { currentGeneration ->
            maintenanceScope.launch {
                startRegistration(currentGeneration)
            }
        }
        return subscriberId
    }

    private fun detach(subscriberId: Long) {
        if (subscriberId == CLOSED_SUBSCRIBER_ID) return

        var stopImmediately = false
        synchronized(stateLock) {
            subscribers.remove(subscriberId)
            if (subscribers.isEmpty() && (registration != null || starting)) {
                if (config.stopTimeoutMillis == 0L) {
                    stopImmediately = true
                } else {
                    stopJob?.cancel()
                    stopJob = maintenanceScope.launch {
                        delay(config.stopTimeoutMillis)
                        stopIfIdle()
                    }
                }
            }
            updateLifecycleLocked()
        }
        if (stopImmediately) {
            stopIfIdle()
        }
    }

    private fun startRegistration(expectedGeneration: Long) {
        synchronized(sourceOperationLock) {
            val stillNeeded = synchronized(stateLock) {
                !closed && generation == expectedGeneration && starting
            }
            if (!stillNeeded) return

            registrationAttempts.incrementAndGet()
            val created = try {
                source.register(GenerationEmitter(expectedGeneration))
            } catch (failure: Throwable) {
                handleRegistrationFailure(expectedGeneration, failure)
                return
            }

            var closeImmediately = false
            synchronized(stateLock) {
                if (closed || generation != expectedGeneration || !starting) {
                    closeImmediately = true
                } else {
                    starting = false
                    registration = created
                    successfulRegistrations.incrementAndGet()
                    updateLifecycleLocked()
                }
            }
            if (closeImmediately) {
                closeRegistration(created)
            }
        }
    }

    private fun handleRegistrationFailure(
        expectedGeneration: Long,
        failure: Throwable
    ) {
        val toClose = synchronized(stateLock) {
            if (closed || generation != expectedGeneration || !starting) {
                return
            }
            starting = false
            registrationFailures.incrementAndGet()
            lastFailure = failure.describe()
            val channels = subscribers.values.toList()
            subscribers.clear()
            updateLifecycleLocked()
            channels
        }
        toClose.forEach { it.close(failure) }
    }

    private fun stopIfIdle() {
        var toClose: LocalCallbackRegistration? = null
        synchronized(stateLock) {
            if (closed || subscribers.isNotEmpty()) return

            stopJob = null
            generation += 1L
            starting = false
            toClose = registration
            registration = null
            updateLifecycleLocked()
        }
        toClose?.let(::closeRegistration)
    }

    private inner class GenerationEmitter(
        private val emitterGeneration: Long
    ) : LocalCallbackEmitter<T> {
        override fun emit(value: T) {
            enqueueFromSource(emitterGeneration, value)
        }

        override fun fail(cause: Throwable) {
            failGeneration(emitterGeneration, cause)
        }
    }

    private fun enqueueFromSource(expectedGeneration: Long, value: T) {
        val isCurrent = synchronized(stateLock) {
            !closed &&
                generation == expectedGeneration &&
                (starting || registration != null)
        }
        if (!isCurrent) return

        sourceEvents.incrementAndGet()
        val incoming = IngressValue(expectedGeneration, value)
        if (ingress.trySend(incoming).isSuccess) return

        if (config.overflowStrategy == LocalCallbackOverflowStrategy.DROP_LATEST) {
            droppedSourceEvents.incrementAndGet()
            return
        }

        if (ingress.tryReceive().isSuccess) {
            droppedSourceEvents.incrementAndGet()
        }
        if (ingress.trySend(incoming).isFailure) {
            droppedSourceEvents.incrementAndGet()
        }
    }

    private fun dispatchFromWorker(expectedGeneration: Long, value: T) {
        val targets = synchronized(stateLock) {
            if (
                closed ||
                generation != expectedGeneration ||
                (!starting && registration == null)
            ) {
                return
            }
            if (config.replayLatest) {
                hasLatest = true
                latest = value
            }
            subscribers.values.toList()
        }

        targets.forEach { mailbox ->
            deliver(mailbox, value)
        }
    }

    private fun deliver(mailbox: Channel<T>, value: T) {
        if (mailbox.trySend(value).isSuccess) {
            enqueuedDeliveries.incrementAndGet()
            return
        }

        if (config.overflowStrategy == LocalCallbackOverflowStrategy.DROP_LATEST) {
            droppedDeliveries.incrementAndGet()
            return
        }

        if (mailbox.tryReceive().isSuccess) {
            droppedDeliveries.incrementAndGet()
        }
        if (mailbox.trySend(value).isSuccess) {
            enqueuedDeliveries.incrementAndGet()
        } else {
            droppedDeliveries.incrementAndGet()
        }
    }

    private fun failGeneration(expectedGeneration: Long, cause: Throwable) {
        var toUnregister: LocalCallbackRegistration? = null
        val toClose = synchronized(stateLock) {
            if (closed || generation != expectedGeneration) return

            fatalSourceFailures.incrementAndGet()
            lastFailure = cause.describe()
            generation += 1L
            starting = false
            stopJob?.cancel()
            stopJob = null
            toUnregister = registration
            registration = null
            val channels = subscribers.values.toList()
            subscribers.clear()
            updateLifecycleLocked()
            channels
        }
        toClose.forEach { it.close(cause) }
        toUnregister?.let(::closeRegistration)
    }

    internal fun latestValueOrNull(): T? = synchronized(stateLock) {
        if (!hasLatest) {
            null
        } else {
            @Suppress("UNCHECKED_CAST")
            (latest as T)
        }
    }

    internal fun snapshot(): LocalCallbackSnapshot {
        val state = synchronized(stateLock) {
            Triple(mutableLifecycle.value, hasLatest, lastFailure)
        }
        return LocalCallbackSnapshot(
            keyName = key.name,
            lifecycle = state.first,
            registrationAttempts = registrationAttempts.get(),
            successfulRegistrations = successfulRegistrations.get(),
            unregistrations = unregistrations.get(),
            unregistrationFailures = unregistrationFailures.get(),
            registrationFailures = registrationFailures.get(),
            fatalSourceFailures = fatalSourceFailures.get(),
            sourceEvents = sourceEvents.get(),
            droppedSourceEvents = droppedSourceEvents.get(),
            enqueuedDeliveries = enqueuedDeliveries.get(),
            droppedDeliveries = droppedDeliveries.get(),
            replayDeliveries = replayDeliveries.get(),
            hasLatestValue = state.second,
            lastFailure = state.third
        )
    }

    internal fun close(reason: String) {
        var toUnregister: LocalCallbackRegistration? = null
        val toClose = synchronized(stateLock) {
            if (closed) return
            closed = true
            generation += 1L
            starting = false
            stopJob?.cancel()
            stopJob = null
            toUnregister = registration
            registration = null
            val channels = subscribers.values.toList()
            subscribers.clear()
            latest = null
            hasLatest = false
            updateLifecycleLocked()
            channels
        }
        val closeFailure = LocalCallbackClosedException(reason)
        toClose.forEach { it.close(closeFailure) }
        toUnregister?.let(::closeRegistration)
        ingress.cancel()
        ingressJob.cancel()
    }

    private fun closeRegistration(toClose: LocalCallbackRegistration) {
        synchronized(sourceOperationLock) {
            unregistrations.incrementAndGet()
            try {
                toClose.close()
            } catch (failure: Throwable) {
                unregistrationFailures.incrementAndGet()
                synchronized(stateLock) {
                    lastFailure = failure.describe()
                }
            }
        }
    }

    private fun updateLifecycleLocked() {
        val status = when {
            closed -> LocalCallbackStatus.CLOSED
            subscribers.isEmpty() && (registration != null || starting) ->
                LocalCallbackStatus.STOPPING
            starting -> LocalCallbackStatus.STARTING
            registration != null -> LocalCallbackStatus.ACTIVE
            else -> LocalCallbackStatus.IDLE
        }
        mutableLifecycle.value = LocalCallbackLifecycle(
            status = status,
            subscriberCount = subscribers.size,
            isRegistered = registration != null
        )
    }

    private fun Throwable.describe(): String {
        val type = this::class.qualifiedName ?: this::class.simpleName ?: "Throwable"
        return message?.let { "$type: $it" } ?: type
    }

    private companion object {
        private const val CLOSED_SUBSCRIBER_ID = -1L
    }
}

private data class IngressValue<T>(
    val generation: Long,
    val value: T
)
