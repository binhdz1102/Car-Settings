package com.android.car.settings.core.vehicle

import com.android.car.settings.core.vehicle.internal.PlatformAreaConfig
import com.android.car.settings.core.vehicle.internal.PlatformPropertyEvent
import com.android.car.settings.core.vehicle.internal.PlatformVehicleException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehiclePropertyClientTest {
    @Test
    fun capability_discoversTypedAreasRangeAndAccess() =
        runTest {
            val gateway = TestPropertyGateway()
            gateway.configs[TEST_PROPERTY_ID] =
                intConfig(
                    areas =
                        listOf(
                            PlatformAreaConfig(
                                TEST_AREA_LEFT,
                                VehiclePropertyAccess.READ_WRITE,
                                minValue = -5,
                                maxValue = 12,
                                supportedEnumValues = listOf(0, 4, 8),
                            ),
                            PlatformAreaConfig(
                                TEST_AREA_RIGHT,
                                VehiclePropertyAccess.READ,
                                minValue = 0,
                                maxValue = 10,
                                supportedEnumValues = emptyList(),
                            ),
                        ),
                )
            val fixture = connectedClient(gateway)

            val result = fixture.client.capability(VehiclePropertySpec.int(TEST_PROPERTY_ID))

            val capability = (result as VehiclePropertyResult.Success).value
            assertThat(capability.areaType).isEqualTo(VehiclePropertyAreaType.SEAT)
            assertThat(capability.changeMode).isEqualTo(VehiclePropertyChangeMode.ON_CHANGE)
            assertThat(capability.areas).hasSize(2)
            assertThat(capability.area(VehiclePropertyArea(TEST_AREA_LEFT))?.minValue).isEqualTo(-5)
            assertThat(capability.area(VehiclePropertyArea(TEST_AREA_LEFT))?.maxValue).isEqualTo(12)
            assertThat(capability.area(VehiclePropertyArea(TEST_AREA_LEFT))?.supportedEnumValues)
                .containsExactly(0, 4, 8)
            assertThat(capability.area(VehiclePropertyArea(TEST_AREA_RIGHT))?.access)
                .isEqualTo(VehiclePropertyAccess.READ)
            fixture.close()
            runCurrent()
        }

    @Test
    fun capability_mapsUnsupportedAndTypeMismatch() =
        runTest {
            val gateway = TestPropertyGateway()
            gateway.configs[TEST_PROPERTY_ID] = intConfig()
            val fixture = connectedClient(gateway)

            val unsupported = fixture.client.capability(VehiclePropertySpec.int(0x11400199))
            val mismatch = fixture.client.capability(VehiclePropertySpec.boolean(TEST_PROPERTY_ID))

            assertThat((unsupported as VehiclePropertyResult.Failure).error)
                .isInstanceOf(VehiclePropertyError.Unsupported::class.java)
            assertThat((mismatch as VehiclePropertyResult.Failure).error)
                .isInstanceOf(VehiclePropertyError.TypeMismatch::class.java)
            fixture.close()
            runCurrent()
        }

    @Test
    fun get_isAreaAwareAndMapsPermissionFailure() =
        runTest {
            val gateway = TestPropertyGateway()
            gateway.configs[TEST_PROPERTY_ID] = intConfig()
            gateway.values[TEST_PROPERTY_ID to TEST_AREA_LEFT] = intValue(7)
            val fixture = connectedClient(gateway)

            val result =
                fixture.client.get(
                    VehiclePropertySpec.int(TEST_PROPERTY_ID),
                    VehiclePropertyArea(TEST_AREA_LEFT),
                )
            gateway.getFailure = PlatformVehicleException.PermissionDenied("denied")
            val denied =
                fixture.client.get(
                    VehiclePropertySpec.int(TEST_PROPERTY_ID),
                    VehiclePropertyArea(TEST_AREA_LEFT),
                )

            assertThat((result as VehiclePropertyResult.Success).value.value).isEqualTo(7)
            assertThat((denied as VehiclePropertyResult.Failure).error)
                .isInstanceOf(VehiclePropertyError.PermissionDenied::class.java)
            fixture.close()
            runCurrent()
        }

    @Test
    fun get_propagatesCoroutineCancellation() =
        runTest {
            val gateway = TestPropertyGateway()
            gateway.configs[TEST_PROPERTY_ID] = intConfig()
            gateway.getFailure = CancellationException("caller cancelled")
            val fixture = connectedClient(gateway)
            var thrown: CancellationException? = null

            try {
                fixture.client.get(
                    VehiclePropertySpec.int(TEST_PROPERTY_ID),
                    VehiclePropertyArea(TEST_AREA_LEFT),
                )
            } catch (cancellation: CancellationException) {
                thrown = cancellation
            }

            assertThat(thrown).isNotNull()
            assertThat(thrown?.message).isEqualTo("caller cancelled")
            fixture.close()
            runCurrent()
        }

    @Test
    fun observe_deliversOnlyRequestedAreaAndClosesSubscription() =
        runTest {
            val gateway = TestPropertyGateway()
            gateway.configs[TEST_PROPERTY_ID] = intConfig()
            val fixture = connectedClient(gateway)
            val events = mutableListOf<VehiclePropertyEvent<Int>>()
            val collectJob =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    fixture.client
                        .observe(
                            VehiclePropertySpec.int(TEST_PROPERTY_ID),
                            setOf(VehiclePropertyArea(TEST_AREA_LEFT)),
                        ).take(1)
                        .toList(events)
                }
            runCurrent()

            gateway.emit(
                PlatformPropertyEvent.Changed(
                    intValue(4, areaId = TEST_AREA_RIGHT),
                ),
            )
            gateway.emit(
                PlatformPropertyEvent.Changed(
                    intValue(8, areaId = TEST_AREA_LEFT),
                ),
            )
            runCurrent()

            val changed = events.single() as VehiclePropertyEvent.ValueChanged
            assertThat(changed.value.area).isEqualTo(VehiclePropertyArea(TEST_AREA_LEFT))
            assertThat(changed.value.value).isEqualTo(8)
            collectJob.join()
            runCurrent()
            assertThat(gateway.subscriptions.single().closed).isTrue()
            fixture.close()
            runCurrent()
        }

    @Test
    fun set_emitsPendingThenCallbackConfirmed() =
        runTest {
            val gateway = writableGateway()
            gateway.onSet = { call ->
                val update = intValue(call.value as Int)
                gateway.values[call.propertyId to call.areaId] = update
                gateway.emit(PlatformPropertyEvent.Changed(update))
            }
            val fixture = connectedClient(gateway)

            val results =
                fixture.client
                    .setInt(TEST_PROPERTY_ID, TEST_AREA_LEFT, 9, acknowledgementTimeoutMillis = 100)
                    .toList()

            assertThat(results.first()).isInstanceOf(VehiclePropertyWriteResult.Pending::class.java)
            val confirmed = results.last() as VehiclePropertyWriteResult.Confirmed
            assertThat(confirmed.value.value).isEqualTo(9)
            assertThat(confirmed.confirmation).isEqualTo(VehicleWriteConfirmation.CALLBACK)
            assertThat(gateway.subscriptions.single().closed).isTrue()
            fixture.close()
            runCurrent()
        }

    @Test
    fun writeOnlySet_skipsReadSubscriptionAndConfirmsSuccessfulSet() =
        runTest {
            val gateway = TestPropertyGateway()
            gateway.configs[TEST_PROPERTY_ID] =
                intConfig(
                    access = VehiclePropertyAccess.WRITE,
                    areas =
                        listOf(
                            PlatformAreaConfig(
                                TEST_AREA_LEFT,
                                VehiclePropertyAccess.WRITE,
                                minValue = 0,
                                maxValue = 3,
                                supportedEnumValues = emptyList(),
                            ),
                        ),
                )
            val fixture = connectedClient(gateway)

            val results =
                fixture.client
                    .setInt(TEST_PROPERTY_ID, TEST_AREA_LEFT, 2, acknowledgementTimeoutMillis = 100)
                    .toList()

            assertThat(results.map { it::class })
                .containsExactly(
                    VehiclePropertyWriteResult.Pending::class,
                    VehiclePropertyWriteResult.Confirmed::class,
                ).inOrder()
            assertThat((results.last() as VehiclePropertyWriteResult.Confirmed).value.value)
                .isEqualTo(2)
            assertThat(gateway.setCalls).hasSize(1)
            assertThat(gateway.subscriptions).isEmpty()
            fixture.close()
            runCurrent()
        }

    @Test
    fun set_usesReadbackWhenCallbackTimesOut() =
        runTest {
            val gateway = writableGateway()
            gateway.onSet = { call ->
                gateway.values[call.propertyId to call.areaId] = intValue(call.value as Int)
            }
            val fixture = connectedClient(gateway)

            val results =
                fixture.client
                    .setInt(TEST_PROPERTY_ID, TEST_AREA_LEFT, 6, acknowledgementTimeoutMillis = 50)
                    .toList()

            val confirmed = results.last() as VehiclePropertyWriteResult.Confirmed
            assertThat(confirmed.value.value).isEqualTo(6)
            assertThat(confirmed.confirmation).isEqualTo(VehicleWriteConfirmation.READBACK)
            fixture.close()
            runCurrent()
        }

    @Test
    fun set_returnsTypedTimeoutWhenCallbackAndReadbackDoNotConfirm() =
        runTest {
            val gateway = writableGateway(initialValue = 2)
            val fixture = connectedClient(gateway)

            val results =
                fixture.client
                    .setInt(TEST_PROPERTY_ID, TEST_AREA_LEFT, 10, acknowledgementTimeoutMillis = 50)
                    .toList()

            val failure = results.last() as VehiclePropertyWriteResult.Error
            assertThat(failure.error).isInstanceOf(VehiclePropertyError.Timeout::class.java)
            fixture.close()
            runCurrent()
        }

    private fun writableGateway(initialValue: Int = 1) =
        TestPropertyGateway().apply {
            configs[TEST_PROPERTY_ID] = intConfig()
            values[TEST_PROPERTY_ID to TEST_AREA_LEFT] = intValue(initialValue)
        }

    private fun TestScope.connectedClient(gateway: TestPropertyGateway): ClientFixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val connector = RecordingConnector()
        val connection =
            DefaultVehiclePropertyConnection(
                connector = connector,
                dispatcher = dispatcher,
                reconnectPolicy =
                    VehicleReconnectPolicy(
                        initialDelayMillis = 25,
                        maxDelayMillis = 25,
                        connectionTimeoutMillis = 1_000,
                    ),
            )
        runCurrent()
        connector.listeners.single().onConnected(testSession(gateway))
        runCurrent()
        return ClientFixture(
            client = DefaultVehiclePropertyClient(connection, dispatcher),
            connection = connection,
        )
    }

    private data class ClientFixture(
        val client: DefaultVehiclePropertyClient,
        val connection: DefaultVehiclePropertyConnection,
    ) {
        fun close() = connection.closeForTest()
    }
}
