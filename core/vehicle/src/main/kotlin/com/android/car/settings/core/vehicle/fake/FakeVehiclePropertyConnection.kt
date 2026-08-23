package com.android.car.settings.core.vehicle.fake

import com.android.car.settings.core.vehicle.VehicleConnectionState
import com.android.car.settings.core.vehicle.VehiclePropertyConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeVehiclePropertyConnection(
    initialState: VehicleConnectionState = VehicleConnectionState.Connected(generation = 1),
) : VehiclePropertyConnection {
    private val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<VehicleConnectionState> = mutableState.asStateFlow()

    var connectCalls: Int = 0
        private set
    var reconnectCalls: Int = 0
        private set

    override fun connect() {
        connectCalls += 1
    }

    override fun reconnect() {
        reconnectCalls += 1
        mutableState.value = VehicleConnectionState.Connecting(attempt = 1)
    }

    fun setState(state: VehicleConnectionState) {
        mutableState.value = state
    }
}
