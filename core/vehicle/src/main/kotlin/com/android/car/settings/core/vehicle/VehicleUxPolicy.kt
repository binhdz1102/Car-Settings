package com.android.car.settings.core.vehicle

import kotlinx.coroutines.flow.StateFlow

sealed interface VehicleUxPolicyState {
    /** No distraction optimization is required; normally this is the parked state. */
    data object Unrestricted : VehicleUxPolicyState

    /** The activity must be distraction optimized, with baseline restrictions only. */
    data class DistractionOptimizationRequired(
        val activeRestrictions: Int,
    ) : VehicleUxPolicyState

    /** One or more concrete Car UX restriction flags are active. */
    data class Restricted(
        val activeRestrictions: Int,
    ) : VehicleUxPolicyState

    data class Unavailable(
        val error: VehiclePropertyError,
    ) : VehicleUxPolicyState
}

interface VehicleUxPolicy {
    val state: StateFlow<VehicleUxPolicyState>
}
