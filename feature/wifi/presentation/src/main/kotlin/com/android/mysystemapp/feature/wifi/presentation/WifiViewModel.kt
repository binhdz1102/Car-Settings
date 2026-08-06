package com.android.car.settings.feature.wifi.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.wifi.domain.HotspotConfiguration
import com.android.car.settings.feature.wifi.domain.MeteredOverride
import com.android.car.settings.feature.wifi.domain.WifiCredentials
import com.android.car.settings.feature.wifi.domain.WifiNetwork
import com.android.car.settings.feature.wifi.domain.WifiSecurity
import com.android.car.settings.feature.wifi.domain.WifiState
import com.android.car.settings.feature.wifi.domain.WifiUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WifiUiState(
    val wifi: WifiState = WifiState(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class WifiViewModel
    @Inject
    constructor(
        private val useCases: WifiUseCases,
    ) : ViewModel() {
        private val working = MutableStateFlow(false)
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<WifiUiState> =
            combine(useCases.observeState(), working, message, ::WifiUiState)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = WifiUiState(),
                )

        init {
            execute { useCases.refresh() }
        }

        fun setWifiEnabled(enabled: Boolean) = execute { useCases.setEnabled(enabled) }

        fun scan() = execute(showProgress = false) { useCases.scan() }

        fun connect(
            network: WifiNetwork,
            credentials: WifiCredentials = WifiCredentials(),
        ) = execute { useCases.connect(network, credentials) }

        fun addNetwork(
            ssid: String,
            security: WifiSecurity,
            credentials: WifiCredentials,
            hidden: Boolean,
        ) = execute { useCases.addNetwork(ssid, security, credentials, hidden) }

        fun disconnect() = execute { useCases.disconnect() }

        fun forget(networkId: Int) = execute { useCases.forget(networkId) }

        fun setAutoJoin(
            networkId: Int,
            enabled: Boolean,
        ) = execute { useCases.setAutoJoin(networkId, enabled) }

        fun setMeteredOverride(
            networkId: Int,
            override: MeteredOverride,
        ) = execute { useCases.setMeteredOverride(networkId, override) }

        fun setHotspotEnabled(enabled: Boolean) = execute { useCases.setHotspotEnabled(enabled) }

        fun updateHotspotConfiguration(configuration: HotspotConfiguration) = execute { useCases.updateHotspotConfiguration(configuration) }

        fun setScanAlwaysAvailable(enabled: Boolean) = execute { useCases.setScanAlwaysAvailable(enabled) }

        fun setWakeupEnabled(enabled: Boolean) = execute { useCases.setWakeupEnabled(enabled) }

        fun setOpenNetworkNotificationEnabled(enabled: Boolean) = execute { useCases.setOpenNetworkNotificationEnabled(enabled) }

        fun clearMessage() {
            message.value = null
        }

        private fun execute(
            showProgress: Boolean = true,
            block: suspend () -> ActionResult,
        ) {
            viewModelScope.launch {
                if (showProgress) working.value = true
                when (val result = block()) {
                    ActionResult.Success -> Unit
                    is ActionResult.Failure -> message.value = result.message
                }
                if (showProgress) working.value = false
            }
        }
    }
