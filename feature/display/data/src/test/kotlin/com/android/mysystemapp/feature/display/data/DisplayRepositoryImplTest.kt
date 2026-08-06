package com.android.car.settings.feature.display.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.display.domain.DisplayState
import com.android.car.settings.feature.display.domain.ThemeMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DisplayRepositoryImplTest {
    @Test
    fun `repository delegates framework actions and exposes framework state`() =
        runTest {
            val platform = RecordingDisplayPlatform()
            val repository = DisplayRepositoryImpl(platform)

            assertThat(repository.state).isSameInstanceAs(platform.state)
            assertThat(repository.setBrightness(23_456)).isEqualTo(ActionResult.Success)
            assertThat(repository.setAdaptiveBrightness(true)).isEqualTo(ActionResult.Success)
            assertThat(repository.setThemeMode(ThemeMode.DAY)).isEqualTo(ActionResult.Success)
            assertThat(repository.setAutoTime(false)).isEqualTo(ActionResult.Success)
            assertThat(repository.setAutoTimeZone(false)).isEqualTo(ActionResult.Success)
            assertThat(repository.setUse24HourFormat(true)).isEqualTo(ActionResult.Success)
            assertThat(repository.setManualTime(456L)).isEqualTo(ActionResult.Success)
            assertThat(repository.setManualTimeZone("UTC")).isEqualTo(ActionResult.Success)

            assertThat(platform.brightness).isEqualTo(23_456)
            assertThat(platform.adaptiveBrightness).isTrue()
            assertThat(platform.themeMode).isEqualTo(ThemeMode.DAY)
            assertThat(platform.autoTime).isFalse()
            assertThat(platform.autoTimeZone).isFalse()
            assertThat(platform.use24HourFormat).isTrue()
            assertThat(platform.manualTime).isEqualTo(456L)
            assertThat(platform.manualTimeZone).isEqualTo("UTC")
        }
}

private class RecordingDisplayPlatform : DisplayPlatform {
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
