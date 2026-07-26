package com.b231001.bmaterial.runtime.localcallback

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalCallbackAndroidTest {

    @Test
    fun largeFanOutRunsOnAndroidRuntimeWithOneExternalRegistration() = runBlocking {
        val subscriberCount = 512
        val eventCount = 2_000
        val expectedDeliveries = subscriberCount.toLong() * eventCount
        val source = AndroidFakeSource()
        val registry = LocalCallbackRegistry()
        val callback = registry.getOrCreate(
            key = LocalCallbackKey.create("android-stress"),
            config = LocalCallbackConfig.events(
                sourceBufferCapacity = eventCount,
                subscriberBufferCapacity = eventCount
            )
        ) { source }
        val observed = AtomicLong()
        val collectors = List(subscriberCount) {
            launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                callback.events.take(eventCount).collect {
                    observed.incrementAndGet()
                }
            }
        }
        withTimeout(TEST_TIMEOUT_MILLIS) {
            while (
                callback.lifecycle.value.subscriberCount != subscriberCount ||
                !callback.lifecycle.value.isRegistered
            ) {
                delay(1)
            }
        }

        val durationMillis = measureTimeMillis {
            repeat(eventCount, source::emit)
            withTimeout(TEST_TIMEOUT_MILLIS) {
                collectors.forEach { it.join() }
            }
        }
        withTimeout(TEST_TIMEOUT_MILLIS) {
            while (source.unregistrationCount.get() != 1) {
                delay(1)
            }
        }

        val snapshot = callback.snapshot()
        assertEquals(expectedDeliveries, observed.get())
        assertEquals(expectedDeliveries, snapshot.enqueuedDeliveries)
        assertEquals(0, snapshot.droppedSourceEvents)
        assertEquals(0, snapshot.droppedDeliveries)
        assertEquals(1, source.registrationCount.get())
        assertEquals(1, source.maxActiveRegistrationCount.get())
        assertEquals(1, source.unregistrationCount.get())

        Log.i(
            TAG,
            "LOCAL_CALLBACK_DEVICE_STRESS PASS subscribers=$subscriberCount " +
                "events=$eventCount deliveries=$expectedDeliveries " +
                "durationMs=$durationMillis registrations=1 unregistrations=1"
        )
        registry.close()
    }

    private class AndroidFakeSource : LocalCallbackSource<Int> {
        private lateinit var emitter: LocalCallbackEmitter<Int>

        val registrationCount = AtomicInteger()
        val unregistrationCount = AtomicInteger()
        val activeRegistrationCount = AtomicInteger()
        val maxActiveRegistrationCount = AtomicInteger()

        override fun register(
            emitter: LocalCallbackEmitter<Int>
        ): LocalCallbackRegistration {
            this.emitter = emitter
            registrationCount.incrementAndGet()
            val active = activeRegistrationCount.incrementAndGet()
            maxActiveRegistrationCount.updateAndGet { current -> maxOf(current, active) }
            val closed = AtomicBoolean(false)
            return LocalCallbackRegistration {
                if (closed.compareAndSet(false, true)) {
                    activeRegistrationCount.decrementAndGet()
                    unregistrationCount.incrementAndGet()
                }
            }
        }

        fun emit(value: Int) {
            emitter.emit(value)
        }
    }

    private companion object {
        private const val TAG = "BMaterialLocalCallback"
        private const val TEST_TIMEOUT_MILLIS = 60_000L
    }
}
