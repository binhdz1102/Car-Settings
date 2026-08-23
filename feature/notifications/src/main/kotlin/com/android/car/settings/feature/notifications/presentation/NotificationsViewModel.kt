package com.android.car.settings.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.ActiveOperationTracker
import com.android.car.settings.feature.notifications.domain.NotificationsState
import com.android.car.settings.feature.notifications.domain.NotificationsUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val notifications: NotificationsState = NotificationsState(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class NotificationsViewModel
    @Inject
    constructor(
        private val useCases: NotificationsUseCases,
    ) : ViewModel() {
        private val working = ActiveOperationTracker()
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<NotificationsUiState> =
            combine(useCases.observeState(), working.isActive, message, ::NotificationsUiState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

        init {
            execute { useCases.refresh() }
        }

        fun refresh() = execute(showProgress = false) { useCases.refresh() }

        fun selectApp(packageName: String) = execute { useCases.selectApp(packageName) }

        fun setNotificationsEnabled(
            packageName: String,
            enabled: Boolean,
        ) = execute { useCases.setNotificationsEnabled(packageName, enabled) }

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
