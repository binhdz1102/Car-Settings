package com.android.car.settings.feature.accessibility.data

import com.android.car.settings.feature.accessibility.domain.AccessibilityRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AccessibilityRepositoryImpl
    @Inject
    constructor(
        private val platform: AccessibilityPlatform,
    ) : AccessibilityRepository {
        override val state = platform.state

        override suspend fun refresh() = platform.refresh()

        override suspend fun setCaptionsEnabled(enabled: Boolean) = platform.setCaptionsEnabled(enabled)

        override suspend fun setCaptionTextSize(size: com.android.car.settings.feature.accessibility.domain.CaptionTextSize) =
            platform.setCaptionTextSize(size)

        override suspend fun setCaptionTextStyle(style: com.android.car.settings.feature.accessibility.domain.CaptionTextStyle) =
            platform.setCaptionTextStyle(style)

        override suspend fun setServiceEnabled(
            componentName: String,
            enabled: Boolean,
        ) = platform.setServiceEnabled(componentName, enabled)
    }
