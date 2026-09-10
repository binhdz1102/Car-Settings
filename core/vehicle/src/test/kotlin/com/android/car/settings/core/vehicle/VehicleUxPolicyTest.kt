package com.android.car.settings.core.vehicle

import com.android.car.settings.core.vehicle.internal.PlatformUxRestrictions
import com.android.car.settings.core.vehicle.internal.PlatformVehicleUxGateway
import com.android.car.settings.core.vehicle.internal.PlatformUxSubscription
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleUxPolicyTest {
    @Test
    fun policy_tracksUnrestrictedDistractionOptimizedRestrictedAndDisconnect() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val connector = RecordingConnector()
            val connection =
                DefaultVehiclePropertyConnection(
                    connector,
                    dispatcher,
                    VehicleReconnectPolicy(
                        initialDelayMillis = 25,
                        maxDelayMillis = 25,
                        connectionTimeoutMillis = 1_000,
                    ),
                )
            val gateway = TestUxGateway(PlatformUxRestrictions(false, 0))
            val policy = DefaultVehicleUxPolicy(connection, dispatcher)
            runCurrent()
            connector.listeners.single().onConnected(testSession(uxGateway = gateway))
            runCurrent()

            assertThat(policy.state.value).isEqualTo(VehicleUxPolicyState.Unrestricted)
            gateway.emit(PlatformUxRestrictions(true, 0))
            runCurrent()
            assertThat(policy.state.value)
                .isEqualTo(VehicleUxPolicyState.DistractionOptimizationRequired(0))
            gateway.emit(PlatformUxRestrictions(true, 4))
            runCurrent()
            assertThat(policy.state.value).isEqualTo(VehicleUxPolicyState.Restricted(4))

            connector.listeners.single().onDisconnected("service lost")
            runCurrent()
            assertThat(policy.state.value).isInstanceOf(VehicleUxPolicyState.Unavailable::class.java)
            assertThat(gateway.closed).isTrue()
            connection.closeForTest()
            runCurrent()
        }

    @Test
    fun gatewayFailure_changesPolicyToUnavailable_insteadOfKeepingUnrestricted() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val connector = RecordingConnector()
            val connection =
                DefaultVehiclePropertyConnection(
                    connector,
                    dispatcher,
                    VehicleReconnectPolicy(
                        initialDelayMillis = 25,
                        maxDelayMillis = 25,
                        connectionTimeoutMillis = 1_000,
                    ),
                )
            val policy = DefaultVehicleUxPolicy(connection, dispatcher)
            runCurrent()

            connector.listeners.single().onConnected(
                testSession(uxGateway = TestUxGateway(PlatformUxRestrictions(false, 0))),
            )
            runCurrent()
            assertThat(policy.state.value).isEqualTo(VehicleUxPolicyState.Unrestricted)

            connector.listeners.single().onDisconnected("reconnect")
            runCurrent()
            advanceTimeBy(25)
            runCurrent()
            connector.listeners[1].onConnected(testSession(uxGateway = ThrowingUxGateway()))
            runCurrent()

            assertThat(policy.state.value).isInstanceOf(VehicleUxPolicyState.Unavailable::class.java)
            connection.closeForTest()
            runCurrent()
        }
}

private class ThrowingUxGateway : PlatformVehicleUxGateway {
    override fun current(): PlatformUxRestrictions =
        error("UX restriction service failed")

    override fun subscribe(callback: (PlatformUxRestrictions) -> Unit): PlatformUxSubscription =
        error("UX restriction service failed")
}
