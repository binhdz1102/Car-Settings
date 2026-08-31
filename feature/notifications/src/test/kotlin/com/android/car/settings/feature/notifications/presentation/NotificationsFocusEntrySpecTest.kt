package com.android.car.settings.feature.notifications.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationsFocusEntrySpecTest {
    @Test
    fun recentlySentApp_precedesAllAppsAndKeepsTheSectionSlotInItsRevealIndex() {
        val spec =
            notificationsRootFocusSpec(
                recentlySentFocusIds = listOf("notifications-recent-alpha"),
                allAppsFocusIds = listOf("notifications-app-beta"),
                isWorking = false,
            )

        assertEquals("notifications-recent-alpha", spec.firstContentFocusId)
        assertEquals(
            mapOf(
                "notifications-recent-alpha" to 1,
                "notifications-app-beta" to 3,
            ),
            spec.itemIndexByFocusId,
        )
    }

    @Test
    fun emptyInitialData_waitsForAppsInsteadOfTargetingTheSectionHeader() {
        val spec =
            notificationsRootFocusSpec(
                recentlySentFocusIds = emptyList(),
                allAppsFocusIds = emptyList(),
                isWorking = false,
            )

        assertNull(spec.firstContentFocusId)
        assertEquals(emptyMap<String, Int>(), spec.itemIndexByFocusId)
    }
}
