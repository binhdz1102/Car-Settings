package com.android.car.settings.core.vehicle

import com.android.car.settings.core.vehicle.internal.PlatformCarConnector
import com.android.car.settings.core.vehicle.internal.PlatformCarRegistration
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehiclePropertyConnectionTest {
    @Test
    fun connection_exposesConnectingAndConnectedState() =
        runTest {
            val connector = RecordingConnector()
            val connection = createConnection(connector)

            runCurrent()
            assertThat(connection.state.value).isEqualTo(VehicleConnectionState.Connecting(1))
            assertThat(connector.openCount).isEqualTo(1)

            connector.listeners.single().onConnected(testSession())
            runCurrent()

            assertThat(connection.state.value).isEqualTo(VehicleConnectionState.Connected(1))
            connection.closeForTest()
            runCurrent()
        }

    @Test
    fun serviceLoss_schedulesBackoffAndReconnectsWithNewGeneration() =
        runTest {
            val connector = RecordingConnector()
            val connection = createConnection(connector)
            runCurrent()
            connector.listeners[0].onConnected(testSession())
            runCurrent()

            connector.listeners[0].onDisconnected("binder died")
            runCurrent()

            val retry = connection.state.value as VehicleConnectionState.RetryScheduled
            assertThat(retry.attempt).isEqualTo(2)
            assertThat(retry.delayMillis).isEqualTo(RETRY_DELAY_MILLIS)
            assertThat(retry.reason.description).isEqualTo("binder died")
            assertThat(connector.registrations[0].closed).isTrue()

            advanceTimeBy(RETRY_DELAY_MILLIS)
            runCurrent()
            assertThat(connector.openCount).isEqualTo(2)
            assertThat(connection.state.value).isEqualTo(VehicleConnectionState.Connecting(2))

            connector.listeners[1].onConnected(testSession())
            runCurrent()
            assertThat(connection.state.value).isEqualTo(VehicleConnectionState.Connected(2))
            connection.closeForTest()
            runCurrent()
        }

    @Test
    fun reconnect_closesOldRegistrationAndStartsImmediately() =
        runTest {
            val connector = RecordingConnector()
            val connection = createConnection(connector)
            runCurrent()
            connector.listeners[0].onConnected(testSession())
            runCurrent()

            connection.reconnect()
            runCurrent()

            assertThat(connector.registrations[0].closed).isTrue()
            assertThat(connector.openCount).isEqualTo(2)
            assertThat(connection.state.value).isEqualTo(VehicleConnectionState.Connecting(1))
            connection.closeForTest()
            runCurrent()
        }

    @Test
    fun connectionTimeout_closesTransportAndSchedulesRetry() =
        runTest {
            val connector = RecordingConnector()
            val connection = createConnection(connector)
            runCurrent()

            advanceTimeBy(CONNECTION_TIMEOUT_MILLIS)
            runCurrent()

            assertThat(connector.registrations.single().closed).isTrue()
            val state = connection.state.value as VehicleConnectionState.RetryScheduled
            assertThat(state.reason.description).contains("Timed out")
            connection.closeForTest()
            runCurrent()
        }

    private fun kotlinx.coroutines.test.TestScope.createConnection(connector: RecordingConnector) =
        DefaultVehiclePropertyConnection(
            connector = connector,
            dispatcher = StandardTestDispatcher(testScheduler),
            reconnectPolicy =
                VehicleReconnectPolicy(
                    initialDelayMillis = RETRY_DELAY_MILLIS,
                    maxDelayMillis = RETRY_DELAY_MILLIS,
                    connectionTimeoutMillis = CONNECTION_TIMEOUT_MILLIS,
                ),
        )
}

internal class RecordingConnector : PlatformCarConnector {
    val listeners = mutableListOf<PlatformCarConnector.Listener>()
    val registrations = mutableListOf<RecordingRegistration>()
    val openCount: Int
        get() = listeners.size

    var openFailure: RuntimeException? = null

    override fun open(listener: PlatformCarConnector.Listener): PlatformCarRegistration {
        openFailure?.let { throw it }
        listeners += listener
        return RecordingRegistration().also(registrations::add)
    }
}

internal class RecordingRegistration : PlatformCarRegistration {
    var closed = false
        private set

    override fun close() {
        closed = true
    }
}

private const val RETRY_DELAY_MILLIS = 25L
private const val CONNECTION_TIMEOUT_MILLIS = 100L
