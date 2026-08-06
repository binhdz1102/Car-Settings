package com.android.car.settings.navigation

import com.android.car.settings.feature.applications.presentation.APPLICATIONS_ROUTE
import com.android.car.settings.feature.applications.presentation.applicationDetailsRoute
import com.android.car.settings.feature.display.presentation.DISPLAY_ROUTE
import com.android.car.settings.feature.notifications.presentation.NOTIFICATIONS_ROUTE
import com.android.car.settings.feature.sound.presentation.ringtoneRoute
import com.android.car.settings.feature.sound.domain.RingtoneKind
import com.android.car.settings.feature.wifi.presentation.WIFI_ROUTE
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsIntentRouterTest {
    @Test
    fun settingsActionOpensHome() {
        assertEquals(
            SettingsIntentRouter.HOME_ROUTE,
            SettingsIntentRouter.destinationFor(action = "android.settings.SETTINGS"),
        )
    }

    @Test
    fun wifiActionOpensWifi() {
        assertEquals(
            WIFI_ROUTE,
            SettingsIntentRouter.destinationFor(action = "android.settings.WIFI_SETTINGS"),
        )
    }

    @Test
    fun aospInternetAndAddNetworkActionsOpenWifi() {
        assertEquals(
            WIFI_ROUTE,
            SettingsIntentRouter.destinationFor(
                action = "android.settings.panel.action.INTERNET_CONNECTIVITY",
            ),
        )
        assertEquals(
            WIFI_ROUTE,
            SettingsIntentRouter.destinationFor(action = "android.settings.WIFI_ADD_NETWORKS"),
        )
    }

    @Test
    fun aospDndActionOpensNotifications() {
        assertEquals(
            NOTIFICATIONS_ROUTE,
            SettingsIntentRouter.destinationFor(
                action = "android.settings.VOICE_CONTROL_DO_NOT_DISTURB_MODE",
            ),
        )
    }

    @Test
    fun applicationDetailsDataOpensSelectedPackage() {
        assertEquals(
            applicationDetailsRoute("com.example.vehicle"),
            SettingsIntentRouter.destinationFor(
                action = "android.settings.APPLICATION_DETAILS_SETTINGS",
                dataPackage = "com.example.vehicle",
            ),
        )
    }

    @Test
    fun legacyAospComponentIsMappedToComposeRoute() {
        assertEquals(
            DISPLAY_ROUTE,
            SettingsIntentRouter.destinationFor(
                action = null,
                componentClassName =
                    "com.android.car.settings.common.CarSettingActivities\$DisplaySettingsActivity",
            ),
        )
    }

    @Test
    fun ringtoneTypeIsPreserved() {
        assertEquals(
            ringtoneRoute(RingtoneKind.ALARM),
            SettingsIntentRouter.destinationFor(
                action = "android.intent.action.RINGTONE_PICKER",
                ringtoneType = 4,
            ),
        )
    }

    @Test
    fun applicationActionWithoutPackageFallsBackToApplications() {
        assertEquals(
            APPLICATIONS_ROUTE,
            SettingsIntentRouter.destinationFor(
                action = "android.settings.APPLICATION_DETAILS_SETTINGS",
            ),
        )
    }
}
