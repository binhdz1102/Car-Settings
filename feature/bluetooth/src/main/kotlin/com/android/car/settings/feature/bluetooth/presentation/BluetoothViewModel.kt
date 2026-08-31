package com.android.car.settings.feature.bluetooth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.ActiveOperationTracker
import com.android.car.settings.feature.bluetooth.domain.BluetoothProfileType
import com.android.car.settings.feature.bluetooth.domain.BluetoothState
import com.android.car.settings.feature.bluetooth.domain.BluetoothUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BluetoothUiState(
    val bluetooth: BluetoothState = BluetoothState(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class BluetoothViewModel
    @Inject
    constructor(
        private val useCases: BluetoothUseCases,
    ) : ViewModel() {
        private val operations = ActiveOperationTracker()
        private val message = MutableStateFlow<String?>(null)

        val uiState: StateFlow<BluetoothUiState> =
            combine(useCases.observeState(), operations.isActive, message, ::BluetoothUiState)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = BluetoothUiState(),
                )

        init {
            execute { useCases.refresh() }
        }

        fun setEnabled(enabled: Boolean) = execute { useCases.setEnabled(enabled) }

        fun setUwbEnabled(enabled: Boolean) = execute { useCases.setUwbEnabled(enabled) }

        fun startDiscovery() = execute(showProgress = false) { useCases.startDiscovery() }

        fun stopDiscovery() = execute(showProgress = false) { useCases.stopDiscovery() }

        fun pair(address: String) = execute { useCases.pair(address) }

        fun cancelPairing(address: String) = execute { useCases.cancelPairing(address) }

        fun unpair(address: String) = execute { useCases.unpair(address) }

        fun connect(address: String) = execute { useCases.connect(address) }

        fun disconnect(address: String) = execute { useCases.disconnect(address) }

        fun selectDevice(address: String) =
            execute(showProgress = false) {
                useCases.selectDevice(address)
            }

        fun renameDevice(
            address: String,
            alias: String,
        ) = execute { useCases.renameDevice(address, alias) }

        fun renameAdapter(name: String) = execute { useCases.renameAdapter(name) }

        fun setDiscoverable(enabled: Boolean) = execute { useCases.setDiscoverable(enabled) }

        fun setProfileEnabled(
            address: String,
            profile: BluetoothProfileType,
            enabled: Boolean,
        ) = execute { useCases.setProfileEnabled(address, profile, enabled) }

        fun clearMessage() {
            message.value = null
        }

        private fun execute(
            showProgress: Boolean = true,
            block: suspend () -> ActionResult,
        ) {
            viewModelScope.launch {
                operations.track(enabled = showProgress) {
                    when (val result = block()) {
                        ActionResult.Success -> Unit
                        is ActionResult.Failure -> message.value = result.message
                    }
                }
            }
        }
    }
