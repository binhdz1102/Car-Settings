package com.android.car.settings.feature.applications.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.applications.domain.ApplicationDetails
import com.android.car.settings.feature.applications.domain.ApplicationsState
import com.android.car.settings.feature.applications.domain.ApplicationsUseCases
import com.android.car.settings.feature.applications.domain.SpecialAccessState
import com.android.car.settings.feature.applications.domain.SpecialAccessType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApplicationsUiState(
    val applications: ApplicationsState = ApplicationsState(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ApplicationsViewModel
    @Inject
    constructor(
        private val useCases: ApplicationsUseCases,
    ) : ViewModel() {
        private val working = MutableStateFlow(false)
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<ApplicationsUiState> =
            combine(useCases.observeState(), working, message, ::ApplicationsUiState)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = ApplicationsUiState(),
                )

        init {
            refresh()
        }

        fun refresh() = execute(showProgress = false) { useCases.refresh() }

        fun onForeground() = refresh()

        fun setShowSystemApps(show: Boolean) = execute { useCases.setShowSystemApps(show) }

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

data class ApplicationDetailsUiState(
    val details: ApplicationDetails? = null,
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ApplicationDetailsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val useCases: ApplicationsUseCases,
    ) : ViewModel() {
        private val packageName: String =
            checkNotNull(savedStateHandle[APPLICATION_PACKAGE_ARGUMENT]) {
                "An application package name is required"
            }
        private val working = MutableStateFlow(false)
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<ApplicationDetailsUiState> =
            combine(useCases.observeDetails(), working, message, ::ApplicationDetailsUiState)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = ApplicationDetailsUiState(),
                )

        init {
            select()
        }

        fun select() = execute(showProgress = false) { useCases.selectApplication(packageName) }

        fun onForeground() = select()

        fun setNotificationsEnabled(enabled: Boolean) =
            execute { useCases.setNotificationsEnabled(enabled) }

        fun setUnusedAppOptimizationEnabled(enabled: Boolean) =
            execute { useCases.setUnusedAppOptimizationEnabled(enabled) }

        fun forceStop() = execute { useCases.forceStop() }

        fun performPrimaryAction() = execute { useCases.performPrimaryAction() }

        fun clearStorage() = execute { useCases.clearStorage() }

        fun clearCache() = execute { useCases.clearCache() }

        fun setPrioritizePerformanceEnabled(enabled: Boolean) =
            execute { useCases.setPrioritizePerformanceEnabled(enabled) }

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

data class SpecialAccessUiState(
    val specialAccess: SpecialAccessState = SpecialAccessState(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SpecialAccessViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val useCases: ApplicationsUseCases,
    ) : ViewModel() {
        private val type =
            checkNotNull(savedStateHandle[SPECIAL_ACCESS_TYPE_ARGUMENT] as String?)
                .let(SpecialAccessType::valueOf)
        private val working = MutableStateFlow(false)
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<SpecialAccessUiState> =
            combine(useCases.observeSpecialAccess(), working, message, ::SpecialAccessUiState)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = SpecialAccessUiState(),
                )

        init {
            refresh()
        }

        fun refresh() = execute(showProgress = false) { useCases.selectSpecialAccess(type) }

        fun onForeground() = refresh()

        fun setShowSystemApps(show: Boolean) =
            execute { useCases.setSpecialAccessShowSystemApps(show) }

        fun setAllowed(packageName: String, allowed: Boolean) =
            execute { useCases.setSpecialAccessAllowed(packageName, allowed) }

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
