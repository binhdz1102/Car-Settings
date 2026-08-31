package com.android.car.settings.core.vehicle

import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.core.vehicle.internal.PlatformCarConnector
import com.android.car.settings.core.vehicle.internal.PlatformCarRegistration
import com.android.car.settings.core.vehicle.internal.PlatformVehicleSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

internal data class VehicleReconnectPolicy(
    val initialDelayMillis: Long = 250,
    val maxDelayMillis: Long = 5_000,
    val multiplier: Double = 2.0,
    val connectionTimeoutMillis: Long = 5_000,
) {
    init {
        require(initialDelayMillis >= 0)
        require(maxDelayMillis >= initialDelayMillis)
        require(multiplier >= 1.0)
        require(connectionTimeoutMillis > 0)
    }

    fun delayForAttempt(attempt: Int): Long {
        val exponent = (attempt - 2).coerceAtLeast(0)
        return (initialDelayMillis * multiplier.pow(exponent)).toLong().coerceAtMost(maxDelayMillis)
    }
}

@Singleton
internal class DefaultVehiclePropertyConnection
    @Inject
    constructor(
        private val connector: PlatformCarConnector,
        @IoDispatcher dispatcher: CoroutineDispatcher,
        private val reconnectPolicy: VehicleReconnectPolicy,
    ) : VehiclePropertyConnection {
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val commands = Channel<Command>(Channel.UNLIMITED)
        private val mutableState =
            MutableStateFlow<VehicleConnectionState>(VehicleConnectionState.Disconnected())
        private val mutableSession = MutableStateFlow<PlatformVehicleSession?>(null)

        private var registration: PlatformCarRegistration? = null
        private var retryJob: Job? = null
        private var timeoutJob: Job? = null
        private var activeToken = 0L
        private var generation = 0L

        override val state: StateFlow<VehicleConnectionState> = mutableState.asStateFlow()
        internal val sessions: StateFlow<PlatformVehicleSession?> = mutableSession.asStateFlow()

        init {
            scope.launch {
                for (command in commands) process(command)
            }
            connect()
        }

        override fun connect() {
            commands.trySend(Command.Connect(force = false))
        }

        override fun reconnect() {
            commands.trySend(Command.Connect(force = true))
        }

        internal fun closeForTest() {
            commands.trySend(Command.Close)
        }

        private suspend fun process(command: Command) {
            when (command) {
                is Command.Connect -> handleConnect(command.force)
                is Command.Open -> open(command.attempt)
                is Command.Connected -> handleConnected(command)
                is Command.Disconnected -> handleDisconnected(command)
                is Command.ConnectionFailed -> handleConnectionFailed(command)
                is Command.ConnectionTimedOut -> handleConnectionTimedOut(command)
                Command.Close -> closeConnection()
            }
        }

        @Suppress("ComplexCondition")
        private suspend fun handleConnect(force: Boolean) {
            if (!force &&
                (registration != null || retryJob?.isActive == true || mutableSession.value != null)
            ) {
                return
            }
            invalidateCurrentTransport()
            mutableState.value = VehicleConnectionState.Disconnected()
            open(attempt = 1)
        }

        private suspend fun open(attempt: Int) {
            retryJob = null
            activeToken += 1
            val token = activeToken
            mutableState.value = VehicleConnectionState.Connecting(attempt)
            registration =
                try {
                    connector.open(
                        object : PlatformCarConnector.Listener {
                            override fun onConnected(session: PlatformVehicleSession) {
                                commands.trySend(Command.Connected(token, session))
                            }

                            override fun onDisconnected(description: String?) {
                                commands.trySend(Command.Disconnected(token, attempt, description))
                            }

                            override fun onConnectionFailed(description: String) {
                                commands.trySend(Command.ConnectionFailed(token, attempt, description))
                            }
                        },
                    )
                } catch (error: RuntimeException) {
                    scheduleRetry(attempt + 1, error.message ?: error.javaClass.simpleName)
                    return
                }
            timeoutJob =
                scope.launch {
                    delay(reconnectPolicy.connectionTimeoutMillis)
                    commands.send(Command.ConnectionTimedOut(token, attempt))
                }
        }

        private fun handleConnected(command: Command.Connected) {
            if (command.token != activeToken) return
            timeoutJob?.cancel()
            timeoutJob = null
            retryJob?.cancel()
            retryJob = null
            mutableSession.value = command.session
            generation += 1
            mutableState.value = VehicleConnectionState.Connected(generation)
        }

        private suspend fun handleDisconnected(command: Command.Disconnected) {
            if (command.token != activeToken) return
            val description = command.description ?: "CarService connection was lost"
            invalidateCurrentTransport()
            scheduleRetry(command.attempt + 1, description)
        }

        private suspend fun handleConnectionFailed(command: Command.ConnectionFailed) {
            if (command.token != activeToken) return
            invalidateCurrentTransport()
            scheduleRetry(command.attempt + 1, command.description)
        }

        private suspend fun handleConnectionTimedOut(command: Command.ConnectionTimedOut) {
            if (command.token != activeToken || mutableSession.value != null) return
            invalidateCurrentTransport()
            scheduleRetry(command.attempt + 1, "Timed out while connecting to CarService")
        }

        private fun scheduleRetry(
            nextAttempt: Int,
            description: String,
        ) {
            val delayMillis = reconnectPolicy.delayForAttempt(nextAttempt)
            val reason =
                VehiclePropertyError.ServiceUnavailable(
                    operation = VehiclePropertyOperation.CONNECT,
                    description = description,
                )
            mutableState.value =
                VehicleConnectionState.RetryScheduled(
                    attempt = nextAttempt,
                    delayMillis = delayMillis,
                    reason = reason,
                )
            retryJob =
                scope.launch {
                    delay(delayMillis)
                    commands.send(Command.Open(nextAttempt))
                }
        }

        private suspend fun closeConnection() {
            invalidateCurrentTransport()
            mutableState.value = VehicleConnectionState.Disconnected()
            commands.close()
            scope.coroutineContext[Job]?.cancel()
        }

        private fun invalidateCurrentTransport() {
            activeToken += 1
            timeoutJob?.cancel()
            timeoutJob = null
            retryJob?.cancel()
            retryJob = null
            mutableSession.value = null
            val current = registration
            registration = null
            try {
                current?.close()
            } catch (_: RuntimeException) {
                // The binder is already gone. State still has to transition and retry.
            }
        }

        private sealed interface Command {
            data class Connect(
                val force: Boolean,
            ) : Command

            data class Open(
                val attempt: Int,
            ) : Command

            data class Connected(
                val token: Long,
                val session: PlatformVehicleSession,
            ) : Command

            data class Disconnected(
                val token: Long,
                val attempt: Int,
                val description: String?,
            ) : Command

            data class ConnectionFailed(
                val token: Long,
                val attempt: Int,
                val description: String,
            ) : Command

            data class ConnectionTimedOut(
                val token: Long,
                val attempt: Int,
            ) : Command

            data object Close : Command
        }
    }
