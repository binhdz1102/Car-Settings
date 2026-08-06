package com.android.car.settings.feature.bluetooth.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.bluetooth.domain.BluetoothProfileType
import com.android.car.settings.feature.bluetooth.domain.BluetoothState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BluetoothRepositoryImplTest {
    @Test
    fun `repository delegates every platform operation`() =
        runTest {
            val platform = FakeBluetoothPlatform()
            val repository = BluetoothRepositoryImpl(platform)
            val address = "00:11:22:33:44:55"

            repository.refresh()
            repository.setBluetoothEnabled(true)
            repository.startDiscovery()
            repository.stopDiscovery()
            repository.pair(address)
            repository.cancelPairing(address)
            repository.unpair(address)
            repository.connect(address)
            repository.disconnect(address)
            repository.selectDevice(address)
            repository.renameDevice(address, "alias")
            repository.renameAdapter("vehicle")
            repository.setDiscoverable(true, 60)
            repository.setProfileEnabled(address, BluetoothProfileType.A2DP, true)

            assertThat(platform.calls).hasSize(14)
            assertThat(repository.state).isSameInstanceAs(platform.state)
        }
}

private class FakeBluetoothPlatform : BluetoothPlatform {
    val calls = mutableListOf<String>()
    override val state = MutableStateFlow(BluetoothState())

    override suspend fun refresh() = result("refresh")

    override suspend fun setEnabled(enabled: Boolean) = result("enabled")

    override suspend fun startDiscovery() = result("start")

    override suspend fun stopDiscovery() = result("stop")

    override suspend fun pair(address: String) = result("pair")

    override suspend fun cancelPairing(address: String) = result("cancel")

    override suspend fun unpair(address: String) = result("unpair")

    override suspend fun connect(address: String) = result("connect")

    override suspend fun disconnect(address: String) = result("disconnect")

    override suspend fun selectDevice(address: String) = result("select")

    override suspend fun renameDevice(
        address: String,
        alias: String,
    ) = result("renameDevice")

    override suspend fun renameAdapter(name: String) = result("renameAdapter")

    override suspend fun setDiscoverable(
        enabled: Boolean,
        durationSeconds: Int,
    ) = result("discoverable")

    override suspend fun setProfileEnabled(
        address: String,
        profile: BluetoothProfileType,
        enabled: Boolean,
    ) = result("profile")

    private fun result(call: String): ActionResult {
        calls += call
        return ActionResult.Success
    }
}
