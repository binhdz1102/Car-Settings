package com.android.car.settings.feature.bluetooth.presentation

import app.cash.turbine.test
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.bluetooth.domain.BluetoothAdapterInfo
import com.android.car.settings.feature.bluetooth.domain.BluetoothProfileType
import com.android.car.settings.feature.bluetooth.domain.BluetoothRadioState
import com.android.car.settings.feature.bluetooth.domain.BluetoothRepository
import com.android.car.settings.feature.bluetooth.domain.BluetoothState
import com.android.car.settings.feature.bluetooth.domain.BluetoothUseCases
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ViewModelBluetoothRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = ViewModelBluetoothRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `repository state is rendered`() =
        runTest(dispatcher) {
            val viewModel = BluetoothViewModel(BluetoothUseCases(repository))
            viewModel.uiState.test {
                awaitItem()
                repository.mutableState.value =
                    BluetoothState(
                        adapter =
                            BluetoothAdapterInfo(
                                name = "Vehicle",
                                radioState = BluetoothRadioState.ON,
                            ),
                    )
                val enabled = awaitItem()
                assertThat(enabled.bluetooth.adapter.name).isEqualTo("Vehicle")
                assertThat(enabled.bluetooth.adapter.radioState).isEqualTo(BluetoothRadioState.ON)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `failed action is exposed and can be cleared`() =
        runTest(dispatcher) {
            repository.result = ActionResult.Failure("not allowed")
            val viewModel = BluetoothViewModel(BluetoothUseCases(repository))
            viewModel.uiState.test {
                awaitItem()
                advanceUntilIdle()
                assertThat(awaitItem().message).isEqualTo("not allowed")
                viewModel.clearMessage()
                assertThat(awaitItem().message).isNull()
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private class ViewModelBluetoothRepository : BluetoothRepository {
    val mutableState = MutableStateFlow(BluetoothState())
    override val state = mutableState
    var result: ActionResult = ActionResult.Success

    override suspend fun refresh() = result

    override suspend fun setBluetoothEnabled(enabled: Boolean) = result

    override suspend fun setUwbEnabled(enabled: Boolean) = result

    override suspend fun startDiscovery() = result

    override suspend fun stopDiscovery() = result

    override suspend fun pair(address: String) = result

    override suspend fun cancelPairing(address: String) = result

    override suspend fun unpair(address: String) = result

    override suspend fun connect(address: String) = result

    override suspend fun disconnect(address: String) = result

    override suspend fun selectDevice(address: String) = result

    override suspend fun renameDevice(
        address: String,
        alias: String,
    ) = result

    override suspend fun renameAdapter(name: String) = result

    override suspend fun setDiscoverable(
        enabled: Boolean,
        durationSeconds: Int,
    ) = result

    override suspend fun setProfileEnabled(
        address: String,
        profile: BluetoothProfileType,
        enabled: Boolean,
    ) = result
}
