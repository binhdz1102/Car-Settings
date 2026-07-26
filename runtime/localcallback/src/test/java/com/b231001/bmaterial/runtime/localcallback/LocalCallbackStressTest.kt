package com.b231001.bmaterial.runtime.localcallback

import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class LocalCallbackStressTest {

    @Test
    fun tenThousandConcurrentSubscribersStillUseOneRegistration() = runBlocking {
        val source = FakeCallbackSource<Int>()
        val registry = LocalCallbackRegistry(
            defaultConfig = LocalCallbackConfig.events(
                sourceBufferCapacity = 16,
                subscriberBufferCapacity = 1
            )
        )
        val callback = registry.getOrCreate(LocalCallbackKey.create("10k-subscribers")) { source }
        val collectors = List(10_000) {
            launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                callback.events.collect {}
            }
        }

        awaitSubscribers(callback, 10_000)

        assertEquals(1, source.registrationCount.get())
        assertEquals(1, source.maxActiveRegistrationCount.get())
        collectors.forEach { it.cancel() }
        collectors.forEach { it.join() }
        awaitUnregistrations(source, 1)
        assertEquals(0, callback.lifecycle.value.subscriberCount)
        registry.close()
    }

    @Test
    fun twoThousandSubscribersReceiveTwoHundredThousandDeliveries() = runBlocking {
        val subscriberCount = 2_000
        val eventCount = 100
        val expectedDeliveries = subscriberCount.toLong() * eventCount
        val source = FakeCallbackSource<Int>()
        val registry = LocalCallbackRegistry()
        val callback = registry.getOrCreate(
            key = LocalCallbackKey.create("fan-out"),
            config = LocalCallbackConfig.events(
                sourceBufferCapacity = eventCount,
                subscriberBufferCapacity = eventCount
            )
        ) { source }
        val observed = AtomicLong()
        val collectors = List(subscriberCount) {
            launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                callback.events.take(eventCount).collect { observed.incrementAndGet() }
            }
        }
        awaitSubscribers(callback, subscriberCount)

        val durationMillis = measureTimeMillis {
            repeat(eventCount, source::emit)
            withTimeout(STRESS_TIMEOUT_MILLIS) {
                collectors.forEach { it.join() }
            }
        }

        assertEquals(expectedDeliveries, observed.get())
        assertEquals(expectedDeliveries, callback.snapshot().enqueuedDeliveries)
        assertEquals(0, callback.snapshot().droppedDeliveries)
        assertEquals(1, source.maxActiveRegistrationCount.get())
        println(
            "LOCAL_CALLBACK_STRESS subscribers=$subscriberCount events=$eventCount " +
                "deliveries=$expectedDeliveries durationMs=$durationMillis"
        )
        registry.close()
    }

    @Test
    fun concurrentProducersKeepRegistrationInvariantAndDoNotDeadlock() = runBlocking {
        val producerCount = 32
        val eventsPerProducer = 2_000
        val subscriberCount = 32
        val eventCount = producerCount * eventsPerProducer
        val source = FakeCallbackSource<Long>()
        val registry = LocalCallbackRegistry()
        val callback = registry.getOrCreate(
            key = LocalCallbackKey.create("concurrent-producers"),
            config = LocalCallbackConfig.events(
                sourceBufferCapacity = eventCount,
                subscriberBufferCapacity = eventCount
            )
        ) { source }
        val observed = AtomicLong()
        val collectors = List(subscriberCount) {
            launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                callback.events.take(eventCount).collect { observed.incrementAndGet() }
            }
        }
        awaitSubscribers(callback, subscriberCount)

        val durationMillis = measureTimeMillis {
            val producers = List(producerCount) { producer ->
                launch(Dispatchers.Default) {
                    repeat(eventsPerProducer) { index ->
                        source.emit(producer.toLong() shl 32 or index.toLong())
                    }
                }
            }
            withTimeout(STRESS_TIMEOUT_MILLIS) {
                producers.forEach { it.join() }
                collectors.forEach { it.join() }
            }
        }

        val expected = eventCount.toLong() * subscriberCount
        assertEquals(expected, observed.get())
        assertEquals(0, callback.snapshot().droppedSourceEvents)
        assertEquals(0, callback.snapshot().droppedDeliveries)
        assertEquals(1, source.maxActiveRegistrationCount.get())
        assertTrue(durationMillis < STRESS_TIMEOUT_MILLIS)
        println(
            "LOCAL_CALLBACK_CONCURRENT producers=$producerCount subscribers=$subscriberCount " +
                "events=$eventCount deliveries=$expected durationMs=$durationMillis"
        )
        registry.close()
    }

    private suspend fun awaitSubscribers(callback: LocalCallback<*>, expected: Int) {
        withTimeout(STRESS_TIMEOUT_MILLIS) {
            while (callback.lifecycle.value.subscriberCount != expected) {
                delay(1)
            }
            while (!callback.lifecycle.value.isRegistered) {
                delay(1)
            }
        }
    }

    private suspend fun awaitUnregistrations(source: FakeCallbackSource<*>, expected: Int) {
        withTimeout(STRESS_TIMEOUT_MILLIS) {
            while (source.unregistrationCount.get() != expected) {
                delay(1)
            }
        }
    }

    private companion object {
        private const val STRESS_TIMEOUT_MILLIS = 60_000L
    }
}
