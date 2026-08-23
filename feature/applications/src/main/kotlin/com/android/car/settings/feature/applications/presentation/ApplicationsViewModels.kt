package com.android.car.settings.feature.applications.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.ActiveOperationTracker
import com.android.car.settings.feature.applications.domain.AppPermissionState
import com.android.car.settings.feature.applications.domain.ApplicationDetails
import com.android.car.settings.feature.applications.domain.ApplicationsState
import com.android.car.settings.feature.applications.domain.ApplicationsUseCases
import com.android.car.settings.feature.applications.domain.DefaultAppRole
import com.android.car.settings.feature.applications.domain.OpeningLinkState
import com.android.car.settings.feature.applications.domain.PermissionGroupSummary
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
        private val working = ActiveOperationTracker()
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<ApplicationsUiState> =
            combine(useCases.observeState(), working.isActive, message, ::ApplicationsUiState)
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
                working.track(showProgress) {
                    when (val result = block()) {
                        ActionResult.Success -> Unit
                        is ActionResult.Failure -> message.value = result.message
                    }
                }
            }
        }
    }

data class PermissionGroupsUiState(
    val groups: List<PermissionGroupSummary> = emptyList(),
    val applications: List<com.android.car.settings.feature.applications.domain.ApplicationSummary> = emptyList(),
    val selectedGroup: String? = null,
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class PermissionGroupsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val useCases: ApplicationsUseCases,
    ) : ViewModel() {
        private val working = ActiveOperationTracker()
        private val selectedGroup = MutableStateFlow<String?>(null)
        private val initialGroup: String? = savedStateHandle[PERMISSION_GROUP_ARGUMENT]
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<PermissionGroupsUiState> =
            combine(
                useCases.observePermissionGroups(),
                useCases.observePermissionApps(),
                selectedGroup,
                working.isActive,
                message,
            ) { groups, applications, group, isWorking, error ->
                PermissionGroupsUiState(groups, applications, group, isWorking, error)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PermissionGroupsUiState(),
            )

        init {
            refresh()
            initialGroup?.let(::selectGroup)
        }

        fun refresh() =
            execute(showProgress = false) {
                useCases.refresh()
            }

        fun selectGroup(groupName: String) =
            execute {
                selectedGroup.value = groupName
                useCases.selectPermissionGroup(groupName)
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

data class ApplicationPermissionsUiState(
    val permissions: List<AppPermissionState> = emptyList(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ApplicationPermissionsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val useCases: ApplicationsUseCases,
    ) : ViewModel() {
        private val packageName: String = checkNotNull(savedStateHandle[APPLICATION_PACKAGE_ARGUMENT])
        private val working = ActiveOperationTracker()
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<ApplicationPermissionsUiState> =
            combine(useCases.observeSelectedAppPermissions(), working.isActive, message) { permissions, isWorking, error ->
                ApplicationPermissionsUiState(permissions, isWorking, error)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApplicationPermissionsUiState())

        init {
            refresh()
        }

        fun refresh() = execute(showProgress = false) { useCases.selectApplicationPermissions(packageName) }

        fun setPermission(
            permissionName: String,
            granted: Boolean,
        ) = execute {
            useCases.setPermission(packageName, permissionName, granted)
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

data class DefaultAppsUiState(
    val roles: List<DefaultAppRole> = emptyList(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class DefaultAppsViewModel
    @Inject
    constructor(
        private val useCases: ApplicationsUseCases,
    ) : ViewModel() {
        private val working = ActiveOperationTracker()
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<DefaultAppsUiState> =
            combine(useCases.observeDefaultAppRoles(), working.isActive, message) { roles, isWorking, error ->
                DefaultAppsUiState(roles, isWorking, error)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultAppsUiState())

        init {
            refresh()
        }

        fun refresh() = execute(showProgress = false) { useCases.refreshDefaultApps() }

        fun setDefaultApp(
            roleName: String,
            packageName: String,
        ) = execute {
            useCases.setDefaultApp(roleName, packageName)
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

data class OpeningLinksUiState(
    val links: List<OpeningLinkState> = emptyList(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class OpeningLinksViewModel
    @Inject
    constructor(
        private val useCases: ApplicationsUseCases,
    ) : ViewModel() {
        private val working = ActiveOperationTracker()
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<OpeningLinksUiState> =
            combine(useCases.observeOpeningLinks(), working.isActive, message) { links, isWorking, error ->
                OpeningLinksUiState(links, isWorking, error)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpeningLinksUiState())

        init {
            refresh()
        }

        fun refresh() = execute(showProgress = false) { useCases.refreshOpeningLinks() }

        fun setEnabled(
            packageName: String,
            enabled: Boolean,
        ) = execute {
            useCases.setOpeningLinksEnabled(packageName, enabled)
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
        private val working = ActiveOperationTracker()
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<ApplicationDetailsUiState> =
            combine(useCases.observeDetails(), working.isActive, message, ::ApplicationDetailsUiState)
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

        fun setNotificationsEnabled(enabled: Boolean) = execute { useCases.setNotificationsEnabled(enabled) }

        fun setUnusedAppOptimizationEnabled(enabled: Boolean) = execute { useCases.setUnusedAppOptimizationEnabled(enabled) }

        fun forceStop() = execute { useCases.forceStop() }

        fun performPrimaryAction() = execute { useCases.performPrimaryAction() }

        fun clearStorage() = execute { useCases.clearStorage() }

        fun clearCache() = execute { useCases.clearCache() }

        fun setPrioritizePerformanceEnabled(enabled: Boolean) = execute { useCases.setPrioritizePerformanceEnabled(enabled) }

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
        private val working = ActiveOperationTracker()
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<SpecialAccessUiState> =
            combine(useCases.observeSpecialAccess(), working.isActive, message, ::SpecialAccessUiState)
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

        fun setShowSystemApps(show: Boolean) = execute { useCases.setSpecialAccessShowSystemApps(show) }

        fun setAllowed(
            packageName: String,
            allowed: Boolean,
        ) = execute { useCases.setSpecialAccessAllowed(packageName, allowed) }

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
