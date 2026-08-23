package com.b231001.bmaterial.ccp.rotaryfocus

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RotaryInputModePolicyTest {
    @Test
    fun touchEntryWaitsForARealRotaryScrollBeforeActivatingDeferredFocus() {
        assertFalse(
            shouldActivateDeferredFocusForRotary(
                isInTouchMode = true,
                hasDeferredFocus = true,
                isRotaryScroll = false,
            ),
        )
        assertTrue(
            shouldActivateDeferredFocusForRotary(
                isInTouchMode = true,
                hasDeferredFocus = true,
                isRotaryScroll = true,
            ),
        )
    }

    @Test
    fun rotaryModeDoesNotConsumeEventsForASecondFocusRestore() {
        assertFalse(
            shouldActivateDeferredFocusForRotary(
                isInTouchMode = false,
                hasDeferredFocus = true,
                isRotaryScroll = true,
            ),
        )
        assertFalse(
            shouldActivateDeferredFocusForRotary(
                isInTouchMode = true,
                hasDeferredFocus = false,
                isRotaryScroll = true,
            ),
        )
    }
}
