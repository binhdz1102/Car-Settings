package com.android.car.settings.feature.display.domain

import com.android.car.settings.core.common.ActionResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DisplayUseCasesTest {
    @Test
    fun `use cases delegate all display commands`() =
        runTest {
            val repository = RecordingDisplayRepository()
            val useCases = DisplayUseCases(repository)

            assertThat(useCases.refresh()).isEqualTo(ActionResult.Success)
            assertThat(useCases.setBrightness(12_345)).isEqualTo(ActionResult.Success)
            assertThat(useCases.setAdaptiveBrightness(true)).isEqualTo(ActionResult.Success)
            assertThat(useCases.setThemeMode(ThemeMode.NIGHT)).isEqualTo(ActionResult.Success)
            assertThat(useCases.setAutoTime(false)).isEqualTo(ActionResult.Success)
            assertThat(useCases.setAutoTimeZone(false)).isEqualTo(ActionResult.Success)
            assertThat(useCases.setUse24HourFormat(true)).isEqualTo(ActionResult.Success)
            assertThat(useCases.setManualTime(123L)).isEqualTo(ActionResult.Success)
            assertThat(useCases.setManualTimeZone("Asia/Bangkok")).isEqualTo(ActionResult.Success)

            assertThat(repository.brightness).isEqualTo(12_345)
            assertThat(repository.adaptiveBrightness).isTrue()
            assertThat(repository.themeMode).isEqualTo(ThemeMode.NIGHT)
            assertThat(repository.autoTime).isFalse()
            assertThat(repository.autoTimeZone).isFalse()
            assertThat(repository.use24HourFormat).isTrue()
            assertThat(repository.manualTime).isEqualTo(123L)
            assertThat(repository.manualTimeZone).isEqualTo("Asia/Bangkok")
        }
}

private class RecordingDisplayRepository : DisplayRepository {
    override val state = MutableStateFlow(DisplayState())
    var brightness: Int? = null
    var adaptiveBrightness: Boolean? = null
    var themeMode: ThemeMode? = null
    var autoTime: Boolean? = null
    var autoTimeZone: Boolean? = null
    var use24HourFormat: Boolean? = null
    var manualTime: Long? = null
    var manualTimeZone: String? = null

    override suspend fun refresh() = ActionResult.Success

    override suspend fun setBrightness(gamma: Int): ActionResult {
        brightness = gamma
        return ActionResult.Success
    }

    override suspend fun setAdaptiveBrightness(enabled: Boolean): ActionResult {
        adaptiveBrightness = enabled
        return ActionResult.Success
    }

    override suspend fun setThemeMode(mode: ThemeMode): ActionResult {
        themeMode = mode
        return ActionResult.Success
    }

    override suspend fun setAutoTime(enabled: Boolean): ActionResult {
        autoTime = enabled
        return ActionResult.Success
    }

    override suspend fun setAutoTimeZone(enabled: Boolean): ActionResult {
        autoTimeZone = enabled
        return ActionResult.Success
    }

    override suspend fun setUse24HourFormat(enabled: Boolean): ActionResult {
        use24HourFormat = enabled
        return ActionResult.Success
    }

    override suspend fun setManualTime(epochMillis: Long): ActionResult {
        manualTime = epochMillis
        return ActionResult.Success
    }

    override suspend fun setManualTimeZone(timeZoneId: String): ActionResult {
        manualTimeZone = timeZoneId
        return ActionResult.Success
    }
}
