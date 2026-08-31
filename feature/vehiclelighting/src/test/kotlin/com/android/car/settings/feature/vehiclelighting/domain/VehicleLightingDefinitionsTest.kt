package com.android.car.settings.feature.vehiclelighting.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VehicleLightingDefinitionsTest {
    @Test fun `lighting properties are unique standard contracts`() {
        assertThat(VEHICLE_LIGHTING_DEFINITIONS).hasSize(8)
        assertThat(VEHICLE_LIGHTING_DEFINITIONS.map { it.propertyId }.distinct()).hasSize(8)
    }
}
