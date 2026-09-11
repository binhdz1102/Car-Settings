package com.android.car.settings

import com.android.car.settings.core.settings.SettingsDestinationId
import com.android.car.settings.core.ui.SettingsFocusEntryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationFocusPolicyTest {
    @Test
    fun vehicleDestinationFocus_isPreparedBeforeLoadingRouteNavigation() {
        val focusState = SettingsFocusEntryState()
        val events = mutableListOf<String>()

        prepareVehicleDestinationFocus(
            destinationId = SettingsDestinationId.SEAT_CONTROL,
            route = "vehicle/seat-control",
            isInTouchMode = false,
            focusEntryState = focusState,
            parkFocus = {
                events += "park:${focusState.pendingRequest?.destinationKey}"
                true
            },
        )

        assertEquals(listOf("park:vehicle/seat-control"), events)
        assertEquals("vehicle/seat-control", focusState.pendingRequest?.destinationKey)
    }

    @Test
    fun nonVehicleDestinationAndTouchMode_doNotParkOrCreateVehicleHandoff() {
        val focusState = SettingsFocusEntryState()
        var parked = false

        prepareVehicleDestinationFocus(
            destinationId = SettingsDestinationId.WIFI,
            route = "wifi",
            isInTouchMode = false,
            focusEntryState = focusState,
            parkFocus = {
                parked = true
                true
            },
        )
        assertFalse(parked)
        assertEquals(null, focusState.pendingRequest)

        assertFalse(isVehicleDestination(SettingsDestinationId.VEHICLE))

        prepareVehicleDestinationFocus(
            destinationId = SettingsDestinationId.SEAT_CONTROL,
            route = "vehicle/seat-control",
            isInTouchMode = true,
            focusEntryState = focusState,
            parkFocus = {
                parked = true
                true
            },
        )
        assertEquals(null, focusState.pendingRequest)
    }

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
