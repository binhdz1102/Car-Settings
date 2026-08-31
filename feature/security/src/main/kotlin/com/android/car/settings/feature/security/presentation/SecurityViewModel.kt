package com.android.car.settings.feature.security.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.ActiveOperationTracker
import com.android.car.settings.feature.security.domain.SecurityLockType
import com.android.car.settings.feature.security.domain.SecurityState
import com.android.car.settings.feature.security.domain.SecurityUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SecurityUiState(
    val security: SecurityState = SecurityState(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SecurityViewModel
    @Inject
    constructor(
        private val useCases: SecurityUseCases,
    ) : ViewModel() {
        private val working = ActiveOperationTracker()
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<SecurityUiState> =
            combine(useCases.observeState(), working.isActive, message, ::SecurityUiState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecurityUiState())

        init {
            execute { useCases.refresh() }
        }

        fun setLock(
            type: SecurityLockType,
            currentCredential: String,
            newCredential: String,
        ) = execute {
            useCases.setLock(type, currentCredential, newCredential)
        }

        fun resetCredentials(currentCredential: String) = execute { useCases.resetCredentials(currentCredential) }

        fun removeDeviceAdmin(componentName: String) = execute { useCases.removeDeviceAdmin(componentName) }

        fun clearMessage() {
            message.value = null
        }

        private fun execute(block: suspend () -> ActionResult) {
            viewModelScope.launch {
                working.track {
                    when (val result = block()) {
                        ActionResult.Success -> Unit
                        is ActionResult.Failure -> message.value = result.message
                    }
                }
            }
        }
    }
