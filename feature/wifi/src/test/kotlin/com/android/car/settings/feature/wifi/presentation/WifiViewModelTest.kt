package com.android.car.settings.feature.wifi.presentation

import app.cash.turbine.test
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.wifi.domain.HotspotConfiguration
import com.android.car.settings.feature.wifi.domain.MeteredOverride
import com.android.car.settings.feature.wifi.domain.WifiCredentials
import com.android.car.settings.feature.wifi.domain.WifiNetwork
import com.android.car.settings.feature.wifi.domain.WifiRadioState
import com.android.car.settings.feature.wifi.domain.WifiRepository
import com.android.car.settings.feature.wifi.domain.WifiSecurity
import com.android.car.settings.feature.wifi.domain.WifiState
import com.android.car.settings.feature.wifi.domain.WifiUseCases
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
class WifiViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ViewModelWifiRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = ViewModelWifiRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `repository state is rendered`() =
        runTest(dispatcher) {
            val viewModel = WifiViewModel(WifiUseCases(repository))
            viewModel.uiState.test {
                awaitItem()
                repository.mutableState.value =
                    WifiState(radioState = WifiRadioState.ENABLED)
                assertThat(awaitItem().wifi.radioState).isEqualTo(WifiRadioState.ENABLED)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `failed action is exposed and can be cleared`() =
        runTest(dispatcher) {
            repository.result = ActionResult.Failure("denied")
            val viewModel = WifiViewModel(WifiUseCases(repository))
            viewModel.uiState.test {
                awaitItem()
                advanceUntilIdle()
                val failed = awaitItem()
                assertThat(failed.message).isEqualTo("denied")

                viewModel.clearMessage()
                assertThat(awaitItem().message).isNull()
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private class ViewModelWifiRepository : WifiRepository {
    val mutableState = MutableStateFlow(WifiState())
    override val state = mutableState
    var result: ActionResult = ActionResult.Success

    override suspend fun refresh() = result

    override suspend fun setWifiEnabled(enabled: Boolean) = result

    override suspend fun startScan() = result

    override suspend fun connect(
        network: WifiNetwork,
        credentials: WifiCredentials,
    ) = result

    override suspend fun addNetwork(
        ssid: String,
        security: WifiSecurity,
        credentials: WifiCredentials,
        hidden: Boolean,
    ) = result

    override suspend fun disconnect() = result

    override suspend fun forget(networkId: Int) = result

    override suspend fun setAutoJoin(
        networkId: Int,
        enabled: Boolean,
    ) = result

    override suspend fun setMeteredOverride(
        networkId: Int,
        override: MeteredOverride,
    ) = result

    override suspend fun setHotspotEnabled(enabled: Boolean) = result

    override suspend fun updateHotspotConfiguration(configuration: HotspotConfiguration) = result

    override suspend fun setScanAlwaysAvailable(enabled: Boolean) = result

    override suspend fun setWakeupEnabled(enabled: Boolean) = result

    override suspend fun setOpenNetworkNotificationEnabled(enabled: Boolean) = result

    override suspend fun setCellularFallbackEnabled(enabled: Boolean) = result

    override suspend fun setPersistentTetheringEnabled(enabled: Boolean) = result

    override suspend fun setMobileDataEnabled(enabled: Boolean) = result

    override suspend fun setDataRoamingEnabled(enabled: Boolean) = result

    override suspend fun setDefaultDataSubscription(subscriptionId: Int) = result
}
