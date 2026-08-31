package com.android.car.settings.feature.driverassistance.presentation

import com.android.car.settings.feature.driverassistance.domain.DriverAssistanceId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DriverAssistanceArtworkTest {
    @Test
    fun `every driver assistance id has a unique nonzero artwork`() {
        val ids = DriverAssistanceId.values().toList()
        val drawables = ids.map(::driverAssistanceArtwork)

        assertThat(ids).hasSize(EXPECTED_DRIVER_ASSISTANCE_ARTWORKS)
        assertThat(drawables).doesNotContain(0)
        assertThat(drawables.distinct()).hasSize(EXPECTED_DRIVER_ASSISTANCE_ARTWORKS)
    }

    private companion object {
        const val EXPECTED_DRIVER_ASSISTANCE_ARTWORKS = 30
    }
}
