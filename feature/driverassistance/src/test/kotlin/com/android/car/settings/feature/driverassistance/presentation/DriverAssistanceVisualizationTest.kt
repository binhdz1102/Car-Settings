package com.android.car.settings.feature.driverassistance.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class DriverAssistanceVisualizationTest {
    @Test
    fun illustrationFamily_distinguishesMajorGuidanceOverlays() {
        assertEquals(
            DriverAssistanceIllustrationFamily.LANE,
            driverAssistanceIllustrationFamily("LANE_DEPARTURE_WARNING"),
        )
        assertEquals(
            DriverAssistanceIllustrationFamily.BLIND_SPOT,
            driverAssistanceIllustrationFamily("BLIND_SPOT_WARNING"),
        )
        assertEquals(
            DriverAssistanceIllustrationFamily.CROSS_TRAFFIC,
            driverAssistanceIllustrationFamily("CROSS_TRAFFIC_MONITORING"),
        )
        assertEquals(
            DriverAssistanceIllustrationFamily.COLLISION,
            driverAssistanceIllustrationFamily("FORWARD_COLLISION_WARNING"),
        )
        assertEquals(
            DriverAssistanceIllustrationFamily.ADAPTIVE_CRUISE,
            driverAssistanceIllustrationFamily("ADAPTIVE_CRUISE_ENABLED"),
        )
        assertEquals(
            DriverAssistanceIllustrationFamily.PARKING,
            driverAssistanceIllustrationFamily("PARKING_VOLUME"),
        )
    }

    @Test
    fun unknownControl_usesGenericInstructionalOverlay() {
        assertEquals(
            DriverAssistanceIllustrationFamily.GENERIC,
            driverAssistanceIllustrationFamily("DRIVER_MONITORING"),
        )
    }
}
