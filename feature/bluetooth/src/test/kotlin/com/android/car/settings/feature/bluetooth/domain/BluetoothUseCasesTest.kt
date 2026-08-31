package com.android.car.settings.feature.bluetooth.domain

import com.android.car.settings.core.common.ActionResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BluetoothUseCasesTest {
    private val repository = RecordingBluetoothRepository()
    private val useCases = BluetoothUseCases(repository)

    @Test
    fun `all commands are delegated with their arguments`() =
        runTest {
            val address = "00:11:22:33:44:55"

            useCases.refresh()
            useCases.setEnabled(true)
            useCases.setUwbEnabled(true)
            useCases.startDiscovery()
            useCases.stopDiscovery()
            useCases.pair(address)
            useCases.cancelPairing(address)
            useCases.unpair(address)
            useCases.connect(address)
            useCases.disconnect(address)
            useCases.selectDevice(address)
            useCases.renameDevice(address, "Headset")
            useCases.renameAdapter("Vehicle")
            useCases.setDiscoverable(true, 180)
            useCases.setProfileEnabled(address, BluetoothProfileType.A2DP, true)

            assertThat(repository.calls)
                .containsExactly(
                    "refresh",
                    "enabled:true",
                    "uwb:true",
                    "startDiscovery",
                    "stopDiscovery",
                    "pair:$address",
                    "cancel:$address",
                    "unpair:$address",
                    "connect:$address",
                    "disconnect:$address",
                    "select:$address",
                    "rename:$address:Headset",
                    "adapterName:Vehicle",
                    "discoverable:true:180",
                    "profile:$address:A2DP:true",
                ).inOrder()
        }

    @Test
    fun `observe state exposes repository flow`() =
        runTest {
            val enabled =
                BluetoothState(adapter = BluetoothAdapterInfo(radioState = BluetoothRadioState.ON))
            repository.mutableState.value = enabled

            assertThat(useCases.observeState().first()).isEqualTo(enabled)
        }
}

private class RecordingBluetoothRepository : BluetoothRepository {
    val calls = mutableListOf<String>()
    val mutableState = MutableStateFlow(BluetoothState())
    override val state = mutableState

    override suspend fun refresh() = success("refresh")

    override suspend fun setBluetoothEnabled(enabled: Boolean) = success("enabled:$enabled")

    override suspend fun setUwbEnabled(enabled: Boolean) = success("uwb:$enabled")

    override suspend fun startDiscovery() = success("startDiscovery")

    override suspend fun stopDiscovery() = success("stopDiscovery")

    override suspend fun pair(address: String) = success("pair:$address")

    override suspend fun cancelPairing(address: String) = success("cancel:$address")

    override suspend fun unpair(address: String) = success("unpair:$address")

    override suspend fun connect(address: String) = success("connect:$address")

    override suspend fun disconnect(address: String) = success("disconnect:$address")

    override suspend fun selectDevice(address: String) = success("select:$address")

    override suspend fun renameDevice(
        address: String,
        alias: String,
    ) = success("rename:$address:$alias")

    override suspend fun renameAdapter(name: String) = success("adapterName:$name")

    override suspend fun setDiscoverable(
        enabled: Boolean,
        durationSeconds: Int,
    ) = success("discoverable:$enabled:$durationSeconds")

    override suspend fun setProfileEnabled(
        address: String,
        profile: BluetoothProfileType,
        enabled: Boolean,
    ) = success("profile:$address:$profile:$enabled")

    private fun success(call: String): ActionResult {
        calls += call
        return ActionResult.Success
    }
}
