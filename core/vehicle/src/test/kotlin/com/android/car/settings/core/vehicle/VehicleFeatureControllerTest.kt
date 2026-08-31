package com.android.car.settings.core.vehicle

import com.android.car.settings.core.vehicle.fake.FakeVehiclePropertyClient
import com.android.car.settings.core.vehicle.fake.FakeVehiclePropertyConnection
import com.android.car.settings.core.vehicle.fake.FakeVehicleUxPolicy
import com.android.car.settings.core.vehicle.fake.FakeVehicleWriteBehavior
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleFeatureControllerTest {
    @Test
    fun `discovers capability and reads every area`() =
        runTest {
            val spec = VehiclePropertySpec.int(0x21406100)
            val left = VehiclePropertyArea(1)
            val right = VehiclePropertyArea(4)
            val client =
                FakeVehiclePropertyClient(
                    capabilities = listOf(capability(spec, listOf(left, right))),
                    values =
                        listOf(
                            value(spec, left, 20),
                            value(spec, right, 80),
                        ),
                )
            val controller = createController(client, listOf(definition("position", spec)))

            runCurrent()

            val control = controller.state.value.control("position")
            assertThat(control?.supported).isTrue()
            assertThat(control?.area(1)?.value).isEqualTo(20)
            assertThat(control?.area(4)?.value).isEqualTo(80)
        }

    @Test
    fun `external event updates only matching area`() =
        runTest {
            val spec = VehiclePropertySpec.boolean(0x21206400)
            val left = VehiclePropertyArea(1)
            val right = VehiclePropertyArea(4)
            val client =
                FakeVehiclePropertyClient(
                    capabilities = listOf(capability(spec, listOf(left, right))),
                    values = listOf(value(spec, left, false), value(spec, right, false)),
                )
            val controller = createController(client, listOf(definition("lock", spec)))
            runCurrent()

            client.emitExternal(spec, right, true)
            runCurrent()

            assertThat(
                controller.state.value
                    .control("lock")
                    ?.area(1)
                    ?.value,
            ).isEqualTo(false)
            assertThat(
                controller.state.value
                    .control("lock")
                    ?.area(4)
                    ?.value,
            ).isEqualTo(true)
        }

    @Test
    fun `confirmed write is retained and records platform write`() =
        runTest {
            val spec = VehiclePropertySpec.int(0x21406006)
            val client =
                FakeVehiclePropertyClient(
                    capabilities = listOf(capability(spec, listOf(VehiclePropertyArea.GLOBAL))),
                    values = listOf(value(spec, VehiclePropertyArea.GLOBAL, 0)),
                )
            val controller = createController(client, listOf(definition("offset", spec)))
            runCurrent()

            controller.setInt("offset", 0, 5)
            runCurrent()

            val area =
                controller.state.value
                    .control("offset")
                    ?.area(0)
            assertThat(area?.value).isEqualTo(5)
            assertThat(area?.pending).isFalse()
            assertThat(client.writes.single().value).isEqualTo(5)
        }

    @Test
    fun `write-only capability does not issue a read during refresh`() =
        runTest {
            val spec = VehiclePropertySpec.int(0x15400B80)
            val area = VehiclePropertyArea(1)
            val client =
                FakeVehiclePropertyClient(
                    capabilities =
                        listOf(
                            VehiclePropertyCapability(
                                spec = spec,
                                access = VehiclePropertyAccess.WRITE,
                                changeMode = VehiclePropertyChangeMode.ON_CHANGE,
                                areaType = VehiclePropertyAreaType.SEAT,
                                areas =
                                    listOf(
                                        VehiclePropertyAreaCapability(
                                            area = area,
                                            access = VehiclePropertyAccess.WRITE,
                                            minValue = 0,
                                            maxValue = 3,
                                        ),
                                    ),
                                minSampleRateHz = 0f,
                                maxSampleRateHz = 0f,
                            ),
                        ),
                )
            val controller = createController(client, listOf(definition("memoryRecall", spec)))

            runCurrent()

            val state = controller.state.value.control("memoryRecall")
            assertThat(state?.area(1)?.access).isEqualTo(VehiclePropertyAccess.WRITE)
            assertThat(state?.area(1)?.value).isNull()
            assertThat(state?.area(1)?.status).isEqualTo(VehiclePropertyStatus.AVAILABLE)
            assertThat(state?.area(1)?.error).isNull()
        }

    @Test
    fun `write is optimistic for two seconds then rolls back to confirmed value`() =
        runTest {
            val spec = VehiclePropertySpec.int(0x21406008)
            val area = VehiclePropertyArea.GLOBAL
            val client =
                FakeVehiclePropertyClient(
                    capabilities = listOf(capability(spec, listOf(area))),
                    values = listOf(value(spec, area, 10)),
                ).apply {
                    setWriteBehavior(spec.propertyId, area.areaId, FakeVehicleWriteBehavior.TIMEOUT)
                }
            val controller = createController(client, listOf(definition("position", spec)))
            runCurrent()

            controller.setInt("position", area.areaId, 80)
            runCurrent()

            var state =
                controller.state.value
                    .control("position")
                    ?.area(area.areaId)
            assertThat(state?.value).isEqualTo(80)
            assertThat(state?.confirmedValue).isEqualTo(10)
            assertThat(state?.requestedValue).isEqualTo(80)
            assertThat(state?.pending).isTrue()

            advanceTimeBy(DEFAULT_VEHICLE_WRITE_TIMEOUT_MS - 1)
            runCurrent()
            state =
                controller.state.value
                    .control("position")
                    ?.area(area.areaId)
            assertThat(state?.value).isEqualTo(80)
            assertThat(state?.pending).isTrue()

            advanceTimeBy(1)
            runCurrent()
            state =
                controller.state.value
                    .control("position")
                    ?.area(area.areaId)
            assertThat(state?.value).isEqualTo(10)
            assertThat(state?.confirmedValue).isEqualTo(10)
            assertThat(state?.requestedValue).isNull()
            assertThat(state?.pending).isFalse()
            assertThat(state?.error).isInstanceOf(VehiclePropertyError.Timeout::class.java)
        }

    @Test
    fun `rapid writes keep latest request and an out of order callback cannot repaint it`() =
        runTest {
            val spec = VehiclePropertySpec.int(0x21406009)
            val area = VehiclePropertyArea.GLOBAL
            val client =
                FakeVehiclePropertyClient(
                    capabilities = listOf(capability(spec, listOf(area))),
                    values = listOf(value(spec, area, 0)),
                ).apply {
                    setWriteBehavior(spec.propertyId, area.areaId, FakeVehicleWriteBehavior.TIMEOUT)
                }
            val controller = createController(client, listOf(definition("offset", spec)))
            runCurrent()

            controller.setInt("offset", area.areaId, 1)
            controller.setInt("offset", area.areaId, 2)
            controller.setInt("offset", area.areaId, 3)

            assertThat(
                controller.state.value
                    .control("offset")
                    ?.area(area.areaId)
                    ?.value,
            ).isEqualTo(3)
            assertThat(
                controller.state.value
                    .control("offset")
                    ?.area(area.areaId)
                    ?.pending,
            ).isTrue()
            runCurrent()
            assertThat(client.writes.map { it.value }).containsExactly(3)

            client.emitExternal(spec, area, 1, timestampNanos = 0)
            runCurrent()
            var state =
                controller.state.value
                    .control("offset")
                    ?.area(area.areaId)
            assertThat(state?.value).isEqualTo(3)
            assertThat(state?.confirmedValue).isEqualTo(0)
            assertThat(state?.pending).isTrue()

            advanceTimeBy(DEFAULT_VEHICLE_WRITE_TIMEOUT_MS)
            runCurrent()
            state =
                controller.state.value
                    .control("offset")
                    ?.area(area.areaId)
            assertThat(state?.value).isEqualTo(0)
            assertThat(state?.pending).isFalse()
        }

    @Test
    fun `legitimate external value equal to an earlier request is accepted`() =
        runTest {
            val spec = VehiclePropertySpec.boolean(0x21206402)
            val area = VehiclePropertyArea.GLOBAL
            val client =
                FakeVehiclePropertyClient(
                    capabilities = listOf(capability(spec, listOf(area))),
                    values = listOf(value(spec, area, false)),
                )
            val controller = createController(client, listOf(definition("enabled", spec)))
            runCurrent()

            controller.setBoolean("enabled", area.areaId, true)
            controller.setBoolean("enabled", area.areaId, false)
            runCurrent()
            assertThat(
                controller.state.value
                    .control("enabled")
                    ?.area(area.areaId)
                    ?.value,
            ).isEqualTo(false)

            client.emitExternal(spec, area, true)
            runCurrent()

            val state =
                controller.state.value
                    .control("enabled")
                    ?.area(area.areaId)
            assertThat(state?.value).isEqualTo(true)
            assertThat(state?.confirmedValue).isEqualTo(true)
        }

    @Test
    fun `subscription error cannot clear a newer pending request`() =
        runTest {
            val spec = VehiclePropertySpec.int(0x2140600b)
            val area = VehiclePropertyArea.GLOBAL
            val client =
                FakeVehiclePropertyClient(
                    capabilities = listOf(capability(spec, listOf(area))),
                    values = listOf(value(spec, area, 4)),
                ).apply {
                    setWriteBehavior(spec.propertyId, area.areaId, FakeVehicleWriteBehavior.TIMEOUT)
                }
            val controller = createController(client, listOf(definition("position", spec)))
            runCurrent()

            controller.setInt("position", area.areaId, 8)
            runCurrent()
            client.emitExternalError(
                spec.propertyId,
                area,
                VehiclePropertyError.Unavailable(spec.propertyId, area, retryable = true),
            )
            runCurrent()

            val state =
                controller.state.value
                    .control("position")
                    ?.area(area.areaId)
            assertThat(state?.value).isEqualTo(8)
            assertThat(state?.requestedValue).isEqualTo(8)
            assertThat(state?.pending).isTrue()
            assertThat(state?.error).isNull()
        }

    @Test
    fun `refresh rolls back a pending request before capability reload`() =
        runTest {
            val spec = VehiclePropertySpec.int(0x2140600c)
            val area = VehiclePropertyArea.GLOBAL
            val client =
                FakeVehiclePropertyClient(
                    capabilities = listOf(capability(spec, listOf(area))),
                    values = listOf(value(spec, area, 2)),
                ).apply {
                    setWriteBehavior(spec.propertyId, area.areaId, FakeVehicleWriteBehavior.TIMEOUT)
                }
            val controller = createController(client, listOf(definition("position", spec)))
            runCurrent()
            controller.setInt("position", area.areaId, 9)
            runCurrent()

            controller.refresh()
            val immediate =
                controller.state.value
                    .control("position")
                    ?.area(area.areaId)
            assertThat(immediate?.value).isEqualTo(2)
            assertThat(immediate?.pending).isFalse()
            assertThat(immediate?.requestedValue).isNull()

            runCurrent()
            val refreshed =
                controller.state.value
                    .control("position")
                    ?.area(area.areaId)
            assertThat(refreshed?.value).isEqualTo(2)
            assertThat(refreshed?.pending).isFalse()
        }

    @Test
    fun `writes to separate areas remain independent`() =
        runTest {
            val spec = VehiclePropertySpec.int(0x2140600a)
            val left = VehiclePropertyArea(1)
            val right = VehiclePropertyArea(4)
            val client =
                FakeVehiclePropertyClient(
                    capabilities = listOf(capability(spec, listOf(left, right))),
                    values = listOf(value(spec, left, 10), value(spec, right, 20)),
                ).apply {
                    setWriteBehavior(spec.propertyId, left.areaId, FakeVehicleWriteBehavior.TIMEOUT)
                }
            val controller = createController(client, listOf(definition("position", spec)))
            runCurrent()

            controller.setInt("position", left.areaId, 11)
            controller.setInt("position", right.areaId, 21)
            runCurrent()

            val leftState =
                controller.state.value
                    .control("position")
                    ?.area(left.areaId)
            val rightState =
                controller.state.value
                    .control("position")
                    ?.area(right.areaId)
            assertThat(leftState?.value).isEqualTo(11)
            assertThat(leftState?.pending).isTrue()
            assertThat(rightState?.value).isEqualTo(21)
            assertThat(rightState?.pending).isFalse()
        }

    @Test
    fun `unsupported and UX policy are explicit state`() =
        runTest {
            val client = FakeVehiclePropertyClient()
            val ux = FakeVehicleUxPolicy()
            val controller =
                createController(
                    client,
                    listOf(definition("missing", VehiclePropertySpec.boolean(0x212060ff))),
                    ux,
                )
            runCurrent()
            ux.setState(VehicleUxPolicyState.Restricted(activeRestrictions = 7))
            runCurrent()

            val state = controller.state.value
            assertThat(state.control("missing")?.supported).isFalse()
            assertThat(state.control("missing")?.error)
                .isInstanceOf(VehiclePropertyError.Unsupported::class.java)
            assertThat(state.uxPolicy)
                .isEqualTo(VehicleUxPolicyState.Restricted(activeRestrictions = 7))
        }

    @Test
    fun `disconnect clears stale values and prevents subsequent writes`() =
        runTest {
            val spec = VehiclePropertySpec.int(0x21406007)
            val connection = FakeVehiclePropertyConnection()
            val client =
                FakeVehiclePropertyClient(
                    capabilities = listOf(capability(spec, listOf(VehiclePropertyArea.GLOBAL))),
                    values = listOf(value(spec, VehiclePropertyArea.GLOBAL, 4)),
                )
            val controller =
                createController(
                    client = client,
                    definitions = listOf(definition("seat-position", spec)),
                    connection = connection,
                )
            runCurrent()

            connection.setState(VehicleConnectionState.Disconnected())
            runCurrent()
            controller.setInt("seat-position", 0, 6)
            runCurrent()

            val area =
                controller.state.value
                    .control("seat-position")
                    ?.area(0)
            assertThat(area?.value).isNull()
            assertThat(area?.status).isEqualTo(VehiclePropertyStatus.UNAVAILABLE)
            assertThat(area?.pending).isFalse()
            assertThat(area?.error).isInstanceOf(VehiclePropertyError.ServiceUnavailable::class.java)
            assertThat(client.writes).isEmpty()
        }

    @Test
    fun `restricted UX prevents writes in the controller`() =
        runTest {
            val spec = VehiclePropertySpec.boolean(0x21206401)
            val client =
                FakeVehiclePropertyClient(
                    capabilities = listOf(capability(spec, listOf(VehiclePropertyArea.GLOBAL))),
                    values = listOf(value(spec, VehiclePropertyArea.GLOBAL, false)),
                )
            val controller =
                createController(
                    client = client,
                    definitions = listOf(definition("adas-setting", spec, requiresUnrestrictedUx = true)),
                    uxPolicy = FakeVehicleUxPolicy(VehicleUxPolicyState.Restricted(activeRestrictions = 1)),
                )
            runCurrent()

            controller.setBoolean("adas-setting", 0, true)
            runCurrent()

            assertThat(client.writes).isEmpty()
            assertThat(
                controller.state.value
                    .control("adas-setting")
                    ?.area(0)
                    ?.error,
            ).isInstanceOf(VehiclePropertyError.UxRestricted::class.java)
        }

    private fun <T : Any> capability(
        spec: VehiclePropertySpec<T>,
        areas: List<VehiclePropertyArea>,
    ) = VehiclePropertyCapability(
        spec = spec,
        access = VehiclePropertyAccess.READ_WRITE,
        changeMode = VehiclePropertyChangeMode.ON_CHANGE,
        areaType =
            if (areas == listOf(VehiclePropertyArea.GLOBAL)) {
                VehiclePropertyAreaType.GLOBAL
            } else {
                VehiclePropertyAreaType.SEAT
            },
        areas =
            areas.map {
                VehiclePropertyAreaCapability(
                    area = it,
                    access = VehiclePropertyAccess.READ_WRITE,
                    minValue = null,
                    maxValue = null,
                )
            },
        minSampleRateHz = 0f,
        maxSampleRateHz = 0f,
    )

    private fun <T : Any> value(
        spec: VehiclePropertySpec<T>,
        area: VehiclePropertyArea,
        value: T,
    ) = VehiclePropertyValue(
        spec = spec,
        area = area,
        value = value,
        status = VehiclePropertyStatus.AVAILABLE,
        timestampNanos = 1,
    )

    private fun definition(
        key: String,
        spec: VehiclePropertySpec<*>,
        requiresUnrestrictedUx: Boolean = false,
    ) = VehicleFeatureDefinition(key, spec, requiresUnrestrictedUx)

    private fun kotlinx.coroutines.test.TestScope.createController(
        client: FakeVehiclePropertyClient,
        definitions: List<VehicleFeatureDefinition>,
        uxPolicy: FakeVehicleUxPolicy = FakeVehicleUxPolicy(),
        connection: FakeVehiclePropertyConnection = FakeVehiclePropertyConnection(),
    ): VehicleFeatureController =
        VehicleFeatureControllerFactory(
            client,
            connection,
            uxPolicy,
        ).create(backgroundScope, definitions)
}
