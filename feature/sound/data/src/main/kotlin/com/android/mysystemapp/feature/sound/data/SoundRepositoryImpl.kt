package com.android.car.settings.feature.sound.data

import com.android.car.settings.feature.sound.domain.InterruptionMode
import com.android.car.settings.feature.sound.domain.RingerMode
import com.android.car.settings.feature.sound.domain.RingtoneKind
import com.android.car.settings.feature.sound.domain.SoundRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SoundRepositoryImpl
    @Inject
    constructor(
        private val platform: SoundPlatform,
    ) : SoundRepository {
        override val state = platform.state

        override suspend fun refresh() = platform.refresh()

        override suspend fun setVolume(groupId: Int, value: Int) = platform.setVolume(groupId, value)

        override suspend fun setRingerMode(mode: RingerMode) = platform.setRingerMode(mode)

        override suspend fun setVibrateWhenRinging(enabled: Boolean) = platform.setVibrateWhenRinging(enabled)

        override suspend fun setInterruptionMode(mode: InterruptionMode) = platform.setInterruptionMode(mode)

        override suspend fun ringtoneOptions(kind: RingtoneKind) = platform.ringtoneOptions(kind)

        override suspend fun previewRingtone(uri: String?) = platform.previewRingtone(uri)

        override suspend fun setRingtone(kind: RingtoneKind, uri: String?) = platform.setRingtone(kind, uri)
    }
