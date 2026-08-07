package com.android.car.settings.feature.privacy.domain

import com.android.car.settings.core.common.ActionResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PrivacyUseCasesTest {
    @Test
    fun delegatesSensorLocationAndAppPermissionChanges() = runTest {
        val repository = FakePrivacyRepository()
        val useCases = PrivacyUseCases(repository)

        useCases.setMicrophoneAccessEnabled(false)
        useCases.setCameraAccessEnabled(true)
        useCases.setLocationEnabled(true)
        useCases.setAppPermission("app.one", PrivacyPermissionType.CAMERA, false)

        assertThat(repository.calls).containsExactly("mic:false", "camera:true", "location:true", "app.one:CAMERA:false")
            .inOrder()
    }

    private class FakePrivacyRepository : PrivacyRepository {
        override val state = MutableStateFlow(PrivacyState())
        val calls = mutableListOf<String>()
        override suspend fun refresh() = ActionResult.Success
        override suspend fun setMicrophoneAccessEnabled(enabled: Boolean): ActionResult { calls += "mic:$enabled"; return ActionResult.Success }
        override suspend fun setCameraAccessEnabled(enabled: Boolean): ActionResult { calls += "camera:$enabled"; return ActionResult.Success }
        override suspend fun setLocationEnabled(enabled: Boolean): ActionResult { calls += "location:$enabled"; return ActionResult.Success }
        override suspend fun selectPermissionType(type: PrivacyPermissionType) = ActionResult.Success
        override suspend fun setAppPermission(packageName: String, type: PrivacyPermissionType, granted: Boolean): ActionResult {
            calls += "$packageName:${type.name}:$granted"
            return ActionResult.Success
        }
    }
}
