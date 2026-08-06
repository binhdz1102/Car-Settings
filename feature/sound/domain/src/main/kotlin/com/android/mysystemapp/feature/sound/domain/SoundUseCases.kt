package com.android.car.settings.feature.sound.domain

import javax.inject.Inject

class SoundUseCases
    @Inject
    constructor(
        private val repository: SoundRepository,
    ) {
        fun observeState() = repository.state

        suspend fun refresh() = repository.refresh()

        suspend fun setVolume(groupId: Int, value: Int) = repository.setVolume(groupId, value)

        suspend fun setRingerMode(mode: RingerMode) = repository.setRingerMode(mode)

        suspend fun setVibrateWhenRinging(enabled: Boolean) = repository.setVibrateWhenRinging(enabled)

        suspend fun setInterruptionMode(mode: InterruptionMode) = repository.setInterruptionMode(mode)

        suspend fun ringtoneOptions(kind: RingtoneKind) = repository.ringtoneOptions(kind)

        suspend fun previewRingtone(uri: String?) = repository.previewRingtone(uri)

        suspend fun setRingtone(kind: RingtoneKind, uri: String?) = repository.setRingtone(kind, uri)
    }
