package com.android.car.settings.feature.profileaccounts.domain

import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.flow.Flow

interface ProfileAccountsRepository {
    val state: Flow<ProfileAccountsState>

    val selectedProfile: Flow<ProfileDetails?>

    val selectedAccount: Flow<AccountDetails?>

    suspend fun refresh(): ActionResult

    suspend fun selectProfile(userId: Int): ActionResult

    suspend fun clearSelectedProfile(): ActionResult

    suspend fun renameCurrentProfile(name: String): ActionResult

    suspend fun addProfile(name: String): ActionResult

    suspend fun switchProfile(userId: Int): ActionResult

    suspend fun removeProfile(userId: Int): ActionResult

    suspend fun setMasterSyncEnabled(enabled: Boolean): ActionResult

    suspend fun selectAccount(name: String, type: String): ActionResult

    suspend fun clearSelectedAccount(): ActionResult

    suspend fun setAccountSyncEnabled(authority: String, enabled: Boolean): ActionResult

    suspend fun requestAccountSync(authority: String): ActionResult

    suspend fun removeSelectedAccount(): ActionResult

    suspend fun addAccount(providerType: String): AddAccountResult
}
