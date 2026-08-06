package com.android.car.settings.feature.display.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.display.domain.DisplayState
import com.android.car.settings.feature.display.domain.DisplayUseCases
import com.android.car.settings.feature.display.domain.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DisplayUiState(
    val display: DisplayState = DisplayState(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class DisplayViewModel
    @Inject
    constructor(
        private val useCases: DisplayUseCases,
    ) : ViewModel() {
        private val working = MutableStateFlow(false)
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<DisplayUiState> =
            combine(useCases.observeState(), working, message, ::DisplayUiState)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = DisplayUiState(),
                )

        init {
            execute(showProgress = false) { useCases.refresh() }
        }

        fun refresh() = execute(showProgress = false) { useCases.refresh() }

        /** Re-read framework state after returning from another Settings surface. */
        fun onForeground() = refresh()

        fun setBrightness(gamma: Int) = execute(showProgress = false) { useCases.setBrightness(gamma) }

        fun setAdaptiveBrightness(enabled: Boolean) = execute { useCases.setAdaptiveBrightness(enabled) }

        fun setThemeMode(mode: ThemeMode) = execute { useCases.setThemeMode(mode) }

        fun clearMessage() {
            message.value = null
        }

        private fun execute(
            showProgress: Boolean = true,
            block: suspend () -> ActionResult,
        ) {
            viewModelScope.launch {
                if (showProgress) working.value = true
                when (val result = block()) {
                    ActionResult.Success -> Unit
                    is ActionResult.Failure -> message.value = result.message
                }
                if (showProgress) working.value = false
            }
        }
    }
