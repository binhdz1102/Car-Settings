package com.android.car.settings.feature.location.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.ActiveOperationTracker
import com.android.car.settings.feature.location.domain.LocationState
import com.android.car.settings.feature.location.domain.LocationUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationUiState(
    val location: LocationState = LocationState(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class LocationViewModel
    @Inject
    constructor(
        private val useCases: LocationUseCases,
    ) : ViewModel() {
        private val working = ActiveOperationTracker()
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<LocationUiState> =
            combine(
                useCases.observeState(),
                working.isActive,
                message,
                ::LocationUiState,
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationUiState())

        init {
            refresh()
        }

        fun refresh() = execute(showProgress = false) { useCases.refresh() }

        fun setLocationEnabled(enabled: Boolean) =
            execute {
                useCases.setLocationEnabled(enabled)
            }

        fun setAdasLocationEnabled(enabled: Boolean) =
            execute {
                useCases.setAdasLocationEnabled(enabled)
            }

        fun setAppPermission(
            packageName: String,
            granted: Boolean,
        ) = execute {
            useCases.setAppPermission(packageName, granted)
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
