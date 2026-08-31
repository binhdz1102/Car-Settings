package com.android.car.settings.feature.security.data

import android.content.Context
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.core.common.rethrowIfCancellation
import com.android.car.settings.feature.security.domain.DeviceAdminApp
import com.android.car.settings.feature.security.domain.SecurityLockType
import com.android.car.settings.feature.security.domain.SecurityState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AndroidSecurityPlatform
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SecurityPlatform {
        private val mutableState = MutableStateFlow(SecurityState())
        override val state: StateFlow<SecurityState> = mutableState.asStateFlow()

        override suspend fun refresh(): ActionResult =
            execute {
                val lockType =
                    runCatching {
                        check(SecurityHiddenApiBridge.canManageScreenLock(context)) {
                            SCREEN_LOCK_PERMISSION_REQUIRED_MESSAGE
                        }
                        SecurityHiddenApiBridge.getLockType(context).toLockType()
                    }
                mutableState.value =
                    SecurityState(
                        lockType = lockType.getOrDefault(SecurityLockType.UNKNOWN),
                        canManageScreenLock = lockType.isSuccess,
                        screenLockUnavailableReason = lockType.exceptionOrNull()?.message,
                        isGuestUser = SecurityHiddenApiBridge.isGuestUser(context),
                        deviceAdmins =
                            SecurityHiddenApiBridge
                                .getActiveAdmins(context)
                                .map { DeviceAdminApp(it.componentName, it.label, it.packageName) }
                                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }),
                    )
            }

        override suspend fun setLock(
            type: SecurityLockType,
            currentCredential: String,
            newCredential: String,
        ): ActionResult =
            execute {
                check(SecurityHiddenApiBridge.canManageScreenLock(context)) {
                    SCREEN_LOCK_PERMISSION_REQUIRED_MESSAGE
                }
                check(!SecurityHiddenApiBridge.isGuestUser(context)) {
                    "Screen lock cannot be changed for a guest profile"
                }
                val currentType = SecurityHiddenApiBridge.getLockType(context).toLockType()
                if (currentType != SecurityLockType.NONE) {
                    check(currentCredential.isNotBlank()) { "Enter the current screen lock" }
                }
                validateReplacement(type, newCredential)
                check(SecurityHiddenApiBridge.setLock(context, type.bridgeType(), currentCredential, newCredential)) {
                    "The current screen lock is incorrect or the system rejected the new screen lock"
                }
                refreshOrThrow()
            }

        override suspend fun resetCredentials(currentCredential: String): ActionResult =
            execute {
                check(SecurityHiddenApiBridge.canManageScreenLock(context)) {
                    SCREEN_LOCK_PERMISSION_REQUIRED_MESSAGE
                }
                check(!SecurityHiddenApiBridge.isGuestUser(context)) {
                    "Credentials cannot be reset for a guest profile"
                }
                if (SecurityHiddenApiBridge.getLockType(context).toLockType() != SecurityLockType.NONE) {
                    check(currentCredential.isNotBlank()) { "Enter the current screen lock" }
                }
                check(SecurityHiddenApiBridge.resetCredentials(context, currentCredential)) {
                    "The current screen lock is incorrect or credentials could not be reset"
                }
            }

        override suspend fun removeDeviceAdmin(componentName: String): ActionResult =
            execute {
                SecurityHiddenApiBridge.removeActiveAdmin(context, componentName)
                refreshOrThrow()
            }

        private fun validateReplacement(
            type: SecurityLockType,
            value: String,
        ) {
            when (type) {
                SecurityLockType.NONE -> Unit
                SecurityLockType.PIN ->
                    require(value.length >= MIN_PIN_LENGTH && value.all(Char::isDigit)) {
                        "PIN must contain at least $MIN_PIN_LENGTH digits"
                    }
                SecurityLockType.PASSWORD ->
                    require(value.length >= MIN_PASSWORD_LENGTH) {
                        "Password must contain at least $MIN_PASSWORD_LENGTH characters"
                    }
                SecurityLockType.PATTERN ->
                    require(value.split(',').filter(String::isNotBlank).size >= MIN_PATTERN_CELLS) {
                        "Pattern must connect at least $MIN_PATTERN_CELLS cells"
                    }
                SecurityLockType.UNKNOWN -> error("Unsupported screen lock type")
            }
        }

        private suspend fun execute(block: suspend () -> Unit): ActionResult =
            withContext(dispatcher) {
                try {
                    block()
                    ActionResult.Success
                } catch (throwable: Throwable) {
                    throwable.rethrowIfCancellation()
                    val message = throwable.cause?.message ?: throwable.message ?: "Unknown security error"
                    mutableState.value = mutableState.value.copy(lastError = message)
                    ActionResult.Failure(message, throwable)
                }
            }

        private suspend fun refreshOrThrow() {
            check(refresh() == ActionResult.Success) { "Unable to refresh security settings" }
        }
    }

private fun Int.toLockType(): SecurityLockType =
    when (this) {
        SecurityHiddenApiBridge.TYPE_NONE -> SecurityLockType.NONE
        SecurityHiddenApiBridge.TYPE_PATTERN -> SecurityLockType.PATTERN
        SecurityHiddenApiBridge.TYPE_PIN -> SecurityLockType.PIN
        SecurityHiddenApiBridge.TYPE_PASSWORD -> SecurityLockType.PASSWORD
        else -> SecurityLockType.UNKNOWN
    }

private fun SecurityLockType.bridgeType(): Int =
    when (this) {
        SecurityLockType.NONE -> SecurityHiddenApiBridge.TYPE_NONE
        SecurityLockType.PATTERN -> SecurityHiddenApiBridge.TYPE_PATTERN
        SecurityLockType.PIN -> SecurityHiddenApiBridge.TYPE_PIN
        SecurityLockType.PASSWORD -> SecurityHiddenApiBridge.TYPE_PASSWORD
        SecurityLockType.UNKNOWN -> SecurityHiddenApiBridge.TYPE_UNKNOWN
    }

private const val MIN_PIN_LENGTH = 4
private const val MIN_PASSWORD_LENGTH = 4
private const val MIN_PATTERN_CELLS = 4
private const val SCREEN_LOCK_PERMISSION_REQUIRED_MESSAGE =
    "Screen-lock controls require the AAOS system Settings identity on this vehicle image"
