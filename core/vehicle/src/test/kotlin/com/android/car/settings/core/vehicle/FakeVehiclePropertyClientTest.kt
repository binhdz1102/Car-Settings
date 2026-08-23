package com.android.car.settings.core.vehicle

import com.android.car.settings.core.vehicle.fake.FakeVehiclePropertyClient
import com.android.car.settings.core.vehicle.fake.FakeVehicleWriteBehavior
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeVehiclePropertyClientTest {
    @Test
    fun externalMutation_isDeliveredWithoutRestartAndIsAreaIsolated() =
        runTest {
            val spec = VehiclePropertySpec.int(TEST_PROPERTY_ID)
            val left = VehiclePropertyArea(TEST_AREA_LEFT)
            val right = VehiclePropertyArea(TEST_AREA_RIGHT)
            val fake =
                FakeVehiclePropertyClient(
                    capabilities = listOf(twoAreaCapability(spec)),
                    values =
                        listOf(
                            VehiclePropertyValue(spec, left, 1, VehiclePropertyStatus.AVAILABLE, 1),
                            VehiclePropertyValue(spec, right, 2, VehiclePropertyStatus.AVAILABLE, 2),
                        ),
                )
            var event: VehiclePropertyEvent<Int>? = null
            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    event = fake.observe(spec, setOf(right)).drop(1).first()
                }
            runCurrent()

            fake.emitExternal(spec, left, 7)
            fake.emitExternal(spec, right, 8)
            runCurrent()

            val changed = event as VehiclePropertyEvent.ValueChanged
            assertThat(changed.value.area).isEqualTo(right)
            assertThat(changed.value.value).isEqualTo(8)
            job.cancel()
        }

    @Test
    fun fakeWrite_supportsConfirmedTimeoutAndTypedError() =
        runTest {
            val spec = VehiclePropertySpec.int(TEST_PROPERTY_ID)
            val area = VehiclePropertyArea(TEST_AREA_LEFT)
            val fake =
                FakeVehiclePropertyClient(
                    capabilities = listOf(twoAreaCapability(spec)),
                    values =
                        listOf(
                            VehiclePropertyValue(spec, area, 1, VehiclePropertyStatus.AVAILABLE, 1),
                        ),
                )

            val confirmed = fake.set(spec, area, 4).toList().last()
            fake.setWriteBehavior(TEST_PROPERTY_ID, TEST_AREA_LEFT, FakeVehicleWriteBehavior.TIMEOUT)
            val timeout = fake.set(spec, area, 5, acknowledgementTimeoutMillis = 25).toList().last()
            fake.setWriteBehavior(
                TEST_PROPERTY_ID,
                TEST_AREA_LEFT,
                FakeVehicleWriteBehavior.ERROR,
                VehiclePropertyError.PermissionDenied(TEST_PROPERTY_ID, area),
            )
            val denied = fake.set(spec, area, 6).toList().last()

            assertThat(confirmed).isInstanceOf(VehiclePropertyWriteResult.Confirmed::class.java)
            assertThat((timeout as VehiclePropertyWriteResult.Error).error)
                .isInstanceOf(VehiclePropertyError.Timeout::class.java)
            assertThat((denied as VehiclePropertyWriteResult.Error).error)
                .isInstanceOf(VehiclePropertyError.PermissionDenied::class.java)
        }

    private fun twoAreaCapability(spec: VehiclePropertySpec<Int>) =
        VehiclePropertyCapability(
            spec = spec,
            access = VehiclePropertyAccess.READ_WRITE,
            changeMode = VehiclePropertyChangeMode.ON_CHANGE,
            areaType = VehiclePropertyAreaType.SEAT,
            areas =
                listOf(TEST_AREA_LEFT, TEST_AREA_RIGHT).map { areaId ->
                    VehiclePropertyAreaCapability(
                        area = VehiclePropertyArea(areaId),
                        access = VehiclePropertyAccess.READ_WRITE,
                        minValue = 0,
                        maxValue = 10,
                    )
                },
            minSampleRateHz = 0f,
            maxSampleRateHz = 0f,
        )
}
