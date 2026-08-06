package com.android.car.settings.feature.privacy.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.privacy.domain.PrivacyPermissionType
import com.android.car.settings.feature.privacy.domain.PrivacyState
import com.android.car.settings.feature.privacy.domain.PrivacyUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivacyUiState(
    val privacy: PrivacyState = PrivacyState(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val useCases: PrivacyUseCases,
) : ViewModel() {
    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PrivacyUiState> =
        combine(useCases.observeState(), working, message, ::PrivacyUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyUiState())

    init {
        execute { useCases.refresh() }
    }

    fun setMicrophoneAccessEnabled(enabled: Boolean) = execute {
        useCases.setMicrophoneAccessEnabled(enabled)
    }

    fun setCameraAccessEnabled(enabled: Boolean) = execute {
        useCases.setCameraAccessEnabled(enabled)
    }

    fun setLocationEnabled(enabled: Boolean) = execute { useCases.setLocationEnabled(enabled) }

    fun selectPermissionType(type: PrivacyPermissionType) = execute {
        useCases.selectPermissionType(type)
    }

    fun setAppPermission(packageName: String, type: PrivacyPermissionType, granted: Boolean) = execute {
        useCases.setAppPermission(packageName, type, granted)
    }

    fun clearMessage() {
        message.value = null
    }

    private fun execute(block: suspend () -> ActionResult) {
        viewModelScope.launch {
            working.value = true
            when (val result = block()) {
                ActionResult.Success -> Unit
                is ActionResult.Failure -> message.value = result.message
            }
            working.value = false
        }
    }
}
