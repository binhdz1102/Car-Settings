package com.android.car.settings.core.ui

import androidx.compose.runtime.Immutable
import com.android.car.settings.core.vehicle.VehicleUxPolicyState

@Immutable
data class VehicleVisualPolicy(
    val allowPreviewTransition: Boolean,
    val allowGuide: Boolean,
    val allowGuidePlayback: Boolean,
    val reason: String? = null,
)

fun VehicleUxPolicyState.toVehicleVisualPolicy(): VehicleVisualPolicy =
    when (this) {
        VehicleUxPolicyState.Unrestricted ->
            VehicleVisualPolicy(
                allowPreviewTransition = true,
                allowGuide = true,
                allowGuidePlayback = true,
            )
        is VehicleUxPolicyState.DistractionOptimizationRequired,
        is VehicleUxPolicyState.Restricted,
        ->
            VehicleVisualPolicy(
                allowPreviewTransition = false,
                allowGuide = false,
                allowGuidePlayback = false,
                reason = "Unavailable while driving",
            )
        is VehicleUxPolicyState.Unavailable ->
            VehicleVisualPolicy(
                allowPreviewTransition = false,
                allowGuide = false,
                allowGuidePlayback = false,
                reason = "Vehicle UX policy unavailable",
            )
    }
