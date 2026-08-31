package com.android.car.settings.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsRotaryFocusPolicyTest {
    @Test
    fun rotaryFocusPresentation_usesOnlyTheSharedBorderForOrdinaryFocus() {
        assertEquals(RotaryFocusPresentation.None, rotaryFocusPresentation(isFocused = false))
        assertEquals(RotaryFocusPresentation.BorderOnly, rotaryFocusPresentation(isFocused = true))
    }

    @Test
    fun rootDestination_doesNotReplaceTheShellSearchFocusWithDetailContent() {
        val climate =
            RotaryFocusTarget(
                areaId = FocusAreaId("settings-detail-vehicle"),
                itemId = FocusItemId("climate"),
            )

        assertNull(
            settingsDestinationFallback(
                isRoot = true,
                contentFallback = climate,
                backFallback = null,
            ),
        )
    }

    @Test
    fun childDestination_usesItsContentThenBackFallback() {
        val firstRow =
            RotaryFocusTarget(
                areaId = FocusAreaId("settings-detail-all-apps"),
                itemId = FocusItemId("show-system-apps"),
            )
        val back =
            RotaryFocusTarget(
                areaId = FocusAreaId("settings-header-all-apps"),
                itemId = FocusItemId("back"),
            )

        assertEquals(
            firstRow,
            settingsDestinationFallback(
                isRoot = false,
                contentFallback = firstRow,
                backFallback = back,
            ),
        )
        assertEquals(
            back,
            settingsDestinationFallback(
                isRoot = false,
                contentFallback = null,
                backFallback = back,
            ),
        )
        assertNull(
            settingsDestinationFallback(
                isRoot = false,
                contentFallback = firstRow,
                backFallback = back,
                hasExplicitFocusHandoff = true,
            ),
        )
    }

    @Test
    fun lazyFocusSpec_keepsOffscreenRowsInLogicalOrderAndMapsTheirPhysicalIndexes() {
        val spec =
            settingsLazyFocusSpec(
                destinationKey = "all-apps",
                itemIndexByFocusId =
                    linkedMapOf(
                        "show-system-apps" to 0,
                        "application-com.example.alpha" to 1,
                        "application-com.example.privacy" to 2,
                    ),
            )

        assertEquals(
            listOf(
                FocusItemId("settings-item-2d16ab51"),
                FocusItemId("settings-item-9c0517bf"),
                FocusItemId("settings-item-d27c75a9"),
            ),
            spec.focusOrder,
        )
        assertEquals(2, spec.itemIndexById[FocusItemId("settings-item-d27c75a9")])
    }

    @Test
    fun railFocusIndexes_revealPrivacyAfterAnOffscreenAndDisabledCategory() {
        val indexes =
            settingsRailLazyFocusIndexById(
                listOf(
                    SettingsCategoryUiModel("VEHICLE", "Vehicle", Icons.Outlined.Settings),
                    SettingsCategoryUiModel("HIDDEN", "Hidden", Icons.Outlined.Settings, enabled = false),
                    SettingsCategoryUiModel("LOCATION", "Location", Icons.Outlined.Settings),
                    SettingsCategoryUiModel("PRIVACY", "Privacy", Icons.Outlined.Settings),
                ),
            )

        assertEquals(
            linkedMapOf(
                FocusItemId("settings-category-VEHICLE") to 0,
                FocusItemId("settings-category-LOCATION") to 2,
                FocusItemId("settings-category-PRIVACY") to 3,
            ),
            indexes,
        )
    }
}
