package com.android.car.settings.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleNavigationFocusTest {
    @Test
    fun rotaryCategoryTransition_parksBeforeReplacingDetailContent() {
        val events = mutableListOf<String>()

        prepareVehicleDetailNavigation(
            isInTouchMode = false,
            parkFocus = {
                events += "park"
                true
            },
            navigate = { events += "navigate" },
        )

        assertEquals(listOf("park", "navigate"), events)
    }

    @Test
    fun touchCategoryTransition_doesNotParkFocus() {
        val events = mutableListOf<String>()

        prepareVehicleDetailNavigation(
            isInTouchMode = true,
            parkFocus = {
                events += "park"
                true
            },
            navigate = { events += "navigate" },
        )

        assertEquals(listOf("navigate"), events)
    }
}
