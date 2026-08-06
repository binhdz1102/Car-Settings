package com.android.car.settings.feature.security.domain

import com.android.car.settings.core.common.ActionResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SecurityUseCasesTest {
    @Test
    fun delegatesLockAndCredentialOperations() = runTest {
        val repository = FakeSecurityRepository()
        val useCases = SecurityUseCases(repository)

        useCases.setLock(SecurityLockType.PIN, "old", "1234")
        useCases.resetCredentials("1234")
        useCases.removeDeviceAdmin("pkg/.Admin")

        assertThat(repository.calls).containsExactly("PIN:old:1234", "reset:1234", "admin:pkg/.Admin").inOrder()
    }

    private class FakeSecurityRepository : SecurityRepository {
        override val state = MutableStateFlow(SecurityState())
        val calls = mutableListOf<String>()
        override suspend fun refresh() = ActionResult.Success
        override suspend fun setLock(type: SecurityLockType, currentCredential: String, newCredential: String): ActionResult {
            calls += "${type.name}:$currentCredential:$newCredential"
            return ActionResult.Success
        }
        override suspend fun resetCredentials(currentCredential: String): ActionResult { calls += "reset:$currentCredential"; return ActionResult.Success }
        override suspend fun removeDeviceAdmin(componentName: String): ActionResult { calls += "admin:$componentName"; return ActionResult.Success }
    }
}
