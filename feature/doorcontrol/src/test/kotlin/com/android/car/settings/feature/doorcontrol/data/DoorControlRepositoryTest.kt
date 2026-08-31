package com.android.car.settings.feature.doorcontrol.data

import com.android.car.settings.core.vehicle.VehicleFeatureControllerFactory
import com.android.car.settings.core.vehicle.VehiclePropertyAccess
import com.android.car.settings.core.vehicle.VehiclePropertyArea
import com.android.car.settings.core.vehicle.VehiclePropertyAreaCapability
import com.android.car.settings.core.vehicle.VehiclePropertyAreaType
import com.android.car.settings.core.vehicle.VehiclePropertyCapability
import com.android.car.settings.core.vehicle.VehiclePropertyChangeMode
import com.android.car.settings.core.vehicle.VehiclePropertySpec
import com.android.car.settings.core.vehicle.VehiclePropertyStatus
import com.android.car.settings.core.vehicle.VehiclePropertyValue
import com.android.car.settings.core.vehicle.fake.FakeVehiclePropertyClient
import com.android.car.settings.core.vehicle.fake.FakeVehiclePropertyConnection
import com.android.car.settings.core.vehicle.fake.FakeVehicleUxPolicy
import com.android.car.settings.feature.doorcontrol.domain.DOOR_CONTROL_DEFINITIONS
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DoorControlRepositoryTest {
    @Test fun `fake per-door lock reaches repository`() =
        runTest {
            val definition = DOOR_CONTROL_DEFINITIONS.first()
            val spec = VehiclePropertySpec.boolean(definition.propertyId)
            val area = VehiclePropertyArea(1)
            val capability =
                VehiclePropertyCapability(
                    spec,
                    VehiclePropertyAccess.READ_WRITE,
                    VehiclePropertyChangeMode.ON_CHANGE,
                    VehiclePropertyAreaType.DOOR,
                    listOf(VehiclePropertyAreaCapability(area, VehiclePropertyAccess.READ_WRITE)),
                    0f,
                    0f,
                )
            val value = VehiclePropertyValue(spec, area, true, VehiclePropertyStatus.AVAILABLE, 1)
            val client = FakeVehiclePropertyClient(listOf(capability), listOf(value))
            val controller = DoorControlRepository(factory(client)).createController(backgroundScope)
            runCurrent()
            assertThat(controller.state.value.controls).hasSize(13)
            assertThat(
                controller.state.value
                    .control(definition.id.name)
                    ?.area(1)
                    ?.value,
            ).isEqualTo(true)
        }

    private fun factory(client: FakeVehiclePropertyClient) =
        VehicleFeatureControllerFactory(
            client,
            FakeVehiclePropertyConnection(),
            FakeVehicleUxPolicy(),
        )
}
