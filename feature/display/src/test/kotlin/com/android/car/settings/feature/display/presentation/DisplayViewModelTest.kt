package com.android.car.settings.feature.display.presentation

import app.cash.turbine.test
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.display.domain.DisplayRepository
import com.android.car.settings.feature.display.domain.DisplayState
import com.android.car.settings.feature.display.domain.DisplayUseCases
import com.android.car.settings.feature.display.domain.ThemeMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DisplayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ViewModelDisplayRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = ViewModelDisplayRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `display state is rendered and commands are forwarded`() =
        runTest(dispatcher) {
            val viewModel = DisplayViewModel(DisplayUseCases(repository))
            viewModel.uiState.test {
                awaitItem()
                repository.mutableState.value =
                    DisplayState(
                        brightnessGamma = 20_000,
                        adaptiveBrightnessAvailable = true,
                        themeModeAvailable = true,
                    )
                assertThat(awaitItem().display.brightnessGamma).isEqualTo(20_000)

                viewModel.setBrightness(33_000)
                viewModel.setAdaptiveBrightness(true)
                viewModel.setThemeMode(ThemeMode.NIGHT)
                advanceUntilIdle()

                assertThat(repository.brightness).isEqualTo(33_000)
                assertThat(repository.adaptiveBrightness).isTrue()
                assertThat(repository.themeMode).isEqualTo(ThemeMode.NIGHT)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private class ViewModelDisplayRepository : DisplayRepository {
    val mutableState = MutableStateFlow(DisplayState())
    override val state = mutableState
    var brightness: Int? = null
    var adaptiveBrightness: Boolean? = null
    var themeMode: ThemeMode? = null

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

    override suspend fun setAutoTime(enabled: Boolean) = ActionResult.Success

    override suspend fun setAutoTimeZone(enabled: Boolean) = ActionResult.Success

    override suspend fun setUse24HourFormat(enabled: Boolean) = ActionResult.Success

    override suspend fun setManualTime(epochMillis: Long) = ActionResult.Success

    override suspend fun setManualTimeZone(timeZoneId: String) = ActionResult.Success
}
