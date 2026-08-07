package com.android.car.settings.feature.profileaccounts.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.profileaccounts.domain.AccountDetails
import com.android.car.settings.feature.profileaccounts.domain.AddAccountResult
import com.android.car.settings.feature.profileaccounts.domain.ProfileAccountsState
import com.android.car.settings.feature.profileaccounts.domain.ProfileAccountsUseCases
import com.android.car.settings.feature.profileaccounts.domain.ProfileDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileAccountsUiState(
    val settings: ProfileAccountsState = ProfileAccountsState(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ProfileAccountsViewModel @Inject constructor(
    private val useCases: ProfileAccountsUseCases,
) : ViewModel() {
    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfileAccountsUiState> =
        combine(useCases.observeState(), working, message, ::ProfileAccountsUiState).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProfileAccountsUiState(),
        )

    init { refresh() }

    fun refresh() = execute(showProgress = false) { useCases.refresh() }
    fun onForeground() = refresh()
    fun setMasterSyncEnabled(enabled: Boolean) = execute { useCases.setMasterSyncEnabled(enabled) }
    fun clearMessage() { message.value = null }

    private fun execute(showProgress: Boolean = true, block: suspend () -> ActionResult) {
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

data class ProfileDetailsUiState(
    val details: ProfileDetails? = null,
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ProfileDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val useCases: ProfileAccountsUseCases,
) : ViewModel() {
    private val userId = checkNotNull(savedStateHandle[PROFILE_ID_ARGUMENT] as Int?)
    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfileDetailsUiState> =
        combine(useCases.observeSelectedProfile(), working, message, ::ProfileDetailsUiState).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProfileDetailsUiState(),
        )

    init { refresh() }
    fun refresh() = execute(showProgress = false) { useCases.selectProfile(userId) }
    fun rename(name: String) = execute { useCases.renameCurrentProfile(name) }
    fun switchProfile() = execute { useCases.switchProfile(userId) }
    fun removeProfile() = execute { useCases.removeProfile(userId) }
    fun clearMessage() { message.value = null }

    private fun execute(showProgress: Boolean = true, block: suspend () -> ActionResult) {
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

@HiltViewModel
class AddProfileViewModel @Inject constructor(
    private val useCases: ProfileAccountsUseCases,
) : ViewModel() {
    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    val uiState: StateFlow<ProfileAccountsUiState> =
        combine(useCases.observeState(), working, message, ::ProfileAccountsUiState).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProfileAccountsUiState(),
        )

    fun addProfile(name: String) = execute { useCases.addProfile(name) }
    fun refresh() = execute(showProgress = false) { useCases.refresh() }
    fun clearMessage() { message.value = null }

    private fun execute(showProgress: Boolean = true, block: suspend () -> ActionResult) {
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

data class AccountDetailsUiState(
    val details: AccountDetails? = null,
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class AccountDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val useCases: ProfileAccountsUseCases,
) : ViewModel() {
    private val name = checkNotNull(savedStateHandle[ACCOUNT_NAME_ARGUMENT] as String?)
    private val type = checkNotNull(savedStateHandle[ACCOUNT_TYPE_ARGUMENT] as String?)
    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    val uiState: StateFlow<AccountDetailsUiState> =
        combine(useCases.observeSelectedAccount(), working, message, ::AccountDetailsUiState).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AccountDetailsUiState(),
        )

    init { refresh() }
    fun refresh() = execute(showProgress = false) { useCases.selectAccount(name, type) }
    fun setSyncEnabled(authority: String, enabled: Boolean) =
        execute { useCases.setAccountSyncEnabled(authority, enabled) }
    fun requestSync(authority: String) = execute { useCases.requestAccountSync(authority) }
    fun removeAccount() = execute { useCases.removeSelectedAccount() }
    fun clearMessage() { message.value = null }

    private fun execute(showProgress: Boolean = true, block: suspend () -> ActionResult) {
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

@HiltViewModel
class AddAccountViewModel @Inject constructor(
    private val useCases: ProfileAccountsUseCases,
) : ViewModel() {
    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    val uiState: StateFlow<ProfileAccountsUiState> =
        combine(useCases.observeState(), working, message, ::ProfileAccountsUiState).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProfileAccountsUiState(),
        )

    fun refresh() = execute(showProgress = false) { useCases.refresh() }
    fun addAccount(type: String) {
        viewModelScope.launch {
            working.value = true
            when (val result = useCases.addAccount(type)) {
                AddAccountResult.Started -> message.value = "Opening account provider"
                is AddAccountResult.Failure -> message.value = result.message
                is AddAccountResult.NeedsActivity -> message.value = "Opening account provider"
            }
            working.value = false
        }
    }
    fun clearMessage() { message.value = null }

    private fun execute(showProgress: Boolean = true, block: suspend () -> ActionResult) {
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
