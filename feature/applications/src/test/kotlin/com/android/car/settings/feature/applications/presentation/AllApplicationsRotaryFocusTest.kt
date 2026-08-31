package com.android.car.settings.feature.applications.presentation

import com.android.car.settings.feature.applications.domain.ApplicationSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class AllApplicationsRotaryFocusTest {
    @Test
    fun applicationsRootFocusIndex_keepsLateRecentAppsBetweenAllAppsAndAppSettings() {
        val indexById =
            applicationsRootLazyFocusIndexById(
                listOf(
                    ApplicationSummary(packageName = "com.android.car.settings", label = "Car Settings"),
                    ApplicationSummary(packageName = "com.example.mysystemapp", label = "MySystemApp"),
                ),
            )

        assertEquals(
            linkedMapOf(
                "applications-all-apps" to 1,
                "application-com.android.car.settings" to 3,
                "application-com.example.mysystemapp" to 4,
                "applications-app-permissions" to 6,
                "applications-default-apps" to 7,
                "applications-unused-apps" to 8,
                "applications-performance-apps" to 9,
                "applications-special-access" to 10,
            ),
            indexById,
        )
    }

    @Test
    fun applicationsRootFocusIndex_compactsAppSettingsWhenThereAreNoRecentApps() {
        val indexById = applicationsRootLazyFocusIndexById(emptyList())

        assertEquals(
            linkedMapOf(
                "applications-all-apps" to 1,
                "applications-app-permissions" to 3,
                "applications-default-apps" to 4,
                "applications-unused-apps" to 5,
                "applications-performance-apps" to 6,
                "applications-special-access" to 7,
            ),
            indexById,
        )
    }

    @Test
    fun allAppsFocusIndex_keepsEveryApplicationAfterThePersistentSystemAppsToggle() {
        val indexById =
            allApplicationsLazyFocusIndexById(
                listOf(
                    ApplicationSummary(packageName = "com.example.alpha", label = "Alpha"),
                    ApplicationSummary(packageName = "com.example.location", label = "Location"),
                    ApplicationSummary(packageName = "com.example.privacy", label = "Privacy"),
                ),
            )

        assertEquals(
            linkedMapOf(
                "all-apps-show-system" to 0,
                "application-com.example.alpha" to 1,
                "application-com.example.location" to 2,
                "application-com.example.privacy" to 3,
            ),
            indexById,
        )
    }
}
