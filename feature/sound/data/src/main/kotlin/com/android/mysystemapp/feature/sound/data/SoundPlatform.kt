package com.android.car.settings.feature.sound.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.sound.domain.InterruptionMode
import com.android.car.settings.feature.sound.domain.RingerMode
import com.android.car.settings.feature.sound.domain.RingtoneKind
import com.android.car.settings.feature.sound.domain.RingtoneOption
import com.android.car.settings.feature.sound.domain.SoundState
import kotlinx.coroutines.flow.StateFlow

internal interface SoundPlatform {
    val state: StateFlow<SoundState>

    suspend fun refresh(): ActionResult

    suspend fun setVolume(groupId: Int, value: Int): ActionResult

    suspend fun setRingerMode(mode: RingerMode): ActionResult

    suspend fun setVibrateWhenRinging(enabled: Boolean): ActionResult

    suspend fun setInterruptionMode(mode: InterruptionMode): ActionResult

    suspend fun ringtoneOptions(kind: RingtoneKind): List<RingtoneOption>

    suspend fun previewRingtone(uri: String?): ActionResult

    suspend fun setRingtone(kind: RingtoneKind, uri: String?): ActionResult
}
