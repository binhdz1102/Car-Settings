package com.android.car.settings.feature.driverassistance.domain

import com.android.car.settings.core.vehicle.VehiclePropertyValueType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DriverAssistanceDefinitionsTest {
    @Test
    fun `catalog contains 17 standard and 13 meaningful vendor functions`() {
        assertThat(DRIVER_ASSISTANCE_DEFINITIONS).hasSize(30)
        assertThat(
            DRIVER_ASSISTANCE_DEFINITIONS.count { it.source == DriverAssistanceSource.SYSTEM },
        ).isEqualTo(17)
        assertThat(
            DRIVER_ASSISTANCE_DEFINITIONS.count { it.source == DriverAssistanceSource.VENDOR },
        ).isEqualTo(13)
    }

    @Test
    fun `stable IDs and complete property IDs are unique`() {
        assertThat(DRIVER_ASSISTANCE_DEFINITIONS.map { it.id }.distinct()).hasSize(30)
        assertThat(DRIVER_ASSISTANCE_DEFINITIONS.map { it.propertyId }.distinct()).hasSize(30)
        assertThat(DRIVER_ASSISTANCE_DEFINITIONS.all { it.propertyId != 0 }).isTrue()
    }

    @Test
    fun `vendor encoded value type matches definition`() {
        val valueTypeMask = 0x00ff0000
        val boolType = 0x00200000
        val intType = 0x00400000

        DRIVER_ASSISTANCE_DEFINITIONS
            .filter { it.source == DriverAssistanceSource.VENDOR }
            .forEach { definition ->
                val expected =
                    when (definition.valueType) {
                        VehiclePropertyValueType.BOOLEAN -> boolType
                        VehiclePropertyValueType.INT -> intType
                        VehiclePropertyValueType.FLOAT -> error("No vendor float ADAS control")
                    }
                assertThat(definition.propertyId and valueTypeMask).isEqualTo(expected)
            }
    }
}
