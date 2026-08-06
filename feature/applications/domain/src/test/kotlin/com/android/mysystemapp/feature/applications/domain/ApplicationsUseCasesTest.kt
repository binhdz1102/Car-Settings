package com.android.car.settings.feature.applications.domain

import com.android.car.settings.core.common.ActionResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ApplicationsUseCasesTest {
    @Test
    fun `use cases delegate every Applications command`() =
        runTest {
            val repository = RecordingApplicationsRepository()
            val useCases = ApplicationsUseCases(repository)

            assertThat(useCases.refresh()).isEqualTo(ActionResult.Success)
            assertThat(useCases.setShowSystemApps(true)).isEqualTo(ActionResult.Success)
            assertThat(useCases.selectApplication("test.app")).isEqualTo(ActionResult.Success)
            assertThat(useCases.clearSelectedApplication()).isEqualTo(ActionResult.Success)
            assertThat(useCases.setNotificationsEnabled(false)).isEqualTo(ActionResult.Success)
            assertThat(useCases.setUnusedAppOptimizationEnabled(false)).isEqualTo(ActionResult.Success)
            assertThat(useCases.forceStop()).isEqualTo(ActionResult.Success)
            assertThat(useCases.performPrimaryAction()).isEqualTo(ActionResult.Success)
            assertThat(useCases.clearStorage()).isEqualTo(ActionResult.Success)
            assertThat(useCases.clearCache()).isEqualTo(ActionResult.Success)
            assertThat(useCases.setPrioritizePerformanceEnabled(true)).isEqualTo(ActionResult.Success)
            assertThat(useCases.selectSpecialAccess(SpecialAccessType.WIFI_CONTROL))
                .isEqualTo(ActionResult.Success)
            assertThat(useCases.setSpecialAccessShowSystemApps(true)).isEqualTo(ActionResult.Success)
            assertThat(useCases.setSpecialAccessAllowed("test.app", true)).isEqualTo(ActionResult.Success)

            assertThat(repository.commands).containsExactly(
                "refresh",
                "show-system:true",
                "select:test.app",
                "clear-selected",
                "notifications:false",
                "unused:false",
                "force-stop",
                "primary",
                "clear-storage",
                "clear-cache",
                "prioritize:true",
                "special:WIFI_CONTROL",
                "special-show-system:true",
                "special-allowed:test.app:true",
            ).inOrder()
        }
}

private class RecordingApplicationsRepository : ApplicationsRepository {
    override val state = MutableStateFlow(ApplicationsState())
    override val details = MutableStateFlow<ApplicationDetails?>(null)
    override val specialAccess = MutableStateFlow(SpecialAccessState())
    val commands = mutableListOf<String>()

    override suspend fun refresh() = record("refresh")
    override suspend fun setShowSystemApps(show: Boolean) = record("show-system:$show")
    override suspend fun selectApplication(packageName: String) = record("select:$packageName")
    override suspend fun clearSelectedApplication() = record("clear-selected")
    override suspend fun setNotificationsEnabled(enabled: Boolean) = record("notifications:$enabled")
    override suspend fun setUnusedAppOptimizationEnabled(enabled: Boolean) = record("unused:$enabled")
    override suspend fun forceStop() = record("force-stop")
    override suspend fun performPrimaryAction() = record("primary")
    override suspend fun clearStorage() = record("clear-storage")
    override suspend fun clearCache() = record("clear-cache")
    override suspend fun setPrioritizePerformanceEnabled(enabled: Boolean) = record("prioritize:$enabled")
    override suspend fun selectSpecialAccess(type: SpecialAccessType) = record("special:$type")
    override suspend fun setSpecialAccessShowSystemApps(show: Boolean) = record("special-show-system:$show")
    override suspend fun setSpecialAccessAllowed(packageName: String, allowed: Boolean) =
        record("special-allowed:$packageName:$allowed")

    private fun record(command: String): ActionResult {
        commands += command
        return ActionResult.Success
    }
}
