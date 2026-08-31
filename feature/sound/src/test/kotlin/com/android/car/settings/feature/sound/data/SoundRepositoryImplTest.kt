package com.android.car.settings.feature.sound.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.sound.domain.RingtoneKind
import com.android.car.settings.feature.sound.domain.SoundState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SoundRepositoryImplTest {
    @Test
    fun `repository delegates framework actions and exposes framework state`() =
        runTest {
            val platform = RecordingSoundPlatform()
            val repository = SoundRepositoryImpl(platform)

            assertThat(repository.state).isSameInstanceAs(platform.state)
            assertThat(repository.setVolume(3, 3)).isEqualTo(ActionResult.Success)
            assertThat(repository.setRingtone(RingtoneKind.PHONE, null)).isEqualTo(ActionResult.Success)

            assertThat(platform.volume).isEqualTo(3 to 3)
            assertThat(platform.ringtone).isEqualTo(RingtoneKind.PHONE to null)
        }
}

private class RecordingSoundPlatform : SoundPlatform {
    override val state = MutableStateFlow(SoundState())
    var volume: Pair<Int, Int>? = null
    var ringtone: Pair<RingtoneKind, String?>? = null

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

    override suspend fun ringtoneOptions(kind: RingtoneKind) = emptyList<com.android.car.settings.feature.sound.domain.RingtoneOption>()

    override suspend fun previewRingtone(uri: String?) = ActionResult.Success

    override suspend fun setRingtone(
        kind: RingtoneKind,
        uri: String?,
    ): ActionResult {
        ringtone = kind to uri
        return ActionResult.Success
    }
}
