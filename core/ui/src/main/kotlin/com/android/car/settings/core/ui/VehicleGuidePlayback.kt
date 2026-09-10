package com.android.car.settings.core.ui

import androidx.compose.runtime.Immutable

enum class VehicleGuidePhase {
    POSTER,
    PLAYING,
    STOPPED,
    COMPLETE,
}

enum class VehicleGuideIntent {
    Play,
    Stop,
    Replay,
    Complete,
    Reset,
}

@Immutable
data class VehicleGuidePlaybackState(
    val phase: VehicleGuidePhase = VehicleGuidePhase.POSTER,
    val progress: Float = 0f,
) {
    val isActionable: Boolean
        get() = phase != VehicleGuidePhase.POSTER
}

internal fun reduceVehicleGuidePlayback(
    state: VehicleGuidePlaybackState,
    intent: VehicleGuideIntent,
    policy: VehicleVisualPolicy,
): VehicleGuidePlaybackState {
    if (!policy.allowGuidePlayback && intent != VehicleGuideIntent.Reset) {
        return VehicleGuidePlaybackState()
    }
    return when (intent) {
        VehicleGuideIntent.Play,
        VehicleGuideIntent.Replay,
        -> VehicleGuidePlaybackState(VehicleGuidePhase.PLAYING, 0f)
        VehicleGuideIntent.Stop,
        VehicleGuideIntent.Reset,
        -> VehicleGuidePlaybackState(VehicleGuidePhase.STOPPED, 0f)
        VehicleGuideIntent.Complete ->
            VehicleGuidePlaybackState(VehicleGuidePhase.COMPLETE, 1f)
    }
}
