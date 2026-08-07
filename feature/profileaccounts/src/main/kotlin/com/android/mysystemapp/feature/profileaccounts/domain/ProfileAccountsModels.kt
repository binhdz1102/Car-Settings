package com.android.car.settings.feature.profileaccounts.domain

import android.content.Intent

data class ProfileSummary(
    val id: Int,
    val name: String,
    val isCurrent: Boolean,
    val isAdmin: Boolean,
    val isGuest: Boolean,
    val isDemo: Boolean,
    val isRunning: Boolean,
)

data class AccountSummary(
    val name: String,
    val type: String,
    val providerLabel: String,
)

data class AccountProvider(
    val type: String,
    val label: String,
)

data class SyncAuthority(
    val authority: String,
    val label: String,
    val enabled: Boolean,
    val active: Boolean,
    val summary: String,
)

data class ProfileAccountsState(
    val currentProfile: ProfileSummary? = null,
    val profiles: List<ProfileSummary> = emptyList(),
    val accounts: List<AccountSummary> = emptyList(),
    val accountProviders: List<AccountProvider> = emptyList(),
    val masterSyncEnabled: Boolean = false,
    val canAddProfile: Boolean = false,
    val canModifyAccounts: Boolean = false,
    val profileRestriction: String? = null,
)

data class ProfileDetails(
    val profile: ProfileSummary,
    val canRename: Boolean,
    val canSwitch: Boolean,
    val canRemove: Boolean,
)

data class AccountDetails(
    val account: AccountSummary,
    val syncAuthorities: List<SyncAuthority> = emptyList(),
    val canModify: Boolean = false,
)

sealed interface AddAccountResult {
    data object Started : AddAccountResult

    data class NeedsActivity(val intent: Intent) : AddAccountResult

    data class Failure(val message: String) : AddAccountResult
}
