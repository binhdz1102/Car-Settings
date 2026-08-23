package com.android.car.settings.feature.system.domain

import javax.inject.Inject

class SystemUseCases
    @Inject
    constructor(
        private val repository: SystemRepository,
    ) {
        fun observeState() = repository.state

        suspend fun refresh() = repository.refresh()

        suspend fun tapBuildNumber() = repository.tapBuildNumber()

        suspend fun setSystemLocale(languageTag: String) = repository.setSystemLocale(languageTag)

        suspend fun setAutofillService(componentName: String?) = repository.setAutofillService(componentName)

        suspend fun setKeyboardEnabled(
            id: String,
            enabled: Boolean,
        ) = repository.setKeyboardEnabled(id, enabled)

        suspend fun setTextToSpeechEngine(packageName: String) = repository.setTextToSpeechEngine(packageName)

        suspend fun setTextToSpeechPlayback(
            speechRate: Int,
            pitch: Int,
        ) = repository.setTextToSpeechPlayback(speechRate, pitch)

        suspend fun speakTextToSpeechSample() = repository.speakTextToSpeechSample()

        suspend fun setVehicleUnit(
            propertyId: Int,
            unitId: Int,
        ) = repository.setVehicleUnit(propertyId, unitId)

        suspend fun launchExternal(actionId: SystemExternalActionId) = repository.launchExternal(actionId)

        suspend fun restartSystem() = repository.restartSystem()

        suspend fun resetNetwork(
            subscriptionId: Int?,
            eraseEsim: Boolean,
        ) = repository.resetNetwork(subscriptionId, eraseEsim)

        suspend fun resetAppPreferences() = repository.resetAppPreferences()

        suspend fun factoryReset(eraseEsim: Boolean) = repository.factoryReset(eraseEsim)
    }
