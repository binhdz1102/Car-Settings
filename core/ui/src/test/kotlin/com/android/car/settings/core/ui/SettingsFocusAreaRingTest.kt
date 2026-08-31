package com.android.car.settings.core.ui

import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFocusAreaRingTest {
    @Test
    fun oneArea_wrapsLocallyWithoutLeavingItsPane() {
        val content = FocusAreaId("settings-detail-vehicle")

        val policy = settingsFocusAreaRing(listOf(content)).getValue(content)

        assertTrue(policy.wrapAround)
        assertNull(policy.previousFocusArea)
        assertNull(policy.nextFocusArea)
    }

    @Test
    fun appBarAndMultipleContentAreas_cycleOnlyInsideTheDetailPane() {
        val appBar = FocusAreaId("settings-header-hvac")
        val preview = FocusAreaId("vehicle-preview")
        val zones = FocusAreaId("vehicle-zones")
        val controls = FocusAreaId("vehicle-controls")

        val ring = settingsFocusAreaRing(listOf(appBar, preview, zones, controls))

        assertEquals(controls, ring.getValue(appBar).previousFocusArea)
        assertEquals(preview, ring.getValue(appBar).nextFocusArea)
        assertEquals(appBar, ring.getValue(preview).previousFocusArea)
        assertEquals(zones, ring.getValue(preview).nextFocusArea)
        assertEquals(preview, ring.getValue(zones).previousFocusArea)
        assertEquals(controls, ring.getValue(zones).nextFocusArea)
        assertEquals(zones, ring.getValue(controls).previousFocusArea)
        assertEquals(appBar, ring.getValue(controls).nextFocusArea)
        assertTrue(ring.values.none { policy -> policy.previousFocusArea == SettingsRailFocusAreaId })
    }

    @Test
    fun duplicateAreaId_isRejectedInsteadOfCreatingAnAmbiguousCycle() {
        val content = FocusAreaId("settings-detail")

        assertThrows(IllegalArgumentException::class.java) {
            settingsFocusAreaRing(listOf(content, content))
        }
    }
}
