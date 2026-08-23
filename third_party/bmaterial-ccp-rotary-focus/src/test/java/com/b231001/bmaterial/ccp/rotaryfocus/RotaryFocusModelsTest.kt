package com.b231001.bmaterial.ccp.rotaryfocus

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RotaryFocusModelsTest {
    @Test
    fun identifiersRejectBlankValues() {
        assertFailsWith<IllegalArgumentException> { FocusAreaId(" ") }
        assertFailsWith<IllegalArgumentException> { FocusItemId("") }
    }

    @Test
    fun targetKeepsAreaAndItemIdentitySeparate() {
        val target = RotaryFocusTarget(FocusAreaId("media"), FocusItemId("play"))

        assertEquals("media", target.areaId.value)
        assertEquals("play", target.itemId.value)
    }

    @Test
    fun itemLayoutRejectsNegativeWeight() {
        assertFailsWith<IllegalArgumentException> {
            FocusItemLayout(width = 100.dp, weight = -1f)
        }
    }

    @Test
    fun customAccessibilityRoleRequiresClassName() {
        assertFailsWith<IllegalArgumentException> { FocusItemRole.Custom(" ") }
    }
}
