package com.android.car.settings.feature.display.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.ActiveOperationTracker
import com.android.car.settings.feature.display.domain.DateTimeState
import com.android.car.settings.feature.display.domain.DisplayUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DateTimeUiState(
    val dateTime: DateTimeState = DateTimeState(),
    val isWorking: Boolean = false,
    val message: String? = null,
    val savedTimeZoneId: String? = null,
)

@HiltViewModel
class DateTimeViewModel
    @Inject
    constructor(
        private val useCases: DisplayUseCases,
    ) : ViewModel() {
        private val working = ActiveOperationTracker()
        private val message = MutableStateFlow<String?>(null)
        private val savedTimeZoneId = MutableStateFlow<String?>(null)

        val uiState: StateFlow<DateTimeUiState> =
            combine(
                useCases.observeState().map { it.dateTime },
                working.isActive,
                message,
                savedTimeZoneId,
                ::DateTimeUiState,
            ).stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DateTimeUiState(),
            )

        init {
            refresh()
        }

        fun refresh() = execute(showProgress = false) { useCases.refresh() }

        fun onForeground() = refresh()

        fun setAutoTime(enabled: Boolean) = execute { useCases.setAutoTime(enabled) }

        fun setAutoTimeZone(enabled: Boolean) = execute { useCases.setAutoTimeZone(enabled) }

        fun setUse24HourFormat(enabled: Boolean) = execute { useCases.setUse24HourFormat(enabled) }

        fun setManualTime(epochMillis: Long) = execute { useCases.setManualTime(epochMillis) }

        fun setManualTimeZone(timeZoneId: String) =
            execute(onSuccess = { savedTimeZoneId.value = timeZoneId }) {
                useCases.setManualTimeZone(timeZoneId)
            }

        fun clearMessage() {
            message.value = null
        }

        fun clearSavedTimeZone() {
            savedTimeZoneId.value = null
        }

        private fun execute(
            showProgress: Boolean = true,
            onSuccess: () -> Unit = {},
            block: suspend () -> ActionResult,
        ) {
            viewModelScope.launch {
                working.track(showProgress) {
                    when (val result = block()) {
                        ActionResult.Success -> onSuccess()
                        is ActionResult.Failure -> message.value = result.message
                    }
                }
            }
        }
    }
