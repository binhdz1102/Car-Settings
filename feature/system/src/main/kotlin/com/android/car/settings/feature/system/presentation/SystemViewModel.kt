package com.android.car.settings.feature.system.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.ActiveOperationTracker
import com.android.car.settings.feature.system.domain.SystemExternalActionId
import com.android.car.settings.feature.system.domain.SystemSettingsState
import com.android.car.settings.feature.system.domain.SystemUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SystemUiState(
    val settings: SystemSettingsState = SystemSettingsState(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SystemViewModel
    @Inject
    constructor(
        private val useCases: SystemUseCases,
    ) : ViewModel() {
        private val working = ActiveOperationTracker()
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<SystemUiState> =
            combine(useCases.observeState(), working.isActive, message) { settings, isWorking, error ->
                SystemUiState(settings, isWorking, error)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SystemUiState())

        init {
            refresh()
        }

        fun refresh() = execute(showProgress = false) { useCases.refresh() }

        fun onForeground() = refresh()

        fun tapBuildNumber() = execute(showProgress = false) { useCases.tapBuildNumber() }

        fun setSystemLocale(languageTag: String) = execute { useCases.setSystemLocale(languageTag) }

        fun setAutofillService(componentName: String?) = execute { useCases.setAutofillService(componentName) }

        fun setKeyboardEnabled(
            id: String,
            enabled: Boolean,
        ) = execute {
            useCases.setKeyboardEnabled(id, enabled)
        }

        fun setTextToSpeechEngine(packageName: String) =
            execute {
                useCases.setTextToSpeechEngine(packageName)
            }

        fun setTextToSpeechPlayback(
            speechRate: Int,
            pitch: Int,
        ) = execute {
            useCases.setTextToSpeechPlayback(speechRate, pitch)
        }

        fun speakTextToSpeechSample() = execute(showProgress = false) { useCases.speakTextToSpeechSample() }

        fun setVehicleUnit(
            propertyId: Int,
            unitId: Int,
        ) = execute { useCases.setVehicleUnit(propertyId, unitId) }

        fun launchExternal(actionId: SystemExternalActionId) = execute { useCases.launchExternal(actionId) }

        fun restartSystem() = execute { useCases.restartSystem() }

        fun resetNetwork(
            subscriptionId: Int?,
            eraseEsim: Boolean,
        ) = execute { useCases.resetNetwork(subscriptionId, eraseEsim) }

        fun resetAppPreferences() = execute { useCases.resetAppPreferences() }

        fun factoryReset(eraseEsim: Boolean) = execute { useCases.factoryReset(eraseEsim) }

        fun clearMessage() {
            message.value = null
        }

        private fun execute(
            showProgress: Boolean = true,
            operation: suspend () -> ActionResult,
        ) {
            viewModelScope.launch {
                working.track(showProgress) {
                    when (val result = operation()) {
                        ActionResult.Success -> Unit
                        is ActionResult.Failure -> message.value = result.message
                    }
                }
            }
        }
    }
