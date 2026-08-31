package com.android.car.settings.feature.profileaccounts.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.profileaccounts.domain.AccountDetails
import com.android.car.settings.feature.profileaccounts.domain.AddAccountResult
import com.android.car.settings.feature.profileaccounts.domain.ProfileAccountsState
import com.android.car.settings.feature.profileaccounts.domain.ProfileDetails
import kotlinx.coroutines.flow.StateFlow

internal interface ProfileAccountsPlatform {
    val state: StateFlow<ProfileAccountsState>
    val selectedProfile: StateFlow<ProfileDetails?>
    val selectedAccount: StateFlow<AccountDetails?>

    suspend fun refresh(): ActionResult

    suspend fun selectProfile(userId: Int): ActionResult

    suspend fun clearSelectedProfile(): ActionResult

    suspend fun renameCurrentProfile(name: String): ActionResult

    suspend fun addProfile(name: String): ActionResult

    suspend fun logoutCurrentUser(): ActionResult

    suspend fun switchProfile(userId: Int): ActionResult

    suspend fun removeProfile(userId: Int): ActionResult

    suspend fun setMasterSyncEnabled(enabled: Boolean): ActionResult

    suspend fun selectAccount(
        name: String,
        type: String,
    ): ActionResult

    suspend fun clearSelectedAccount(): ActionResult

    suspend fun setAccountSyncEnabled(
        authority: String,
        enabled: Boolean,
    ): ActionResult

    suspend fun requestAccountSync(authority: String): ActionResult

    suspend fun removeSelectedAccount(): ActionResult

    suspend fun addAccount(providerType: String): AddAccountResult
}
