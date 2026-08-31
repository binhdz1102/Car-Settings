package com.android.car.settings.feature.assistantvoice.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.ActiveOperationTracker
import com.android.car.settings.feature.assistantvoice.domain.AssistantVoiceState
import com.android.car.settings.feature.assistantvoice.domain.AssistantVoiceUseCases
import com.android.car.settings.feature.assistantvoice.domain.VoiceInputOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssistantVoiceUiState(
    val assistantVoice: AssistantVoiceState = AssistantVoiceState(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class AssistantVoiceViewModel
    @Inject
    constructor(
        private val useCases: AssistantVoiceUseCases,
    ) : ViewModel() {
        private val working = ActiveOperationTracker()
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<AssistantVoiceUiState> =
            combine(
                useCases.observeState(),
                working.isActive,
                message,
                ::AssistantVoiceUiState,
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantVoiceUiState())

        init {
            refresh()
        }

        fun refresh() = execute(showProgress = false) { useCases.refresh() }

        fun setTextFromScreenEnabled(enabled: Boolean) =
            execute {
                useCases.setTextFromScreenEnabled(enabled)
            }

        fun setScreenshotEnabled(enabled: Boolean) =
            execute {
                useCases.setScreenshotEnabled(enabled)
            }

        fun setDefaultVoiceInput(option: VoiceInputOption) =
            execute {
                useCases.setDefaultVoiceInput(option)
            }

        fun clearMessage() {
            message.value = null
        }

        private fun execute(
            showProgress: Boolean = true,
            block: suspend () -> ActionResult,
        ) {
            viewModelScope.launch {
                working.track(showProgress) {
                    when (val result = block()) {
                        ActionResult.Success -> Unit
                        is ActionResult.Failure -> message.value = result.message
                    }
                }
            }
        }
    }
