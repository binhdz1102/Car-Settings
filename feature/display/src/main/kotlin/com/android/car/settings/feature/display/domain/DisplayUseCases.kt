package com.android.car.settings.feature.display.domain

import javax.inject.Inject

class DisplayUseCases
    @Inject
    constructor(
        private val repository: DisplayRepository,
    ) {
        fun observeState() = repository.state

        suspend fun refresh() = repository.refresh()

        suspend fun setBrightness(gamma: Int) = repository.setBrightness(gamma)

        suspend fun setAdaptiveBrightness(enabled: Boolean) = repository.setAdaptiveBrightness(enabled)

        suspend fun setThemeMode(mode: ThemeMode) = repository.setThemeMode(mode)

        suspend fun setAutoTime(enabled: Boolean) = repository.setAutoTime(enabled)

        suspend fun setAutoTimeZone(enabled: Boolean) = repository.setAutoTimeZone(enabled)

        suspend fun setUse24HourFormat(enabled: Boolean) = repository.setUse24HourFormat(enabled)

        suspend fun setManualTime(epochMillis: Long) = repository.setManualTime(epochMillis)

        suspend fun setManualTimeZone(timeZoneId: String) = repository.setManualTimeZone(timeZoneId)
    }
