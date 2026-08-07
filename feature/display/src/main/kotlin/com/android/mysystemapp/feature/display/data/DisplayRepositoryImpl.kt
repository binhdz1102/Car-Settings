package com.android.car.settings.feature.display.data

import com.android.car.settings.feature.display.domain.DisplayRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DisplayRepositoryImpl
    @Inject
    constructor(
        private val platform: DisplayPlatform,
    ) : DisplayRepository {
        override val state = platform.state

        override suspend fun refresh() = platform.refresh()

        override suspend fun setBrightness(gamma: Int) = platform.setBrightness(gamma)

        override suspend fun setAdaptiveBrightness(enabled: Boolean) =
            platform.setAdaptiveBrightness(enabled)

        override suspend fun setThemeMode(mode: com.android.car.settings.feature.display.domain.ThemeMode) =
            platform.setThemeMode(mode)

        override suspend fun setAutoTime(enabled: Boolean) = platform.setAutoTime(enabled)

        override suspend fun setAutoTimeZone(enabled: Boolean) = platform.setAutoTimeZone(enabled)

        override suspend fun setUse24HourFormat(enabled: Boolean) =
            platform.setUse24HourFormat(enabled)

        override suspend fun setManualTime(epochMillis: Long) = platform.setManualTime(epochMillis)

        override suspend fun setManualTimeZone(timeZoneId: String) =
            platform.setManualTimeZone(timeZoneId)
    }
