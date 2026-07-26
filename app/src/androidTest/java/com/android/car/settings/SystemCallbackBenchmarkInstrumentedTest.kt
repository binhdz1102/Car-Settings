package com.android.car.settings

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemCallbackBenchmarkInstrumentedTest {
    @Test
    fun comparesDirectAndLocallySharedRealSystemCallbacks() =
        runBlocking {
            val runner =
                SystemCallbackBenchmarkRunner(
                    context = ApplicationProvider.getApplicationContext(),
                    config =
                        SystemCallbackBenchmarkConfig(
                            consumers = CONSUMERS,
                            measuredRounds = MEASURED_ROUNDS,
                            observationMillis = OBSERVATION_MILLIS,
                        ),
                )
            runner.use {
                val results = it.runAll()
                assertEquals(7, results.size)
                results.forEach { result ->
                    Log.i(TEST_TAG, result.logLine())
                    assertNull("${result.displayName}: ${result.failure}", result.failure)
                    assertEquals(
                        CONSUMERS,
                        requireNotNull(result.direct).externalRegistrations,
                    )
                    assertEquals(1, requireNotNull(result.local).externalRegistrations)
                    assertEquals(
                        "${result.displayName} dropped LocalCallback deliveries",
                        0L,
                        result.local.droppedEvents,
                    )
                }

                // These two emulator sources are continuous. Connectivity/audio/Wi-Fi/Bluetooth/
                // display are event-driven and may legitimately produce zero events while idle.
                listOf("car-property", "sensor").forEach { id ->
                    val result = results.single { it.scenarioId == id }
                    assertTrue(
                        "${result.displayName} direct path received no real callback",
                        requireNotNull(result.direct).platformCallbacksP50 > 0L,
                    )
                    assertTrue(
                        "${result.displayName} LocalCallback path received no real callback",
                        requireNotNull(result.local).platformCallbacksP50 > 0L,
                    )
                    assertTrue(
                        "${result.displayName} LocalCallback delivered no events",
                        result.local.consumerDeliveriesP50 > 0L,
                    )
                }
            }
        }

    private companion object {
        const val TEST_TAG = "SystemCallbackBenchmarkTest"
        const val CONSUMERS = 32
        const val MEASURED_ROUNDS = 5
        const val OBSERVATION_MILLIS = 300L
    }
}
