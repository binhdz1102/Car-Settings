package com.android.car.settings.feature.accessibility.presentation

import com.android.car.settings.feature.accessibility.domain.AccessibilityServiceEntry
import com.android.car.settings.feature.accessibility.domain.AccessibilityState
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityRotaryFocusTest {
    @Test
    fun focusIndex_keepsTheScreenReaderBeforeCaptionsAfterAsyncRefresh() {
        val state =
            AccessibilityState(
                screenReaderSupported = true,
                screenReaderName = "Car service",
                services =
                    listOf(
                        AccessibilityServiceEntry(
                            componentName = "com.android.car/.AccessibilityMenuService",
                            label = "Accessibility Menu",
                            description = "",
                            enabled = false,
                        ),
                    ),
            )

        assertEquals(
            linkedMapOf(
                "accessibility-screen-reader" to 1,
                "accessibility-show-captions" to 3,
                "accessibility-caption-text-size" to 4,
                "accessibility-caption-style" to 5,
                "accessibility-service-com.android.car/.AccessibilityMenuService" to 7,
            ),
            accessibilityLazyFocusIndexById(state),
        )
    }

    @Test
    fun loadedScreenReader_replacesTheInitialCaptionsDefaultFocus() {
        val initial = accessibilityRootFocusSpec(AccessibilityState(), isWorking = false)
        val loaded =
            accessibilityRootFocusSpec(
                AccessibilityState(
                    screenReaderSupported = true,
                    screenReaderName = "Car service",
                ),
                isWorking = false,
            )

        assertEquals("accessibility-show-captions", initial.firstContentFocusId)
        assertEquals("accessibility-screen-reader", loaded.firstContentFocusId)
    }
}
