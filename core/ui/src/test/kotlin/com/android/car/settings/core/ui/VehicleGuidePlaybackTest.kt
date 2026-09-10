package com.android.car.settings.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleGuidePlaybackTest {
    private val enabledPolicy =
        VehicleVisualPolicy(
            allowPreviewTransition = true,
            allowGuide = true,
            allowGuidePlayback = true,
        )

    @Test
    fun play_startsOnlyWhenPolicyAllowsPlayback() {
        val initial = VehicleGuidePlaybackState()

        val playing = reduceVehicleGuidePlayback(initial, VehicleGuideIntent.Play, enabledPolicy)
        val blocked =
            reduceVehicleGuidePlayback(
                initial,
                VehicleGuideIntent.Play,
                enabledPolicy.copy(allowGuidePlayback = false),
            )

        assertEquals(VehicleGuidePhase.PLAYING, playing.phase)
        assertEquals(0f, playing.progress)
        assertEquals(VehicleGuidePhase.POSTER, blocked.phase)
        assertFalse(blocked.isActionable)
    }

    @Test
    fun stop_replay_and_complete_haveDeterministicFrames() {
        val playing =
            VehicleGuidePlaybackState(
                phase = VehicleGuidePhase.PLAYING,
                progress = .45f,
            )

        val stopped = reduceVehicleGuidePlayback(playing, VehicleGuideIntent.Stop, enabledPolicy)
        val replayed = reduceVehicleGuidePlayback(stopped, VehicleGuideIntent.Replay, enabledPolicy)
        val complete =
            reduceVehicleGuidePlayback(
                playing.copy(progress = .99f),
                VehicleGuideIntent.Complete,
                enabledPolicy,
            )

        assertEquals(VehicleGuidePhase.STOPPED, stopped.phase)
        assertEquals(0f, stopped.progress)
        assertEquals(VehicleGuidePhase.PLAYING, replayed.phase)
        assertEquals(VehicleGuidePhase.COMPLETE, complete.phase)
        assertEquals(1f, complete.progress)
        assertTrue(complete.isActionable)
    }
}
