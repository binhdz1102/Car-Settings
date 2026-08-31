package com.android.car.settings.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehiclePendingPresentationTest {
    @Test
    fun pendingControl_keepsItsValueDescriptionAndHasNoTransientSupportingText() {
        assertEquals("On", stableVehicleValueStateDescription("On"))
        assertNull(
            stableVehicleControlAnnotation(
                enabled = true,
                readOnly = false,
                disabledReason = null,
                readOnlyDescription = "Read only",
                unavailableDescription = "Unavailable",
            ),
        )
    }
}
