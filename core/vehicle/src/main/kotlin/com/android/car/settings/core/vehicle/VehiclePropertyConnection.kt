package com.android.car.settings.core.vehicle

import kotlinx.coroutines.flow.StateFlow

sealed interface VehicleConnectionState {
    data class Disconnected(
        val reason: VehiclePropertyError.ServiceUnavailable? = null,
    ) : VehicleConnectionState

    data class Connecting(
        val attempt: Int,
    ) : VehicleConnectionState

    data class RetryScheduled(
        val attempt: Int,
        val delayMillis: Long,
        val reason: VehiclePropertyError.ServiceUnavailable,
    ) : VehicleConnectionState

    data class Connected(
        val generation: Long,
    ) : VehicleConnectionState
}

interface VehiclePropertyConnection {
    val state: StateFlow<VehicleConnectionState>

    /** Idempotently starts the shared Car connection. */
    fun connect()

    /** Drops the current transport and starts a fresh connection attempt. */
    fun reconnect()
}
