package com.android.car.settings.feature.sound.domain

import com.android.car.settings.core.common.ActionResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SoundUseCasesTest {
    @Test
    fun `use cases delegate volume ringtone and interruption requests`() =
        runTest {
            val repository = RecordingSoundRepository()
            val useCases = SoundUseCases(repository)

            assertThat(useCases.setVolume(2, 7)).isEqualTo(ActionResult.Success)
            assertThat(useCases.setInterruptionMode(InterruptionMode.ALARMS)).isEqualTo(ActionResult.Success)
            assertThat(useCases.setRingtone(RingtoneKind.ALARM, "content://alarm")).isEqualTo(ActionResult.Success)

            assertThat(repository.volume).isEqualTo(2 to 7)
            assertThat(repository.interruptionMode).isEqualTo(InterruptionMode.ALARMS)
            assertThat(repository.ringtone).isEqualTo(RingtoneKind.ALARM to "content://alarm")
        }
}

private class RecordingSoundRepository : SoundRepository {
    override val state = MutableStateFlow(SoundState())
    var volume: Pair<Int, Int>? = null
    var interruptionMode: InterruptionMode? = null
    var ringtone: Pair<RingtoneKind, String?>? = null

    override suspend fun refresh() = ActionResult.Success

    override suspend fun setVolume(groupId: Int, value: Int): ActionResult {
        volume = groupId to value
        return ActionResult.Success
    }

    override suspend fun setRingerMode(mode: RingerMode) = ActionResult.Success

    override suspend fun setVibrateWhenRinging(enabled: Boolean) = ActionResult.Success

    override suspend fun setInterruptionMode(mode: InterruptionMode): ActionResult {
        interruptionMode = mode
        return ActionResult.Success
    }

    override suspend fun ringtoneOptions(kind: RingtoneKind) = emptyList<RingtoneOption>()

    override suspend fun previewRingtone(uri: String?) = ActionResult.Success

    override suspend fun setRingtone(kind: RingtoneKind, uri: String?): ActionResult {
        ringtone = kind to uri
        return ActionResult.Success
    }
}
