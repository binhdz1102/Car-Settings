package com.android.car.settings.feature.sound.presentation

import app.cash.turbine.test
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.sound.domain.RingtoneKind
import com.android.car.settings.feature.sound.domain.RingtoneOption
import com.android.car.settings.feature.sound.domain.SoundRepository
import com.android.car.settings.feature.sound.domain.SoundState
import com.android.car.settings.feature.sound.domain.SoundUseCases
import com.android.car.settings.feature.sound.domain.SoundVolume
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SoundViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ViewModelSoundRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = ViewModelSoundRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sound state is rendered and volume action is forwarded`() =
        runTest(dispatcher) {
            val viewModel = SoundViewModel(SoundUseCases(repository))
            viewModel.uiState.test {
                awaitItem()
                repository.mutableState.value =
                    SoundState(
                        volumes = listOf(SoundVolume(0, "Media", 4, 0, 10)),
                    )
                assertThat(
                    awaitItem()
                        .sound.volumes
                        .single()
                        .current,
                ).isEqualTo(4)

                viewModel.setVolume(0, 6)
                advanceUntilIdle()
                assertThat(repository.volume).isEqualTo(0 to 6)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ringtone loading stays busy until every overlapping request completes`() =
        runTest(dispatcher) {
            val phoneOptions = CompletableDeferred<List<RingtoneOption>>()
            val alarmOptions = CompletableDeferred<List<RingtoneOption>>()
            repository.ringtoneOptionsBlock = { kind ->
                when (kind) {
                    RingtoneKind.PHONE -> phoneOptions.await()
                    RingtoneKind.ALARM -> alarmOptions.await()
                    RingtoneKind.NOTIFICATION -> emptyList()
                }
            }
            val viewModel = SoundViewModel(SoundUseCases(repository))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
            runCurrent()

            viewModel.loadRingtoneOptions(RingtoneKind.PHONE)
            viewModel.loadRingtoneOptions(RingtoneKind.ALARM)
            runCurrent()
            assertThat(viewModel.uiState.value.isWorking).isTrue()

            phoneOptions.complete(emptyList())
            runCurrent()
            assertThat(viewModel.uiState.value.isWorking).isTrue()

            alarmOptions.complete(emptyList())
            runCurrent()
            assertThat(viewModel.uiState.value.isWorking).isFalse()
        }

    @Test
    fun `ringtone loading failure clears busy state and exposes error`() =
        runTest(dispatcher) {
            repository.ringtoneOptionsBlock = { error("options failed") }
            val viewModel = SoundViewModel(SoundUseCases(repository))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
            runCurrent()

            viewModel.loadRingtoneOptions(RingtoneKind.PHONE)
            runCurrent()

            assertThat(viewModel.uiState.value.isWorking).isFalse()
            assertThat(viewModel.uiState.value.message).isEqualTo("options failed")
        }

    @Test
    fun `ringtone loading cancellation is not exposed as an error`() =
        runTest(dispatcher) {
            val cancellation = CancellationException("cancelled")
            repository.ringtoneOptionsBlock = { throw cancellation }
            val viewModel = SoundViewModel(SoundUseCases(repository))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
            runCurrent()

            viewModel.loadRingtoneOptions(RingtoneKind.PHONE)
            runCurrent()

            assertThat(viewModel.uiState.value.isWorking).isFalse()
            assertThat(viewModel.uiState.value.message).isNull()
        }
}

private class ViewModelSoundRepository : SoundRepository {
    val mutableState = MutableStateFlow(SoundState())
    override val state = mutableState
    var volume: Pair<Int, Int>? = null
    var ringtoneOptionsBlock: suspend (RingtoneKind) -> List<RingtoneOption> = { emptyList() }

    override suspend fun refresh() = ActionResult.Success

    override suspend fun setVolume(
        groupId: Int,
        value: Int,
    ): ActionResult {
        volume = groupId to value
        return ActionResult.Success
    }

    override suspend fun setRingerMode(mode: com.android.car.settings.feature.sound.domain.RingerMode) = ActionResult.Success

    override suspend fun setVibrateWhenRinging(enabled: Boolean) = ActionResult.Success

    override suspend fun setInterruptionMode(mode: com.android.car.settings.feature.sound.domain.InterruptionMode) = ActionResult.Success

    override suspend fun ringtoneOptions(kind: RingtoneKind) = ringtoneOptionsBlock(kind)

    override suspend fun previewRingtone(uri: String?) = ActionResult.Success

    override suspend fun setRingtone(
        kind: RingtoneKind,
        uri: String?,
    ) = ActionResult.Success
}
