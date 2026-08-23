package com.android.car.settings.feature.driverassistance.data

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
import com.android.car.settings.feature.driverassistance.domain.DRIVER_ASSISTANCE_DEFINITIONS
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriverAssistanceRepositoryTest {
    @Test fun `fake capability reaches feature repository without fallback values`() =
        runTest {
            val definition = DRIVER_ASSISTANCE_DEFINITIONS.first()
            val spec = VehiclePropertySpec.boolean(definition.propertyId)
            val client = FakeVehiclePropertyClient(listOf(capability(spec)), listOf(value(spec, true)))
            val controller = DriverAssistanceRepository(factory(client)).createController(backgroundScope)
            runCurrent()
            assertThat(controller.state.value.controls).hasSize(30)
            assertThat(
                controller.state.value
                    .control(definition.id.name)
                    ?.areas
                    ?.single()
                    ?.value,
            ).isEqualTo(true)
        }

    private fun <T : Any> capability(spec: VehiclePropertySpec<T>) =
        VehiclePropertyCapability(
            spec,
            VehiclePropertyAccess.READ_WRITE,
            VehiclePropertyChangeMode.ON_CHANGE,
            VehiclePropertyAreaType.GLOBAL,
            listOf(VehiclePropertyAreaCapability(VehiclePropertyArea.GLOBAL, VehiclePropertyAccess.READ_WRITE)),
            0f,
            0f,
        )

    private fun <T : Any> value(
        spec: VehiclePropertySpec<T>,
        value: T,
    ) = VehiclePropertyValue(
        spec,
        VehiclePropertyArea.GLOBAL,
        value,
        VehiclePropertyStatus.AVAILABLE,
        1,
    )

    private fun factory(client: FakeVehiclePropertyClient) =
        VehicleFeatureControllerFactory(
            client,
            FakeVehiclePropertyConnection(),
            FakeVehicleUxPolicy(),
        )
}
