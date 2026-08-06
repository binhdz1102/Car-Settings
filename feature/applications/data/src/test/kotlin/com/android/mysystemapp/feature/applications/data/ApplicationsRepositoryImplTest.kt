package com.android.car.settings.feature.applications.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.applications.domain.ApplicationDetails
import com.android.car.settings.feature.applications.domain.ApplicationSummary
import com.android.car.settings.feature.applications.domain.SpecialAccessState
import com.android.car.settings.feature.applications.domain.SpecialAccessType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ApplicationsRepositoryImplTest {
    @Test
    fun `repository combines app state and delegates privileged actions`() =
        runTest {
            val platform = RecordingApplicationsPlatform()
            val repository = ApplicationsRepositoryImpl(platform)

            platform.apps.value = listOf(ApplicationSummary(packageName = "a", label = "A"))
            platform.performanceImpactingApps.value =
                listOf(ApplicationSummary(packageName = "b", label = "B"))
            assertThat(repository.state.value().apps).hasSize(1)
            assertThat(repository.state.value().performanceImpactingApps).hasSize(1)

            repository.refresh()
            repository.setShowSystemApps(true)
            repository.selectApplication("a")
            repository.setNotificationsEnabled(false)
            repository.setUnusedAppOptimizationEnabled(false)
            repository.forceStop()
            repository.performPrimaryAction()
            repository.clearStorage()
            repository.clearCache()
            repository.setPrioritizePerformanceEnabled(true)
            repository.selectSpecialAccess(SpecialAccessType.USAGE_ACCESS)
            repository.setSpecialAccessShowSystemApps(true)
            repository.setSpecialAccessAllowed("a", true)

            assertThat(platform.commands).contains("special-allowed:a:true")
            assertThat(repository.details).isSameInstanceAs(platform.selectedDetails)
            assertThat(repository.specialAccess).isSameInstanceAs(platform.specialAccess)
        }
}

private class RecordingApplicationsPlatform : ApplicationsPlatform {
    override val apps = MutableStateFlow<List<ApplicationSummary>>(emptyList())
    override val recentApps = MutableStateFlow<List<ApplicationSummary>>(emptyList())
    override val unusedAppCount = MutableStateFlow<Int?>(null)
    override val selectedDetails = MutableStateFlow<ApplicationDetails?>(null)
    override val showSystemApps = MutableStateFlow(false)
    override val specialAccess = MutableStateFlow(SpecialAccessState())
    override val performanceImpactingApps = MutableStateFlow<List<ApplicationSummary>>(emptyList())
    val commands = mutableListOf<String>()

    override suspend fun refresh() = record("refresh")
    override suspend fun setShowSystemApps(show: Boolean) = record("show:$show")
    override suspend fun selectApplication(packageName: String) = record("select:$packageName")
    override suspend fun clearSelectedApplication() = record("clear-selected")
    override suspend fun setNotificationsEnabled(enabled: Boolean) = record("notifications:$enabled")
    override suspend fun setUnusedAppOptimizationEnabled(enabled: Boolean) = record("unused:$enabled")
    override suspend fun forceStop() = record("force")
    override suspend fun performPrimaryAction() = record("primary")
    override suspend fun clearStorage() = record("storage")
    override suspend fun clearCache() = record("cache")
    override suspend fun setPrioritizePerformanceEnabled(enabled: Boolean) = record("priority:$enabled")
    override suspend fun selectSpecialAccess(type: SpecialAccessType) = record("special:$type")
    override suspend fun setSpecialAccessShowSystemApps(show: Boolean) = record("special-show:$show")
    override suspend fun setSpecialAccessAllowed(packageName: String, allowed: Boolean) =
        record("special-allowed:$packageName:$allowed")

    private fun record(command: String): ActionResult {
        commands += command
        return ActionResult.Success
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.value(): T =
    first()
