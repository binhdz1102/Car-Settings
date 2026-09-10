package com.android.car.settings.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleMotionTest {
    @Test
    fun routeCompositionKey_separatesOverviewAndEachCategoryTree() {
        assertEquals("overview", vehicleRouteCompositionKey(null))
        assertEquals("MIRRORS", vehicleRouteCompositionKey("MIRRORS"))
        assertEquals("WINDOWS", vehicleRouteCompositionKey("WINDOWS"))
    }
}
