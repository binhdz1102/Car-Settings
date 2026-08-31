package com.android.car.settings

import com.android.car.settings.core.settings.SettingsDestinationId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VehicleLandingScreenTest {
    @Test
    fun vehicleFeaturesKeepDriverFirstOrder() {
        assertThat(vehicleFeatureLandingItems.map { it.destination })
            .containsExactly(
                SettingsDestinationId.HVAC,
                SettingsDestinationId.DRIVER_ASSISTANCE,
                SettingsDestinationId.SEAT_CONTROL,
                SettingsDestinationId.DOOR_CONTROL,
                SettingsDestinationId.VEHICLE_LIGHTING,
            ).inOrder()
    }
}
