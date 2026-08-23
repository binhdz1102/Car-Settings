package com.android.car.settings.feature.hvac.data

import com.android.car.settings.feature.hvac.domain.ClimateCapability
import com.android.car.settings.feature.hvac.domain.ClimateControl
import com.android.car.settings.feature.hvac.domain.ClimateControlId
import com.android.car.settings.feature.hvac.domain.ClimateControlKind
import com.android.car.settings.feature.hvac.domain.ClimateState
import com.android.car.settings.feature.hvac.domain.ClimateValueStatus
import com.android.car.settings.feature.hvac.domain.ClimateZone
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClimateEventReducerTest {
    @Test
    fun `event updates only matching property and area`() {
        val driver = control(key = "fan:1", areaId = 1, value = 2)
        val passenger = control(key = "fan:4", areaId = 4, value = 3)

        val result =
            ClimateEventReducer.reduce(
                state = ClimateState(connected = true, controls = listOf(driver, passenger)),
                propertyId = FAN_SPEED_PROPERTY,
                areaId = 4,
                available = true,
                value = 6,
            )

        assertThat(result.controls.first { it.key == "fan:1" }.intValue).isEqualTo(2)
        assertThat(result.controls.first { it.key == "fan:4" }.intValue).isEqualTo(6)
    }

    @Test
    fun `unavailable event annotates only matching control`() {
        val driver = control(key = "fan:1", areaId = 1, value = 2)
        val passenger = control(key = "fan:4", areaId = 4, value = 3)

        val result =
            ClimateEventReducer.reduce(
                state = ClimateState(connected = true, controls = listOf(driver, passenger)),
                propertyId = FAN_SPEED_PROPERTY,
                areaId = 1,
                available = false,
                value = null,
            )

        assertThat(result.controls.first { it.key == "fan:1" }.status)
            .isEqualTo(ClimateValueStatus.UNAVAILABLE)
        assertThat(result.controls.first { it.key == "fan:4" }).isEqualTo(passenger)
    }

    private fun control(
        key: String,
        areaId: Int,
        value: Int,
    ): ClimateControl =
        ClimateControl(
            key = key,
            capability =
                ClimateCapability(
                    id = ClimateControlId.FAN_SPEED,
                    propertyId = FAN_SPEED_PROPERTY,
                    zone = ClimateZone(areaId, "Zone $areaId"),
                    kind = ClimateControlKind.INT_RANGE,
                    writable = true,
                ),
            title = "Fan speed",
            section = "Airflow",
            status = ClimateValueStatus.AVAILABLE,
            intValue = value,
        )

    private companion object {
        const val FAN_SPEED_PROPERTY = 0x15400500
    }
}
