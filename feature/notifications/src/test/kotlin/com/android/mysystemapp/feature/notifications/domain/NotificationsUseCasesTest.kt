package com.android.car.settings.feature.notifications.domain

import com.android.car.settings.core.common.ActionResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotificationsUseCasesTest {
    @Test
    fun delegatesSelectionAndNotificationToggle() = runTest {
        val repository = FakeNotificationsRepository()
        val useCases = NotificationsUseCases(repository)

        useCases.selectApp("app.one")
        useCases.setNotificationsEnabled("app.one", false)

        assertThat(repository.selectedPackage).isEqualTo("app.one")
        assertThat(repository.toggle).isEqualTo("app.one:false")
    }

    private class FakeNotificationsRepository : NotificationsRepository {
        override val state = MutableStateFlow(NotificationsState())
        var selectedPackage: String? = null
        var toggle: String? = null
        override suspend fun refresh() = ActionResult.Success
        override suspend fun selectApp(packageName: String): ActionResult {
            selectedPackage = packageName
            return ActionResult.Success
        }
        override suspend fun setNotificationsEnabled(packageName: String, enabled: Boolean): ActionResult {
            toggle = "$packageName:$enabled"
            return ActionResult.Success
        }
    }
}
