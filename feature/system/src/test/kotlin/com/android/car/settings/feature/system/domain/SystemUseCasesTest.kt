package com.android.car.settings.feature.system.domain

import com.android.car.settings.core.common.ActionResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SystemUseCasesTest {
    @Test
    fun `use cases delegate system commands`() =
        runTest {
            val repository = RecordingSystemRepository()
            val useCases = SystemUseCases(repository)

            assertThat(useCases.refresh()).isEqualTo(ActionResult.Success)
            assertThat(useCases.tapBuildNumber()).isEqualTo(ActionResult.Success)
            assertThat(useCases.setSystemLocale("vi-VN")).isEqualTo(ActionResult.Success)
            assertThat(useCases.setAutofillService("pkg/.Autofill")).isEqualTo(ActionResult.Success)
            assertThat(useCases.setKeyboardEnabled("pkg/.Keyboard", true)).isEqualTo(ActionResult.Success)
            assertThat(useCases.setTextToSpeechEngine("pkg.tts")).isEqualTo(ActionResult.Success)
            assertThat(useCases.setTextToSpeechPlayback(180, 120)).isEqualTo(ActionResult.Success)
            assertThat(useCases.speakTextToSpeechSample()).isEqualTo(ActionResult.Success)
            assertThat(useCases.setVehicleUnit(1, 2)).isEqualTo(ActionResult.Success)
            assertThat(useCases.launchExternal(SystemExternalActionId.TERMS)).isEqualTo(ActionResult.Success)
            assertThat(useCases.restartSystem()).isEqualTo(ActionResult.Success)
            assertThat(useCases.resetNetwork(12, true)).isEqualTo(ActionResult.Success)
            assertThat(useCases.resetAppPreferences()).isEqualTo(ActionResult.Success)
            assertThat(useCases.factoryReset(false)).isEqualTo(ActionResult.Success)

            assertThat(repository.localeTag).isEqualTo("vi-VN")
            assertThat(repository.autofillComponent).isEqualTo("pkg/.Autofill")
            assertThat(repository.keyboardEnabled).isEqualTo("pkg/.Keyboard" to true)
            assertThat(repository.ttsEngine).isEqualTo("pkg.tts")
            assertThat(repository.ttsPlayback).isEqualTo(180 to 120)
            assertThat(repository.ttsSampleCalled).isTrue()
            assertThat(repository.propertyId).isEqualTo(1)
            assertThat(repository.unitId).isEqualTo(2)
            assertThat(repository.externalAction).isEqualTo(SystemExternalActionId.TERMS)
            assertThat(repository.networkSubscriptionId).isEqualTo(12)
            assertThat(repository.networkEraseEsim).isTrue()
            assertThat(repository.factoryEraseEsim).isFalse()
            assertThat(repository.restartCalled).isTrue()
            assertThat(repository.appPreferencesCalled).isTrue()
        }
}

private class RecordingSystemRepository : SystemRepository {
    override val state = MutableStateFlow(SystemSettingsState())
    var localeTag: String? = null
    var autofillComponent: String? = null
    var keyboardEnabled: Pair<String, Boolean>? = null
    var ttsEngine: String? = null
    var ttsPlayback: Pair<Int, Int>? = null
    var ttsSampleCalled = false
    var propertyId: Int? = null
    var unitId: Int? = null
    var externalAction: SystemExternalActionId? = null
    var networkSubscriptionId: Int? = null
    var networkEraseEsim: Boolean? = null
    var factoryEraseEsim: Boolean? = null
    var restartCalled = false
    var appPreferencesCalled = false

    override suspend fun refresh() = ActionResult.Success

    override suspend fun tapBuildNumber() = ActionResult.Success

    override suspend fun setSystemLocale(languageTag: String): ActionResult {
        localeTag = languageTag
        return ActionResult.Success
    }

    override suspend fun setAutofillService(componentName: String?): ActionResult {
        autofillComponent = componentName
        return ActionResult.Success
    }

    override suspend fun setKeyboardEnabled(
        id: String,
        enabled: Boolean,
    ): ActionResult {
        keyboardEnabled = id to enabled
        return ActionResult.Success
    }

    override suspend fun setTextToSpeechEngine(packageName: String): ActionResult {
        ttsEngine = packageName
        return ActionResult.Success
    }

    override suspend fun setTextToSpeechPlayback(
        speechRate: Int,
        pitch: Int,
    ): ActionResult {
        ttsPlayback = speechRate to pitch
        return ActionResult.Success
    }

    override suspend fun speakTextToSpeechSample(): ActionResult {
        ttsSampleCalled = true
        return ActionResult.Success
    }

    override suspend fun setVehicleUnit(
        propertyId: Int,
        unitId: Int,
    ): ActionResult {
        this.propertyId = propertyId
        this.unitId = unitId
        return ActionResult.Success
    }

    override suspend fun launchExternal(actionId: SystemExternalActionId): ActionResult {
        externalAction = actionId
        return ActionResult.Success
    }

    override suspend fun restartSystem(): ActionResult {
        restartCalled = true
        return ActionResult.Success
    }

    override suspend fun resetNetwork(
        subscriptionId: Int?,
        eraseEsim: Boolean,
    ): ActionResult {
        networkSubscriptionId = subscriptionId
        networkEraseEsim = eraseEsim
        return ActionResult.Success
    }

    override suspend fun resetAppPreferences(): ActionResult {
        appPreferencesCalled = true
        return ActionResult.Success
    }

    override suspend fun factoryReset(eraseEsim: Boolean): ActionResult {
        factoryEraseEsim = eraseEsim
        return ActionResult.Success
    }
}
