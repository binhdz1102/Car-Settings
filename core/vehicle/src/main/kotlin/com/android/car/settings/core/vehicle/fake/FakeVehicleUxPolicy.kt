package com.android.car.settings.core.vehicle.fake

import com.android.car.settings.core.vehicle.VehicleUxPolicy
import com.android.car.settings.core.vehicle.VehicleUxPolicyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeVehicleUxPolicy(
    initialState: VehicleUxPolicyState = VehicleUxPolicyState.Unrestricted,
) : VehicleUxPolicy {
    private val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<VehicleUxPolicyState> = mutableState.asStateFlow()

    fun setState(state: VehicleUxPolicyState) {
        mutableState.value = state
    }
}
