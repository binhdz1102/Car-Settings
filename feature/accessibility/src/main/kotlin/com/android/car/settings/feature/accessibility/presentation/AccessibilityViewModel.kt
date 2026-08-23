package com.android.car.settings.feature.accessibility.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.ActiveOperationTracker
import com.android.car.settings.feature.accessibility.domain.AccessibilityState
import com.android.car.settings.feature.accessibility.domain.AccessibilityUseCases
import com.android.car.settings.feature.accessibility.domain.CaptionTextSize
import com.android.car.settings.feature.accessibility.domain.CaptionTextStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccessibilityUiState(
    val accessibility: AccessibilityState = AccessibilityState(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class AccessibilityViewModel
    @Inject
    constructor(
        private val useCases: AccessibilityUseCases,
    ) : ViewModel() {
        private val working = ActiveOperationTracker()
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<AccessibilityUiState> =
            combine(
                useCases.observeState(),
                working.isActive,
                message,
                ::AccessibilityUiState,
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccessibilityUiState())

        init {
            refresh()
        }

        fun refresh() = execute(showProgress = false) { useCases.refresh() }

        fun setCaptionsEnabled(enabled: Boolean) =
            execute {
                useCases.setCaptionsEnabled(enabled)
            }

        fun setCaptionTextSize(size: CaptionTextSize) =
            execute {
                useCases.setCaptionTextSize(size)
            }

        fun setCaptionTextStyle(style: CaptionTextStyle) =
            execute {
                useCases.setCaptionTextStyle(style)
            }

        fun setServiceEnabled(
            componentName: String,
            enabled: Boolean,
        ) = execute {
            useCases.setServiceEnabled(componentName, enabled)
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
