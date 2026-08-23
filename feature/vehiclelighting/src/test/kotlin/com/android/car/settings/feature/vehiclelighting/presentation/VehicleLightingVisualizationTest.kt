package com.android.car.settings.feature.vehiclelighting.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleLightingVisualizationTest {
    @Test
    fun glowTarget_isBoundedForOffOnAndInvalidValues() {
        assertEquals(.16f, lightingGlowTarget(null), 0.0001f)
        assertEquals(.16f, lightingGlowTarget(0f), 0.0001f)
        assertEquals(1f, lightingGlowTarget(1f), 0.0001f)
        assertEquals(1f, lightingGlowTarget(-1f), 0.0001f)
        assertEquals(.16f, lightingGlowTarget(Float.NaN), 0.0001f)
    }

    @Test
    fun booleanSwitchValue_drivesGlowBeforeNumericFallback() {
        assertEquals(1f, lightingGlowTarget(numericValue = null, booleanValue = true), 0.0001f)
        assertEquals(.16f, lightingGlowTarget(numericValue = 1f, booleanValue = false), 0.0001f)
    }
}
