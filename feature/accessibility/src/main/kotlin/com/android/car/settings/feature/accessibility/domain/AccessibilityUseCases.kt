package com.android.car.settings.feature.accessibility.domain

import javax.inject.Inject

class AccessibilityUseCases
    @Inject
    constructor(
        private val repository: AccessibilityRepository,
    ) {
        fun observeState() = repository.state

        suspend fun refresh() = repository.refresh()

        suspend fun setCaptionsEnabled(enabled: Boolean) = repository.setCaptionsEnabled(enabled)

        suspend fun setCaptionTextSize(size: CaptionTextSize) = repository.setCaptionTextSize(size)

        suspend fun setCaptionTextStyle(style: CaptionTextStyle) = repository.setCaptionTextStyle(style)

        suspend fun setServiceEnabled(
            componentName: String,
            enabled: Boolean,
        ) = repository.setServiceEnabled(componentName, enabled)
    }
