package com.android.car.settings

import com.android.car.settings.navigation.SettingsIntentRouter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InitialDestinationPolicyTest {
    @Test
    fun homeLaunch_startsAtTheDefaultRouteWithoutFirstRenderingHome() {
        assertThat(
            resolveInitialDestinationRoute(
                requestedRoute = SettingsIntentRouter.HOME_ROUTE,
                requestedAllowed = true,
                homeRoute = SettingsIntentRouter.HOME_ROUTE,
                defaultRoute = "vehicle",
            ),
        ).isEqualTo("vehicle")
    }

    @Test
    fun allowedDeepLink_keepsItsRequestedRoute() {
        assertThat(
            resolveInitialDestinationRoute(
                requestedRoute = "display",
                requestedAllowed = true,
                homeRoute = SettingsIntentRouter.HOME_ROUTE,
                defaultRoute = "vehicle",
            ),
        ).isEqualTo("display")
    }
}
