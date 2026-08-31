package com.android.car.settings

import com.android.car.settings.core.settings.SettingsDestinationId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationFocusPolicyTest {
    @Test
    fun categoryRootDestinations_areTheOnlyDestinationsThatNeedExplicitShellHandoff() {
        assertTrue(isCategoryRootDestination(SettingsDestinationId.VEHICLE))
        assertTrue(isCategoryRootDestination(SettingsDestinationId.DISPLAY))
        assertTrue(isCategoryRootDestination(SettingsDestinationId.APPLICATIONS))
        assertTrue(isCategoryRootDestination(SettingsDestinationId.PRIVACY))
        assertTrue(isCategoryRootDestination(SettingsDestinationId.LOCATION))
        assertTrue(isCategoryRootDestination(SettingsDestinationId.NETWORK_INTERNET))
        assertTrue(isCategoryRootDestination(SettingsDestinationId.NOTIFICATIONS))
        assertTrue(isCategoryRootDestination(SettingsDestinationId.SOUND))
        assertTrue(isCategoryRootDestination(SettingsDestinationId.PROFILE_ACCOUNTS))
        assertTrue(isCategoryRootDestination(SettingsDestinationId.SECURITY))
        assertFalse(isCategoryRootDestination(SettingsDestinationId.ALL_APPLICATIONS))
        assertFalse(isCategoryRootDestination(SettingsDestinationId.DATE_TIME))
        assertFalse(isCategoryRootDestination(SettingsDestinationId.PRIVACY_MICROPHONE))
    }

    @Test
    fun reselectingVisibleRoot_skipsNavigationSoFocusHandoffKeepsTheDetailTreeAttached() {
        assertFalse(shouldNavigateToDestination("settings-vehicle", "settings-vehicle"))
        assertTrue(shouldNavigateToDestination("settings-vehicle", "settings-display"))
        assertTrue(shouldNavigateToDestination(null, "settings-display"))
    }
}
