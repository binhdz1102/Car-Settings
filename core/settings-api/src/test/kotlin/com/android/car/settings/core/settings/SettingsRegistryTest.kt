package com.android.car.settings.core.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsRegistryTest {
    @Test
    fun routesAndFeatureRootsAreUniqueAndComplete() {
        val routes = DefaultSettingsRegistry.destinations.mapNotNull { it.route }

        assertThat(routes).containsNoDuplicates()
        assertThat(DefaultSettingsRegistry.destinations.map { it.id }).containsNoDuplicates()
        assertThat(DefaultSettingsRegistry.features.map { it.id }).containsNoDuplicates()
        assertThat(DefaultSettingsRegistry.features.map { it.rootDestination })
            .doesNotContain(SettingsDestinationId.HOME)
    }

    @Test
    fun deepLinksResolveEveryRegisteredRouteNotOnlyVehicleRoutes() {
        assertThat(DefaultSettingsRegistry.destinationForRoute("system/reset")?.id)
            .isEqualTo(SettingsDestinationId.RESET_OPTIONS)
        assertThat(DefaultSettingsRegistry.destinationForRoute("wifi")?.id)
            .isEqualTo(SettingsDestinationId.WIFI)
        assertThat(DefaultSettingsRegistry.destinationForRoute("vehicle/seat-control")?.id)
            .isEqualTo(SettingsDestinationId.SEAT_CONTROL)
        assertThat(DefaultSettingsRegistry.destinationForRoute("security/lock/PIN")?.id)
            .isEqualTo(SettingsDestinationId.SCREEN_LOCK_SETUP)
    }

    @Test
    fun depth2RoutesResolveToCorrectedCategory() {
        // bluetooth/details — previously unregistered, caused rail to snap to CONNECTED_DEVICES
        // of the *launch* intent rather than the current session.
        assertThat(DefaultSettingsRegistry.destinationForRoute("bluetooth/details")?.id)
            .isEqualTo(SettingsDestinationId.BLUETOOTH_DEVICE_DETAILS)
        assertThat(DefaultSettingsRegistry.categoryForRoute("bluetooth/details")?.id)
            .isEqualTo(SettingsCategoryId.CONNECTED_DEVICES)

        // wifi/details
        assertThat(DefaultSettingsRegistry.destinationForRoute("wifi/details")?.id)
            .isEqualTo(SettingsDestinationId.WIFI_NETWORK_DETAILS)
        assertThat(DefaultSettingsRegistry.categoryForRoute("wifi/details")?.id)
            .isEqualTo(SettingsCategoryId.NETWORK_INTERNET)

        // notifications/app/{packageName} — parameterised route
        assertThat(DefaultSettingsRegistry.destinationForRoute("notifications/app/com.example.app")?.id)
            .isEqualTo(SettingsDestinationId.NOTIFICATION_APP_DETAILS)
        assertThat(DefaultSettingsRegistry.categoryForRoute("notifications/app/com.example.app")?.id)
            .isEqualTo(SettingsCategoryId.NOTIFICATIONS)

        // sound/ringtone/{kind} parameterised and concrete routes
        assertThat(DefaultSettingsRegistry.categoryForRoute("sound/ringtone/{kind}")?.id)
            .isEqualTo(SettingsCategoryId.SOUND)
        assertThat(DefaultSettingsRegistry.categoryForRoute("sound/ringtone/PHONE")?.id)
            .isEqualTo(SettingsCategoryId.SOUND)

        // profile-accounts parameterised and concrete routes
        assertThat(DefaultSettingsRegistry.categoryForRoute("profile-accounts/profiles/{userId}")?.id)
            .isEqualTo(SettingsCategoryId.PROFILE)
        assertThat(DefaultSettingsRegistry.categoryForRoute("profile-accounts/profiles/10")?.id)
            .isEqualTo(SettingsCategoryId.PROFILE)

        // applications special access parameterised routes
        assertThat(DefaultSettingsRegistry.categoryForRoute("applications/special-access/{accessType}")?.id)
            .isEqualTo(SettingsCategoryId.APPS)

        // display depth 2 & 3 routes
        assertThat(DefaultSettingsRegistry.categoryForRoute("display/date-time")?.id)
            .isEqualTo(SettingsCategoryId.DISPLAY)
        assertThat(DefaultSettingsRegistry.categoryForRoute("display/date-time/time-zone")?.id)
            .isEqualTo(SettingsCategoryId.DISPLAY)

        // system depth 2 routes
        assertThat(DefaultSettingsRegistry.categoryForRoute("system/about")?.id)
            .isEqualTo(SettingsCategoryId.SYSTEM)
        assertThat(DefaultSettingsRegistry.categoryForRoute("system/language-input")?.id)
            .isEqualTo(SettingsCategoryId.SYSTEM)

        // vehicle depth 2 routes
        assertThat(DefaultSettingsRegistry.categoryForRoute("hvac")?.id)
            .isEqualTo(SettingsCategoryId.VEHICLE)
        assertThat(DefaultSettingsRegistry.categoryForRoute("vehicle/driver-assistance")?.id)
            .isEqualTo(SettingsCategoryId.VEHICLE)
        assertThat(DefaultSettingsRegistry.categoryForRoute("vehicle/seat-control")?.id)
            .isEqualTo(SettingsCategoryId.VEHICLE)
        assertThat(DefaultSettingsRegistry.categoryForRoute("vehicle/door-control")?.id)
            .isEqualTo(SettingsCategoryId.VEHICLE)
        assertThat(DefaultSettingsRegistry.categoryForRoute("vehicle/lighting")?.id)
            .isEqualTo(SettingsCategoryId.VEHICLE)
    }

    @Test
    fun parkedOnlyPolicyIsFailClosed() {
        assertThat(UxSafetyClass.PARKED_ONLY.isAllowedWhileRestricted()).isFalse()
        assertThat(UxSafetyClass.GLANCEABLE.isAllowedWhileRestricted()).isTrue()
        assertThat(UxSafetyClass.ALWAYS_SAFE.isAllowedWhileRestricted()).isTrue()
    }

    @Test
    fun everyDestinationResolvesToOneRailCategoryAndRoot() {
        val categories = DefaultSettingsRegistry.categories.map { it.id }
        assertThat(categories).containsExactlyElementsIn(SettingsCategoryId.entries)
        DefaultSettingsRegistry.destinations.forEach { destination ->
            val category = DefaultSettingsRegistry.categoryForDestination(destination.id)
            assertThat(category.id).isEqualTo(destination.categoryId)
            assertThat(category.rootDestination).isIn(SettingsDestinationId.entries)
        }
    }

    @Test
    fun nestedVehicleRouteUsesVehicleRoot() {
        assertThat(DefaultSettingsRegistry.categoryForRoute("vehicle/seat-control")?.id)
            .isEqualTo(SettingsCategoryId.VEHICLE)
        assertThat(DefaultSettingsRegistry.categoryForRoute("vehicle/seat-control")?.rootDestination)
            .isEqualTo(SettingsDestinationId.VEHICLE)
    }

    @Test
    fun normalLaunchRootIsConnectedDevices() {
        assertThat(DefaultSettingsRegistry.category(SettingsCategoryId.CONNECTED_DEVICES).rootDestination)
            .isEqualTo(SettingsDestinationId.BLUETOOTH)
    }
}
