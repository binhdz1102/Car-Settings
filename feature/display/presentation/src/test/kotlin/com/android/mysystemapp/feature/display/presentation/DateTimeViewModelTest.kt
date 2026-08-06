package com.android.car.settings.feature.display.presentation

import app.cash.turbine.test
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.display.domain.DateTimeState
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
class DateTimeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: RecordingDateTimeRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = RecordingDateTimeRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `date time state is rendered and settings commands are forwarded`() =
        runTest(dispatcher) {
            val viewModel = DateTimeViewModel(DisplayUseCases(repository))
            viewModel.uiState.test {
                awaitItem()
                repository.mutableState.value =
                    DisplayState(
                        dateTime =
                            DateTimeState(
                                autoTimeEnabled = true,
                                timeZoneId = "Asia/Bangkok",
                            ),
                    )
                assertThat(awaitItem().dateTime.timeZoneId).isEqualTo("Asia/Bangkok")

                viewModel.setAutoTime(false)
                viewModel.setAutoTimeZone(false)
                viewModel.setUse24HourFormat(true)
                viewModel.setManualTime(123L)
                viewModel.setManualTimeZone("UTC")
                advanceUntilIdle()

                assertThat(repository.autoTime).isFalse()
                assertThat(repository.autoTimeZone).isFalse()
                assertThat(repository.use24HourFormat).isTrue()
                assertThat(repository.manualTime).isEqualTo(123L)
                assertThat(repository.manualTimeZone).isEqualTo("UTC")
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private class RecordingDateTimeRepository : DisplayRepository {
    val mutableState = MutableStateFlow(DisplayState())
    override val state = mutableState
    var autoTime: Boolean? = null
    var autoTimeZone: Boolean? = null
    var use24HourFormat: Boolean? = null
    var manualTime: Long? = null
    var manualTimeZone: String? = null

    override suspend fun refresh() = ActionResult.Success

    override suspend fun setBrightness(gamma: Int) = ActionResult.Success

    override suspend fun setAdaptiveBrightness(enabled: Boolean) = ActionResult.Success

    override suspend fun setThemeMode(mode: ThemeMode) = ActionResult.Success

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
