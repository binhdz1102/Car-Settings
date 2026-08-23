package com.android.car.settings.feature.sound.domain

import com.android.car.settings.core.common.ActionFailureCode
import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.flow.Flow

interface SoundRepository {
    val state: Flow<SoundState>

    suspend fun refresh(): ActionResult

    suspend fun setVolume(
        groupId: Int,
        value: Int,
    ): ActionResult

    /** Mutes or unmutes an AAOS volume group, or its AudioManager fallback stream. */
    suspend fun setVolumeMuted(
        groupId: Int,
        muted: Boolean,
    ): ActionResult =
        ActionResult.Failure(
            message = "Volume mute is not supported",
            code = ActionFailureCode.NOT_SUPPORTED,
        )

    suspend fun setRingerMode(mode: RingerMode): ActionResult

    suspend fun setVibrateWhenRinging(enabled: Boolean): ActionResult

    suspend fun setInterruptionMode(mode: InterruptionMode): ActionResult

    suspend fun ringtoneOptions(kind: RingtoneKind): List<RingtoneOption>

    suspend fun previewRingtone(uri: String?): ActionResult

    suspend fun setRingtone(
        kind: RingtoneKind,
        uri: String?,
    ): ActionResult
}
