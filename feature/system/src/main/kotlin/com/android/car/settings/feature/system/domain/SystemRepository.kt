package com.android.car.settings.feature.system.domain

import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.flow.Flow

interface SystemRepository {
    val state: Flow<SystemSettingsState>

    suspend fun refresh(): ActionResult

    suspend fun tapBuildNumber(): ActionResult

    suspend fun setSystemLocale(languageTag: String): ActionResult

    suspend fun setAutofillService(componentName: String?): ActionResult

    suspend fun setKeyboardEnabled(
        id: String,
        enabled: Boolean,
    ): ActionResult

    suspend fun setTextToSpeechEngine(packageName: String): ActionResult

    suspend fun setTextToSpeechPlayback(
        speechRate: Int,
        pitch: Int,
    ): ActionResult

    suspend fun speakTextToSpeechSample(): ActionResult

    suspend fun setVehicleUnit(
        propertyId: Int,
        unitId: Int,
    ): ActionResult

    suspend fun launchExternal(actionId: SystemExternalActionId): ActionResult

    suspend fun restartSystem(): ActionResult

    suspend fun resetNetwork(
        subscriptionId: Int?,
        eraseEsim: Boolean,
    ): ActionResult

    suspend fun resetAppPreferences(): ActionResult

    suspend fun factoryReset(eraseEsim: Boolean): ActionResult
}
