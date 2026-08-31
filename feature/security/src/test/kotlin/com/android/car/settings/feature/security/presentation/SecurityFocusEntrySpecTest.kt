package com.android.car.settings.feature.security.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityFocusEntrySpecTest {
    @Test
    fun unavailableScreenLock_startsAtDeviceAdminAndKeepsItsPhysicalIndex() {
        val spec =
            securityRootFocusSpec(
                canManageScreenLock = false,
                isGuestUser = false,
                isWorking = false,
            )

        assertEquals("security-device-admin", spec.firstContentFocusId)
        assertEquals(mapOf("security-device-admin" to 2), spec.itemIndexByFocusId)
    }

    @Test
    fun availableScreenLock_isTheFirstFocusableSecurityAction() {
        val spec =
            securityRootFocusSpec(
                canManageScreenLock = true,
                isGuestUser = false,
                isWorking = false,
            )

        assertEquals("security-screen-lock", spec.firstContentFocusId)
        assertEquals(
            mapOf(
                "security-screen-lock" to 0,
                "security-clear-credentials" to 1,
                "security-device-admin" to 2,
            ),
            spec.itemIndexByFocusId,
        )
    }
}
