package com.android.car.settings.feature.sound.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundFocusEntrySpecTest {
    @Test
    fun arrivingVolumeData_replacesTheTemporaryDefaultWithTheFirstVolume() {
        val loadingSpec =
            soundRootFocusSpec(
                volumeFocusIds = emptyList(),
                isRingerModeSupported = false,
                isWorking = false,
                ringtoneFocusIds = emptyList(),
            )
        val loadedSpec =
            soundRootFocusSpec(
                volumeFocusIds = listOf("sound-volume-7"),
                isRingerModeSupported = false,
                isWorking = false,
                ringtoneFocusIds = emptyList(),
            )

        assertEquals("sound-do-not-disturb", loadingSpec.firstContentFocusId)
        assertEquals("sound-volume-7", loadedSpec.firstContentFocusId)
        assertEquals(
            mapOf(
                "sound-volume-7" to 1,
                "sound-do-not-disturb" to 3,
            ),
            loadedSpec.itemIndexByFocusId,
        )
    }

    @Test
    fun workingState_skipsUnavailableVibrateRowWithoutChangingTheRingerOrder() {
        val spec =
            soundRootFocusSpec(
                volumeFocusIds = emptyList(),
                isRingerModeSupported = true,
                isWorking = true,
                ringtoneFocusIds = emptyList(),
            )

        assertEquals("ringer-mode-SILENT", spec.firstContentFocusId)
        assertEquals(false, "sound-vibrate-calls" in spec.itemIndexByFocusId)
    }
}
