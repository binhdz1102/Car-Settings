package com.android.car.settings.feature.hvac.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClimateModelsTest {
    @Test
    fun displayValue_usesControlKind() {
        val control = ClimateControl(
            key = "fan",
            capability = ClimateCapability(
                id = ClimateControlId.FAN_SPEED,
                propertyId = 1,
                zone = ClimateZone(1, "Driver"),
                kind = ClimateControlKind.INT_RANGE,
                writable = true,
            ),
            title = "Fan speed",
            section = "Airflow",
            intValue = 4,
            status = ClimateValueStatus.AVAILABLE,
        )

        assertThat(control.displayValue()).isEqualTo("4")
    }
}
