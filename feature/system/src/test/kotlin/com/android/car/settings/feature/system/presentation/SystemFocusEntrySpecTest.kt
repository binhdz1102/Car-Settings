package com.android.car.settings.feature.system.presentation

import com.android.car.settings.feature.system.domain.SystemExternalAction
import com.android.car.settings.feature.system.domain.SystemExternalActionId
import com.android.car.settings.feature.system.domain.SystemSettingsState
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemFocusEntrySpecTest {
    @Test
    fun loadedSystemUpdate_replacesLanguagesAsTheDefaultFocus() {
        val initial = systemRootFocusSpec(SystemSettingsState(), isWorking = false)
        val loaded =
            systemRootFocusSpec(
                SystemSettingsState(
                    systemUpdate =
                        SystemExternalAction(
                            id = SystemExternalActionId.SYSTEM_UPDATE,
                            title = "CarSettings",
                            packageName = "com.android.car.settings",
                            className = ".SystemUpdateActivity",
                        ),
                ),
                isWorking = false,
            )

        assertEquals("system-languages", initial.firstContentFocusId)
        assertEquals("system-update", loaded.firstContentFocusId)
        assertEquals(1, loaded.itemIndexByFocusId.getValue("system-update"))
        assertEquals(2, loaded.itemIndexByFocusId.getValue("system-languages"))
    }
}
