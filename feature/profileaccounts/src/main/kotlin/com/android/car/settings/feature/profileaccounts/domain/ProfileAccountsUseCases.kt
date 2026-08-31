package com.android.car.settings.feature.profileaccounts.domain

import javax.inject.Inject

class ProfileAccountsUseCases
    @Inject
    constructor(
        private val repository: ProfileAccountsRepository,
    ) {
        fun observeState() = repository.state

        fun observeSelectedProfile() = repository.selectedProfile

        fun observeSelectedAccount() = repository.selectedAccount

        suspend fun refresh() = repository.refresh()

        suspend fun selectProfile(userId: Int) = repository.selectProfile(userId)

        suspend fun clearSelectedProfile() = repository.clearSelectedProfile()

        suspend fun renameCurrentProfile(name: String) = repository.renameCurrentProfile(name)

        suspend fun addProfile(name: String) = repository.addProfile(name)

        suspend fun logoutCurrentUser() = repository.logoutCurrentUser()

        suspend fun switchProfile(userId: Int) = repository.switchProfile(userId)

        suspend fun removeProfile(userId: Int) = repository.removeProfile(userId)

        suspend fun setMasterSyncEnabled(enabled: Boolean) = repository.setMasterSyncEnabled(enabled)

        suspend fun selectAccount(
            name: String,
            type: String,
        ) = repository.selectAccount(name, type)

        suspend fun clearSelectedAccount() = repository.clearSelectedAccount()

        suspend fun setAccountSyncEnabled(
            authority: String,
            enabled: Boolean,
        ) = repository.setAccountSyncEnabled(authority, enabled)

        suspend fun requestAccountSync(authority: String) = repository.requestAccountSync(authority)

        suspend fun removeSelectedAccount() = repository.removeSelectedAccount()

        suspend fun addAccount(providerType: String) = repository.addAccount(providerType)
    }
