@file:SuppressLint("MissingPermission", "InlinedApi")

package com.android.car.settings.feature.profileaccounts.data

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.accounts.AccountManagerFuture
import android.accounts.AuthenticatorDescription
import android.accounts.OnAccountsUpdateListener
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.car.Car
import android.car.user.CarUserManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SyncAdapterType
import android.content.SyncInfo
import android.content.SyncStatusObserver
import android.content.pm.UserInfo
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.core.vehicle.CarServiceProvider
import com.android.car.settings.feature.profileaccounts.domain.AccountDetails
import com.android.car.settings.feature.profileaccounts.domain.AccountProvider
import com.android.car.settings.feature.profileaccounts.domain.AccountSummary
import com.android.car.settings.feature.profileaccounts.domain.AddAccountResult
import com.android.car.settings.feature.profileaccounts.domain.ProfileAccountsState
import com.android.car.settings.feature.profileaccounts.domain.ProfileDetails
import com.android.car.settings.feature.profileaccounts.domain.ProfileSummary
import com.android.car.settings.feature.profileaccounts.domain.SyncAuthority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** AAOS user, account and sync implementation used by the Profile & accounts screens. */
@Singleton
@Suppress("TooManyFunctions")
internal class AndroidProfileAccountsPlatform
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        private val carServiceProvider: CarServiceProvider,
    ) : ProfileAccountsPlatform {
        private val userManager = context.getSystemService(UserManager::class.java)
        private val accountManager = context.getSystemService(AccountManager::class.java)
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val mutableState = MutableStateFlow(ProfileAccountsState())
        private val mutableSelectedProfile = MutableStateFlow<ProfileDetails?>(null)
        private val mutableSelectedAccount = MutableStateFlow<AccountDetails?>(null)

        @Volatile private var carUserManager: CarUserManager? = null

        @Volatile private var selectedAccountKey: AccountKey? = null

        override val state: StateFlow<ProfileAccountsState> = mutableState.asStateFlow()
        override val selectedProfile: StateFlow<ProfileDetails?> = mutableSelectedProfile.asStateFlow()
        override val selectedAccount: StateFlow<AccountDetails?> = mutableSelectedAccount.asStateFlow()

        private val accountListener = OnAccountsUpdateListener { scheduleRefresh() }
        private val syncObserver = SyncStatusObserver { scheduleRefresh() }
        private val userChangeReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = scheduleRefresh()
            }

        init {
            context.registerReceiver(
                userChangeReceiver,
                IntentFilter().apply {
                    addAction(ACTION_USER_ADDED)
                    addAction(ACTION_USER_REMOVED)
                    addAction(ACTION_USER_INFO_CHANGED)
                    addAction(ACTION_USER_SWITCHED)
                },
                Context.RECEIVER_NOT_EXPORTED,
            )
            accountManager.addOnAccountsUpdatedListener(accountListener, null, true, null)
            ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE or
                    ContentResolver.SYNC_OBSERVER_TYPE_PENDING or
                    ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS or
                    SYNC_OBSERVER_TYPE_STATUS,
                syncObserver,
            )
            carServiceProvider.register { connectedCar, ready ->
                carUserManager =
                    if (ready) connectedCar.getCarManager(Car.CAR_USER_SERVICE) as? CarUserManager else null
                scheduleRefresh()
            }
            scheduleRefresh()
        }

        override suspend fun refresh(): ActionResult = execute(::refreshState)

        override suspend fun selectProfile(userId: Int): ActionResult =
            execute {
                val profile = requireNotNull(loadProfiles().firstOrNull { it.id == userId }) { "Profile is unavailable" }
                mutableSelectedProfile.value = profile.toDetails()
            }

        override suspend fun clearSelectedProfile(): ActionResult =
            execute {
                mutableSelectedProfile.value = null
            }

        override suspend fun renameCurrentProfile(name: String): ActionResult =
            execute {
                val sanitizedName = name.trim()
                require(sanitizedName.isNotEmpty()) { "Profile name cannot be empty" }
                val current = requireCurrentProfile()
                require(!current.isGuest && !current.isDemo) { "This profile cannot be renamed" }
                userManager.callHidden(
                    "setUserName",
                    arrayOf(Int::class.javaPrimitiveType!!, String::class.java),
                    current.id,
                    sanitizedName,
                )
                refreshState()
            }

        override suspend fun addProfile(name: String): ActionResult =
            execute {
                require(canAddProfiles()) { profileRestrictionMessage() ?: "Adding profiles is not allowed" }
                require(canSwitchProfiles()) { "User switching is restricted by the administrator" }
                val manager = requireNotNull(carUserManager) { "Vehicle user service is not connected" }
                val result =
                    requireNotNull(
                        manager
                            .createUser(name.trim().ifEmpty { DEFAULT_NEW_PROFILE_NAME }, 0)
                            .get(USER_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    )
                require(result.isSuccess()) { result.errorMessage ?: "The vehicle rejected the new profile" }
                val newUserId =
                    requireNotNull(result.user) { "The vehicle did not return the new profile" }
                        .callHidden("getIdentifier") as? Int
                        ?: error("The vehicle returned an invalid profile")
                val switchResult =
                    requireNotNull(
                        manager.switchUser(newUserId).get(USER_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    )
                require(switchResult.isSuccess()) {
                    switchResult.errorMessage ?: "The profile was created but could not be activated"
                }
                refreshState()
            }

        override suspend fun logoutCurrentUser(): ActionResult =
            execute {
                val current = requireCurrentProfile()
                require(current.isGuest) { "Only a guest session can be logged out here" }
                require(!userManager.hasUserRestriction(UserManager.DISALLOW_USER_SWITCH)) {
                    "User switching is restricted by the administrator"
                }
                val manager = requireNotNull(carUserManager) { "Vehicle user service is not connected" }
                val result = requireNotNull(manager.logoutUser().get(USER_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                require(result.isSuccess()) { result.errorMessage ?: "The vehicle rejected guest logout" }
                scheduleRefresh()
            }

        override suspend fun switchProfile(userId: Int): ActionResult =
            execute {
                val profile = requireNotNull(loadProfiles().firstOrNull { it.id == userId }) { "Profile is unavailable" }
                require(!profile.isCurrent) { "This profile is already active" }
                require(canSwitchProfiles()) { "User switching is restricted by the administrator" }
                val manager = requireNotNull(carUserManager) { "Vehicle user service is not connected" }
                val result = requireNotNull(manager.switchUser(userId).get(USER_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                require(result.isSuccess()) { result.errorMessage ?: "The vehicle rejected the profile switch" }
            }

        override suspend fun removeProfile(userId: Int): ActionResult =
            execute {
                val profile = requireNotNull(loadProfiles().firstOrNull { it.id == userId }) { "Profile is unavailable" }
                require(userId != SYSTEM_USER_ID) { "The system user cannot be removed" }
                require(!profile.isCurrent) { "Switch to another profile before removing this profile" }
                require(requireNotNull(carUserManager) { "Vehicle user service is not connected" }.removeUser(userId).isSuccess()) {
                    "The vehicle rejected removal of ${profile.name}"
                }
                refreshState()
            }

        override suspend fun setMasterSyncEnabled(enabled: Boolean): ActionResult =
            execute {
                require(canModifyAccounts()) { "This profile is not allowed to modify accounts" }
                contentResolverCall(
                    "setMasterSyncAutomaticallyAsUser",
                    arrayOf(Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
                    enabled,
                    currentUserId,
                )
                refreshState()
            }

        override suspend fun selectAccount(
            name: String,
            type: String,
        ): ActionResult =
            execute {
                require(loadAccounts().any { it.name == name && it.type == type }) { "Account is unavailable" }
                selectedAccountKey = AccountKey(name, type)
                mutableSelectedAccount.value = loadAccountDetails(Account(name, type))
            }

        override suspend fun clearSelectedAccount(): ActionResult =
            execute {
                selectedAccountKey = null
                mutableSelectedAccount.value = null
            }

        override suspend fun setAccountSyncEnabled(
            authority: String,
            enabled: Boolean,
        ): ActionResult =
            execute {
                require(canModifyAccounts()) { "This profile is not allowed to modify accounts" }
                val account = requireSelectedAccount()
                require(loadSyncAuthorities(account).any { it.authority == authority }) { "Sync item is unavailable" }
                contentResolverCall(
                    "setSyncAutomaticallyAsUser",
                    arrayOf(Account::class.java, String::class.java, Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
                    account,
                    authority,
                    enabled,
                    currentUserId,
                )
                if (enabled) requestSync(account, authority) else cancelSync(account, authority)
                refreshState()
            }

        override suspend fun requestAccountSync(authority: String): ActionResult =
            execute {
                val account = requireSelectedAccount()
                require(loadSyncAuthorities(account).any { it.authority == authority }) { "Sync item is unavailable" }
                requestSync(account, authority)
                refreshState()
            }

        override suspend fun removeSelectedAccount(): ActionResult =
            execute {
                require(canModifyAccounts()) { "This profile is not allowed to modify accounts" }
                val future =
                    accountManager.callHidden(
                        "removeAccountAsUser",
                        arrayOf(
                            Account::class.java,
                            AccountManagerCallback::class.java,
                            android.os.Handler::class.java,
                            UserHandle::class.java,
                        ),
                        requireSelectedAccount(),
                        null,
                        null,
                        currentUserHandle,
                    ) as? AccountManagerFuture<*> ?: error("Account removal is unavailable")
                require(future.getResult(USER_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS) == true) {
                    "The account provider did not remove this account"
                }
                selectedAccountKey = null
                mutableSelectedAccount.value = null
                refreshState()
            }

        override suspend fun addAccount(providerType: String): AddAccountResult =
            withContext(dispatcher) {
                if (!canModifyAccounts()) return@withContext AddAccountResult.Failure("This profile is not allowed to modify accounts")
                if (loadAccountProviders().none { it.type == providerType }) {
                    return@withContext AddAccountResult.Failure("Account provider is unavailable")
                }
                runCatching {
                    val options = Bundle().apply { putBoolean(ALLOW_SKIP, true) }
                    val identity =
                        Intent(ACTION_CALLER_IDENTITY).apply {
                            component = ComponentName(ACTION_CALLER_IDENTITY, ACTION_CALLER_IDENTITY)
                            addCategory(ACTION_CALLER_IDENTITY)
                        }
                    options.putParcelable(
                        KEY_CALLER_IDENTITY,
                        PendingIntent.getBroadcast(context, 0, identity, PendingIntent.FLAG_IMMUTABLE),
                    )
                    accountManager.callHidden(
                        "addAccountAsUser",
                        arrayOf(
                            String::class.java,
                            String::class.java,
                            Array<String>::class.java,
                            Bundle::class.java,
                            android.app.Activity::class.java,
                            AccountManagerCallback::class.java,
                            android.os.Handler::class.java,
                            UserHandle::class.java,
                        ),
                        providerType,
                        null,
                        null,
                        options,
                        null,
                        AccountManagerCallback<Bundle> { future -> startAddAccountIntent(future) },
                        null,
                        currentUserHandle,
                    )
                }.fold(
                    onSuccess = { AddAccountResult.Started },
                    onFailure = { AddAccountResult.Failure(it.message ?: it.javaClass.simpleName) },
                )
            }

        private fun startAddAccountIntent(future: AccountManagerFuture<Bundle>) {
            runCatching { future.result.getParcelable<Intent>(AccountManager.KEY_INTENT) }
                .getOrNull()
                ?.let { intent ->
                    intent.putExtra(Intent.EXTRA_USER, currentUserHandle)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            scheduleRefresh()
        }

        private fun refreshState() {
            val profiles = loadProfiles()
            mutableState.value =
                ProfileAccountsState(
                    currentProfile = profiles.firstOrNull { it.isCurrent },
                    profiles = profiles,
                    accounts = loadAccounts(),
                    accountProviders = loadAccountProviders(),
                    masterSyncEnabled =
                        contentResolverCall(
                            "getMasterSyncAutomaticallyAsUser",
                            arrayOf(Int::class.javaPrimitiveType!!),
                            currentUserId,
                        ) as? Boolean ?: false,
                    canAddProfile = canAddProfiles(),
                    canSwitchProfile = canSwitchProfiles(),
                    canLogout = canLogoutCurrentUser(profiles),
                    canModifyAccounts = canModifyAccounts(),
                    profileRestriction = profileRestrictionMessage(),
                )
            mutableSelectedProfile.value?.profile?.id?.let { id ->
                mutableSelectedProfile.value = loadProfiles().firstOrNull { it.id == id }?.toDetails()
            }
            selectedAccountKey?.let { key ->
                mutableSelectedAccount.value =
                    if (loadAccounts().any { it.name == key.name && it.type == key.type }) {
                        loadAccountDetails(Account(key.name, key.type))
                    } else {
                        null
                    }
            }
        }

        private fun loadProfiles(): List<ProfileSummary> =
            userManager
                .callHidden("getAliveUsers")
                .asUserInfoList()
                .filterNot { it.id == SYSTEM_USER_ID && isHeadlessSystemUser() }
                .map { it.toProfileSummary() }
                .sortedWith(compareByDescending<ProfileSummary> { it.isCurrent }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        private fun UserInfo.toProfileSummary() =
            ProfileSummary(
                id = id,
                name = name ?: "User $id",
                isCurrent = id == currentUserId,
                isAdmin = userManager.callHidden("isUserAdmin", arrayOf(Int::class.javaPrimitiveType!!), id) as? Boolean ?: false,
                isGuest = isGuest,
                isDemo = isDemo,
                isRunning = userManager.callHidden("isUserRunning", arrayOf(Int::class.javaPrimitiveType!!), id) as? Boolean ?: false,
            )

        private fun ProfileSummary.toDetails() =
            ProfileDetails(
                profile = this,
                canRename = isCurrent && !isGuest && !isDemo,
                // Guest sessions are switchable in AAOS. The previous guard made the guest
                // profile visible but impossible to enter from the profile picker.
                canSwitch = !isCurrent,
                canRemove = id != SYSTEM_USER_ID && !isCurrent,
            )

        private fun loadAccounts(): List<AccountSummary> {
            val labels = loadAccountProviders().associate { it.type to it.label }
            return accountManager
                .callHidden(
                    "getAccountsAsUser",
                    arrayOf(Int::class.javaPrimitiveType!!),
                    currentUserId,
                ).asAccountList()
                .map { AccountSummary(it.name, it.type, labels[it.type] ?: it.type) }
                .sortedBy { "${it.providerLabel}\u0000${it.name}".lowercase() }
        }

        private fun loadAccountProviders(): List<AccountProvider> =
            accountManager
                .callHidden(
                    "getAuthenticatorTypesAsUser",
                    arrayOf(Int::class.javaPrimitiveType!!),
                    currentUserId,
                ).asAuthenticatorList()
                .asSequence()
                .filter { it.type.isNotBlank() }
                .map { AccountProvider(it.type, accountProviderLabel(it)) }
                .distinctBy { it.type }
                .sortedBy { it.label.lowercase() }
                .toList()

        private fun accountProviderLabel(descriptor: AuthenticatorDescription): String =
            runCatching {
                context.packageManager.getResourcesForApplication(descriptor.packageName).getString(descriptor.labelId)
            }.getOrElse { descriptor.type }

        private fun loadAccountDetails(account: Account): AccountDetails =
            AccountDetails(
                account = requireNotNull(loadAccounts().firstOrNull { it.name == account.name && it.type == account.type }),
                syncAuthorities = loadSyncAuthorities(account),
                canModify = canModifyAccounts(),
            )

        private fun loadSyncAuthorities(account: Account): List<SyncAuthority> {
            val syncs =
                contentResolverCall(
                    "getCurrentSyncsAsUser",
                    arrayOf(Int::class.javaPrimitiveType!!),
                    currentUserId,
                ).asSyncInfoList()
            return contentResolverCall(
                "getSyncAdapterTypesAsUser",
                arrayOf(Int::class.javaPrimitiveType!!),
                currentUserId,
            ).asSyncAdapterList()
                .asSequence()
                .filter { it.accountType == account.type && it.isUserVisible() }
                .filter { syncable(account, it.authority) > 0 }
                .map { adapter ->
                    val enabled = syncEnabled(account, adapter.authority)
                    val active = syncs.any { it.account == account && it.authority == adapter.authority }
                    SyncAuthority(
                        authority = adapter.authority,
                        label = adapter.authority.replace('.', ' '),
                        enabled = enabled,
                        active = active,
                        summary =
                            if (active) {
                                "Syncing now"
                            } else if (enabled) {
                                "Sync enabled"
                            } else {
                                "Sync disabled"
                            },
                    )
                }.sortedBy { it.label.lowercase() }
                .toList()
        }

        private fun syncable(
            account: Account,
            authority: String,
        ): Int =
            contentResolverCall(
                "getIsSyncableAsUser",
                arrayOf(Account::class.java, String::class.java, Int::class.javaPrimitiveType!!),
                account,
                authority,
                currentUserId,
            ) as? Int ?: 0

        private fun syncEnabled(
            account: Account,
            authority: String,
        ): Boolean =
            contentResolverCall(
                "getSyncAutomaticallyAsUser",
                arrayOf(Account::class.java, String::class.java, Int::class.javaPrimitiveType!!),
                account,
                authority,
                currentUserId,
            ) as? Boolean ?: false

        private fun requestSync(
            account: Account,
            authority: String,
        ) {
            contentResolverCall(
                "requestSyncAsUser",
                arrayOf(Account::class.java, String::class.java, Int::class.javaPrimitiveType!!, Bundle::class.java),
                account,
                authority,
                currentUserId,
                Bundle.EMPTY,
            )
        }

        private fun cancelSync(
            account: Account,
            authority: String,
        ) {
            contentResolverCall(
                "cancelSyncAsUser",
                arrayOf(Account::class.java, String::class.java, Int::class.javaPrimitiveType!!),
                account,
                authority,
                currentUserId,
            )
        }

        private fun requireCurrentProfile(): ProfileSummary =
            requireNotNull(loadProfiles().firstOrNull { it.isCurrent }) { "Current profile is unavailable" }

        private fun requireSelectedAccount(): Account =
            requireNotNull(selectedAccountKey) {
                "No account is selected"
            }.let { Account(it.name, it.type) }

        private fun canModifyAccounts(): Boolean =
            requireCurrentProfile().let {
                !it.isGuest && !it.isDemo && !userManager.hasUserRestriction(UserManager.DISALLOW_MODIFY_ACCOUNTS)
            }

        private fun canAddProfiles(): Boolean =
            !userManager.hasUserRestriction(UserManager.DISALLOW_ADD_USER) &&
                userManager.callHidden("canAddMoreUsers", arrayOf(String::class.java), FULL_SECONDARY_USER_TYPE) == true &&
                carUserManager != null

        private fun canSwitchProfiles(): Boolean =
            !userManager.hasUserRestriction(UserManager.DISALLOW_USER_SWITCH) &&
                carUserManager != null

        private fun canLogoutCurrentUser(profiles: List<ProfileSummary>): Boolean =
            profiles.firstOrNull { it.isCurrent }?.isGuest == true &&
                !userManager.hasUserRestriction(UserManager.DISALLOW_USER_SWITCH) &&
                carUserManager != null

        private fun profileRestrictionMessage(): String? =
            when {
                userManager.hasUserRestriction(UserManager.DISALLOW_ADD_USER) -> "Adding profiles is restricted by the administrator"
                userManager.callHidden(
                    "canAddMoreUsers",
                    arrayOf(String::class.java),
                    FULL_SECONDARY_USER_TYPE,
                ) != true -> "The maximum number of profiles has been reached"
                carUserManager == null -> "Vehicle user service is connecting"
                else -> null
            }

        private fun scheduleRefresh() {
            scope.launch { refresh() }
        }

        private fun isHeadlessSystemUser(): Boolean =
            runCatching {
                UserManager::class.java.getMethod("isHeadlessSystemUserMode").invoke(null) as Boolean
            }.getOrDefault(false)

        private suspend fun execute(block: () -> Unit): ActionResult =
            withContext(dispatcher) {
                try {
                    block()
                    ActionResult.Success
                } catch (throwable: Throwable) {
                    ActionResult.Failure(throwable.message ?: throwable.javaClass.simpleName, throwable)
                }
            }

        private val currentUserHandle: UserHandle get() = Process.myUserHandle()
        private val currentUserId: Int get() =
            UserHandle::class.java.getMethod("myUserId").invoke(null) as Int

        private data class AccountKey(
            val name: String,
            val type: String,
        )

        private companion object {
            const val DEFAULT_NEW_PROFILE_NAME = "New user"
            const val USER_OPERATION_TIMEOUT_SECONDS = 20L
            const val ALLOW_SKIP = "allowSkip"
            const val KEY_CALLER_IDENTITY = "pendingIntent"
            const val ACTION_CALLER_IDENTITY = "com.android.car.settings.accounts.CALLER_IDENTITY"
            const val SYSTEM_USER_ID = 0
            const val FULL_SECONDARY_USER_TYPE = "android.os.usertype.full.SECONDARY"
            const val SYNC_OBSERVER_TYPE_STATUS = 8
            const val ACTION_USER_ADDED = "android.intent.action.USER_ADDED"
            const val ACTION_USER_REMOVED = "android.intent.action.USER_REMOVED"
            const val ACTION_USER_INFO_CHANGED = "android.intent.action.USER_INFO_CHANGED"
            const val ACTION_USER_SWITCHED = "android.intent.action.USER_SWITCHED"
        }
    }

private fun Any.callHidden(
    methodName: String,
    parameterTypes: Array<Class<*>> = emptyArray(),
    vararg args: Any?,
): Any? =
    try {
        javaClass.getMethod(methodName, *parameterTypes).invoke(this, *args)
    } catch (exception: InvocationTargetException) {
        throw (exception.targetException ?: exception)
    }

private fun contentResolverCall(
    methodName: String,
    parameterTypes: Array<Class<*>> = emptyArray(),
    vararg args: Any?,
): Any? =
    try {
        ContentResolver::class.java.getMethod(methodName, *parameterTypes).invoke(null, *args)
    } catch (exception: InvocationTargetException) {
        throw (exception.targetException ?: exception)
    }

private fun Any?.asUserInfoList(): List<UserInfo> = (this as? List<*>)?.filterIsInstance<UserInfo>().orEmpty()

private fun Any?.asAccountList(): List<Account> = (this as? Array<*>)?.filterIsInstance<Account>().orEmpty()

private fun Any?.asAuthenticatorList(): List<AuthenticatorDescription> =
    (this as? Array<*>)?.filterIsInstance<AuthenticatorDescription>().orEmpty()

private fun Any?.asSyncInfoList(): List<SyncInfo> = (this as? List<*>)?.filterIsInstance<SyncInfo>().orEmpty()

private fun Any?.asSyncAdapterList(): List<SyncAdapterType> = (this as? Array<*>)?.filterIsInstance<SyncAdapterType>().orEmpty()
