package com.b231001.bmaterial.runtime.localcallback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class LocalCallbackTest {

    @Test
    fun manyCollectorsShareOneRegistrationAndOneUnregistration() = runTest {
        val source = FakeCallbackSource<Int>()
        val registry = registry()
        val callback = registry.getOrCreate(LocalCallbackKey.create("shared")) { source }
        val results = List(100) {
            async(start = CoroutineStart.UNDISPATCHED) { callback.events.first() }
        }

        runCurrent()
        assertEquals(100, callback.lifecycle.value.subscriberCount)
        assertEquals(1, source.registrationCount.get())

        source.emit(42)
        runCurrent()

        assertEquals(List(100) { 42 }, results.map { it.await() })
        runCurrent()
        assertEquals(1, source.unregistrationCount.get())
        assertEquals(1, source.maxActiveRegistrationCount.get())
        registry.close()
    }

    @Test
    fun collectorIsAttachedBeforeSynchronousInitialCallback() = runTest {
        val source = FakeCallbackSource(InitialValue.Value("initial"))
        val registry = registry()
        val callback = registry.getOrCreate(LocalCallbackKey.create("initial")) { source }
        val result = async(start = CoroutineStart.UNDISPATCHED) { callback.events.first() }

        runCurrent()

        assertEquals("initial", result.await())
        assertEquals(1, source.registrationCount.get())
        registry.close()
    }

    @Test
    fun statePolicyReplaysLatestButEventPolicyDoesNot() = runTest {
        val stateSource = FakeCallbackSource<Int>()
        val eventSource = FakeCallbackSource<Int>()
        val registry = registry()
        val state = registry.getOrCreate(
            key = LocalCallbackKey.create("state"),
            config = LocalCallbackConfig.state(stopTimeoutMillis = 0L)
        ) { stateSource }
        val event = registry.getOrCreate(
            key = LocalCallbackKey.create("event"),
            config = LocalCallbackConfig.events()
        ) { eventSource }

        val stateFirst = async(start = CoroutineStart.UNDISPATCHED) { state.events.first() }
        val eventFirst = async(start = CoroutineStart.UNDISPATCHED) { event.events.first() }
        runCurrent()
        stateSource.emit(7)
        eventSource.emit(7)
        runCurrent()
        assertEquals(7, stateFirst.await())
        assertEquals(7, eventFirst.await())
        runCurrent()

        val replayed = async(start = CoroutineStart.UNDISPATCHED) { state.events.first() }
        val nextEvent = async(start = CoroutineStart.UNDISPATCHED) { event.events.first() }
        runCurrent()
        assertEquals(7, replayed.await())
        assertFalse(nextEvent.isCompleted)

        eventSource.emit(8)
        runCurrent()
        assertEquals(8, nextEvent.await())
        registry.close()
    }

    @Test
    fun stopTimeoutAvoidsRegistrationChurn() = runTest {
        val source = FakeCallbackSource<Int>()
        val registry = registry()
        val callback = registry.getOrCreate(
            key = LocalCallbackKey.create("timeout"),
            config = LocalCallbackConfig.state(stopTimeoutMillis = 1_000L)
        ) { source }

        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            callback.events.collect {}
        }
        runCurrent()
        assertEquals(1, source.registrationCount.get())
        first.cancelAndJoin()
        runCurrent()
        assertEquals(LocalCallbackStatus.STOPPING, callback.lifecycle.value.status)
        assertEquals(0, source.unregistrationCount.get())

        val replacement = launch(start = CoroutineStart.UNDISPATCHED) {
            callback.events.collect {}
        }
        runCurrent()
        advanceTimeBy(1_500L)
        runCurrent()

        assertEquals(1, source.registrationCount.get())
        assertEquals(0, source.unregistrationCount.get())
        replacement.cancelAndJoin()
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, source.unregistrationCount.get())
        registry.close()
    }

    @Test
    fun registrationFailureTerminatesCurrentCollectorsAndNextCollectorRetries() = runTest {
        val source = FakeCallbackSource<Int>()
        source.failNextRegistration()
        val registry = registry()
        val callback = registry.getOrCreate(LocalCallbackKey.create("retry")) { source }
        val failed = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { callback.events.first() }.exceptionOrNull()
        }

        runCurrent()

        assertIs<IllegalStateException>(failed.await())
        assertEquals(1, callback.snapshot().registrationFailures)

        val retry = async(start = CoroutineStart.UNDISPATCHED) { callback.events.first() }
        runCurrent()
        source.emit(9)
        runCurrent()
        assertEquals(9, retry.await())
        assertEquals(1, source.registrationCount.get())
        registry.close()
    }

    @Test
    fun fatalFailureTerminatesCollectorsAndRecordsDiagnostics() = runTest {
        val source = FakeCallbackSource<Int>()
        val registry = registry()
        val callback = registry.getOrCreate(LocalCallbackKey.create("fatal")) { source }
        val failed = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { callback.events.first() }.exceptionOrNull()
        }
        runCurrent()

        source.fail(IllegalArgumentException("source died"))
        runCurrent()

        assertIs<IllegalArgumentException>(failed.await())
        assertEquals(1, callback.snapshot().fatalSourceFailures)
        assertTrue(callback.snapshot().lastFailure.orEmpty().contains("source died"))
        assertEquals(1, source.unregistrationCount.get())
        registry.close()
    }

    @Test
    fun lateEventsFromOldGenerationAreIgnored() = runTest {
        val source = FakeCallbackSource<Int>()
        val registry = registry()
        val callback = registry.getOrCreate(
            key = LocalCallbackKey.create("generation"),
            config = LocalCallbackConfig.events()
        ) { source }

        val first = async(start = CoroutineStart.UNDISPATCHED) { callback.events.first() }
        runCurrent()
        source.emit(1)
        runCurrent()
        assertEquals(1, first.await())
        runCurrent()

        val second = async(start = CoroutineStart.UNDISPATCHED) { callback.events.first() }
        runCurrent()
        source.emitFromRegistration(0, 99)
        runCurrent()
        assertFalse(second.isCompleted)
        source.emitFromRegistration(1, 2)
        runCurrent()

        assertEquals(2, second.await())
        assertEquals(2, source.registrationCount.get())
        registry.close()
    }

    @Test
    fun subscriberOverflowPoliciesAreDeterministic() = runTest {
        val oldestSource = FakeCallbackSource<Int>()
        val latestSource = FakeCallbackSource<Int>()
        val registry = registry()
        val keepNewest = registry.getOrCreate(
            key = LocalCallbackKey.create("drop-oldest"),
            config = LocalCallbackConfig.events(
                sourceBufferCapacity = 8,
                subscriberBufferCapacity = 2,
                overflowStrategy = LocalCallbackOverflowStrategy.DROP_OLDEST
            )
        ) { oldestSource }
        val keepQueued = registry.getOrCreate(
            key = LocalCallbackKey.create("drop-latest"),
            config = LocalCallbackConfig.events(
                sourceBufferCapacity = 8,
                subscriberBufferCapacity = 2,
                overflowStrategy = LocalCallbackOverflowStrategy.DROP_LATEST
            )
        ) { latestSource }
        val newestValues = mutableListOf<Int>()
        val queuedValues = mutableListOf<Int>()
        val newestFirstSeen = CompletableDeferred<Unit>()
        val queuedFirstSeen = CompletableDeferred<Unit>()
        val releaseConsumers = CompletableDeferred<Unit>()
        val newestCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            keepNewest.events.take(3).collect { value ->
                newestValues += value
                if (newestValues.size == 1) {
                    newestFirstSeen.complete(Unit)
                    releaseConsumers.await()
                }
            }
        }
        val queuedCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            keepQueued.events.take(3).collect { value ->
                queuedValues += value
                if (queuedValues.size == 1) {
                    queuedFirstSeen.complete(Unit)
                    releaseConsumers.await()
                }
            }
        }
        runCurrent()

        oldestSource.emit(1)
        latestSource.emit(1)
        runCurrent()
        newestFirstSeen.await()
        queuedFirstSeen.await()

        listOf(2, 3, 4).forEach {
            oldestSource.emit(it)
            latestSource.emit(it)
        }
        runCurrent()
        releaseConsumers.complete(Unit)
        runCurrent()
        newestCollector.join()
        queuedCollector.join()

        assertEquals(listOf(1, 3, 4), newestValues)
        assertEquals(listOf(1, 2, 3), queuedValues)
        assertEquals(1, keepNewest.snapshot().droppedDeliveries)
        assertEquals(1, keepQueued.snapshot().droppedDeliveries)
        registry.close()
    }

    @Test
    fun keyIdentityConfigValidationRemovalAndCloseAreExplicit() = runTest {
        val key = LocalCallbackKey.create<Int>("same-name")
        val otherKey = LocalCallbackKey.create<Int>("same-name")
        val registry = registry()
        val callback = registry.getOrCreate(key) { FakeCallbackSource() }
        val same = registry.getOrCreate(key) { FakeCallbackSource() }
        val different = registry.getOrCreate(otherKey) { FakeCallbackSource() }

        assertTrue(callback === same)
        assertFalse(callback === different)
        assertFailsWith<IllegalArgumentException> {
            registry.getOrCreate(
                key = key,
                config = LocalCallbackConfig.events()
            ) { FakeCallbackSource() }
        }
        assertTrue(registry.remove(key))
        assertFalse(registry.contains(key))
        assertEquals(LocalCallbackStatus.CLOSED, callback.lifecycle.value.status)
        assertNull(callback.latestValueOrNull())

        registry.close()
        registry.close()
        assertFailsWith<IllegalStateException> {
            registry.getOrCreate(LocalCallbackKey.create("closed")) {
                FakeCallbackSource<Int>()
            }
        }
    }

    private fun TestScope.registry(): LocalCallbackRegistry = LocalCallbackRegistry(
        defaultConfig = LocalCallbackConfig.state(stopTimeoutMillis = 0L),
        maintenanceContext = coroutineContext
    )
}
