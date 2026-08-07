package com.android.car.settings.feature.hvac.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.hvac.domain.ClimateState
import com.android.car.settings.feature.hvac.domain.HvacUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HvacUiState(
    val climate: ClimateState = ClimateState(),
    val isRefreshing: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class HvacViewModel @Inject constructor(
    private val useCases: HvacUseCases,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HvacUiState> = combine(
        useCases.observeState(),
        refreshing,
        message,
        ::HvacUiState,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HvacUiState(),
    )

    init {
        refresh()
    }

    fun refresh() = execute(showProgress = true) { useCases.refresh() }

    fun setBoolean(key: String, value: Boolean) = execute(showProgress = false) {
        useCases.setBoolean(key, value)
    }

    fun setInt(key: String, value: Int) = execute(showProgress = false) {
        useCases.setInt(key, value)
    }

    fun setFloat(key: String, value: Float) = execute(showProgress = false) {
        useCases.setFloat(key, value)
    }

    fun clearMessage() {
        message.value = null
    }

    private fun execute(showProgress: Boolean, block: suspend () -> ActionResult) {
        viewModelScope.launch {
            if (showProgress) refreshing.value = true
            when (val result = block()) {
                ActionResult.Success -> Unit
                is ActionResult.Failure -> message.value = result.message
            }
            if (showProgress) refreshing.value = false
        }
    }
}
