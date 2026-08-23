package com.android.car.settings.feature.system.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.system.domain.SystemExternalActionId
import com.android.car.settings.feature.system.domain.SystemRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SystemRepositoryImpl
    @Inject
    constructor(
        private val platform: SystemPlatform,
    ) : SystemRepository {
        override val state: Flow<com.android.car.settings.feature.system.domain.SystemSettingsState> = platform.state

        override suspend fun refresh(): ActionResult = platform.refresh()

        override suspend fun tapBuildNumber(): ActionResult = platform.tapBuildNumber()

        override suspend fun setSystemLocale(languageTag: String): ActionResult = platform.setSystemLocale(languageTag)

        override suspend fun setAutofillService(componentName: String?): ActionResult = platform.setAutofillService(componentName)

        override suspend fun setKeyboardEnabled(
            id: String,
            enabled: Boolean,
        ): ActionResult = platform.setKeyboardEnabled(id, enabled)

        override suspend fun setTextToSpeechEngine(packageName: String): ActionResult = platform.setTextToSpeechEngine(packageName)

        override suspend fun setTextToSpeechPlayback(
            speechRate: Int,
            pitch: Int,
        ): ActionResult = platform.setTextToSpeechPlayback(speechRate, pitch)

        override suspend fun speakTextToSpeechSample(): ActionResult = platform.speakTextToSpeechSample()

        override suspend fun setVehicleUnit(
            propertyId: Int,
            unitId: Int,
        ): ActionResult = platform.setVehicleUnit(propertyId, unitId)

        override suspend fun launchExternal(actionId: SystemExternalActionId): ActionResult = platform.launchExternal(actionId)

        override suspend fun restartSystem(): ActionResult = platform.restartSystem()

        override suspend fun resetNetwork(
            subscriptionId: Int?,
            eraseEsim: Boolean,
        ): ActionResult = platform.resetNetwork(subscriptionId, eraseEsim)

        override suspend fun resetAppPreferences(): ActionResult = platform.resetAppPreferences()

        override suspend fun factoryReset(eraseEsim: Boolean): ActionResult = platform.factoryReset(eraseEsim)
    }
