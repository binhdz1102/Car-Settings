package com.android.car.settings.feature.system.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.system.domain.SystemExternalActionId
import com.android.car.settings.feature.system.domain.SystemSettingsState
import kotlinx.coroutines.flow.StateFlow

internal interface SystemPlatform {
    val state: StateFlow<SystemSettingsState>

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
