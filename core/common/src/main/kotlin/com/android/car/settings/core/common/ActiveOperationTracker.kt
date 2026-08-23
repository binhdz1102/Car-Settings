package com.android.car.settings.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tracks overlapping UI operations without allowing an early completion to clear busy state. */
class ActiveOperationTracker {
    private val lock = Any()
    private var activeOperationCount = 0
    private val mutableIsActive = MutableStateFlow(false)

    val isActive: StateFlow<Boolean> = mutableIsActive.asStateFlow()

    suspend fun <T> track(
        enabled: Boolean = true,
        block: suspend () -> T,
    ): T {
        if (!enabled) return block()

        beginOperation()
        return try {
            block()
        } finally {
            endOperation()
        }
    }

    private fun beginOperation() {
        synchronized(lock) {
            activeOperationCount += 1
            mutableIsActive.value = true
        }
    }

    private fun endOperation() {
        synchronized(lock) {
            check(activeOperationCount > 0) { "No active operation to finish" }
            activeOperationCount -= 1
            mutableIsActive.value = activeOperationCount > 0
        }
    }
}
