package com.android.car.settings

/**
 * Allows at most one Back pop from a given navigation entry.
 *
 * Compose screen handlers and the navigation host can observe the same hardware event during a
 * destination hand-off. Keeping the originating entry in flight prevents that event from popping
 * a second pane/destination before the new entry has become current.
 */
internal class NavigationBackGate {
    private var inFlightEntryId: String? = null

    fun tryStart(entryId: String?): Boolean {
        if (entryId == null || inFlightEntryId == entryId) return false
        inFlightEntryId = entryId
        return true
    }

    fun onDestinationChanged(entryId: String?) {
        if (entryId != inFlightEntryId) inFlightEntryId = null
    }

    fun release() {
        inFlightEntryId = null
    }
}
