package com.android.car.settings.core.ui

/**
 * Shared motion contract for driver-facing settings surfaces.
 *
 * Keeping durations in one place prevents a focus ring and state banner from feeling like
 * unrelated animations. The values are deliberately short enough for rotary use: motion
 * communicates a state change without delaying the next CCP detent.
 */
object VehicleMotionTokens {
    const val FOCUS_DURATION_MILLIS: Int = 180
    const val STATE_DURATION_MILLIS: Int = 240
    const val CONTENT_ENTER_DURATION_MILLIS: Int = 220
    const val CONTENT_EXIT_DURATION_MILLIS: Int = 160
}
