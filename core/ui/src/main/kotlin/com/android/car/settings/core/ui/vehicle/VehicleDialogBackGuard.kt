package com.android.car.settings.core.ui

import android.os.SystemClock

/**
 * Protects the parent activity from a duplicate Back dispatched while a native rotary dialog
 * window is being removed. The guard is bounded so a later, intentional Back is unaffected.
 */
object VehicleDialogBackGuard {
    private const val DIALOG_HANDOFF_WINDOW_MS = 5_000L

    // API 37 can deliver the leaked parent Back several seconds after Dialog.onDismiss while
    // the automotive task transition is completing. Keep this bounded but longer than that
    // platform hand-off; normal Compose Back callbacks remain above the activity fallback.
    private const val DUPLICATE_BACK_WINDOW_MS = 6_000L
    private val lock = Any()
    private var dialogVisible: Boolean = false
    private var suppressUntilUptime: Long = 0L

    /** Arms the hand-off guard before a native dialog can receive Back. */
    fun armForShownDialog() {
        synchronized(lock) {
            dialogVisible = true
            suppressUntilUptime = SystemClock.uptimeMillis() + DIALOG_HANDOFF_WINDOW_MS
        }
    }

    /** Arms the duplicate-event guard after the dialog has been dismissed. */
    fun armForDismissedDialog() {
        synchronized(lock) {
            dialogVisible = false
            suppressUntilUptime = SystemClock.uptimeMillis() + DUPLICATE_BACK_WINDOW_MS
        }
    }

    /** Returns true only when the next activity-level Back belongs to the dialog hand-off. */
    fun consumePending(): Boolean =
        synchronized(lock) {
            val now = SystemClock.uptimeMillis()
            if (dialogVisible) {
                if (now <= suppressUntilUptime) {
                    true
                } else {
                    dialogVisible = false
                    suppressUntilUptime = 0L
                    false
                }
            } else if (now > suppressUntilUptime) {
                suppressUntilUptime = 0L
                false
            } else {
                suppressUntilUptime = 0L
                true
            }
        }
}
