package com.android.car.settings.feature.sound.domain

import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.flow.Flow

interface SoundRepository {
    val state: Flow<SoundState>

    suspend fun refresh(): ActionResult

    suspend fun setVolume(groupId: Int, value: Int): ActionResult

    suspend fun setRingerMode(mode: RingerMode): ActionResult

    suspend fun setVibrateWhenRinging(enabled: Boolean): ActionResult

    suspend fun setInterruptionMode(mode: InterruptionMode): ActionResult

    suspend fun ringtoneOptions(kind: RingtoneKind): List<RingtoneOption>

    suspend fun previewRingtone(uri: String?): ActionResult

    suspend fun setRingtone(kind: RingtoneKind, uri: String?): ActionResult
}
