package com.android.car.settings.feature.profileaccounts.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.profileaccounts.domain.AccountDetails
import com.android.car.settings.feature.profileaccounts.domain.AddAccountResult
import com.android.car.settings.feature.profileaccounts.domain.ProfileAccountsRepository
import com.android.car.settings.feature.profileaccounts.domain.ProfileAccountsState
import com.android.car.settings.feature.profileaccounts.domain.ProfileDetails
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ProfileAccountsRepositoryImpl @Inject constructor(
    private val platform: ProfileAccountsPlatform,
) : ProfileAccountsRepository {
    override val state: Flow<ProfileAccountsState> = platform.state
    override val selectedProfile: Flow<ProfileDetails?> = platform.selectedProfile
    override val selectedAccount: Flow<AccountDetails?> = platform.selectedAccount
    override suspend fun refresh() = platform.refresh()
    override suspend fun selectProfile(userId: Int) = platform.selectProfile(userId)
    override suspend fun clearSelectedProfile() = platform.clearSelectedProfile()
    override suspend fun renameCurrentProfile(name: String) = platform.renameCurrentProfile(name)
    override suspend fun addProfile(name: String) = platform.addProfile(name)
    override suspend fun switchProfile(userId: Int) = platform.switchProfile(userId)
    override suspend fun removeProfile(userId: Int) = platform.removeProfile(userId)
    override suspend fun setMasterSyncEnabled(enabled: Boolean) = platform.setMasterSyncEnabled(enabled)
    override suspend fun selectAccount(name: String, type: String) = platform.selectAccount(name, type)
    override suspend fun clearSelectedAccount() = platform.clearSelectedAccount()
    override suspend fun setAccountSyncEnabled(authority: String, enabled: Boolean) =
        platform.setAccountSyncEnabled(authority, enabled)
    override suspend fun requestAccountSync(authority: String) = platform.requestAccountSync(authority)
    override suspend fun removeSelectedAccount() = platform.removeSelectedAccount()
    override suspend fun addAccount(providerType: String): AddAccountResult =
        platform.addAccount(providerType)
}
