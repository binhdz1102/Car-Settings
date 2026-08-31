package com.android.car.settings.core.vehicle

import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.core.vehicle.internal.PlatformUxRestrictions
import com.android.car.settings.core.vehicle.internal.PlatformVehicleException
import com.android.car.settings.core.vehicle.internal.PlatformVehicleUxGateway
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultVehicleUxPolicy
    @Inject
    constructor(
        connection: DefaultVehiclePropertyConnection,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) : VehicleUxPolicy {
        private val mutableState =
            MutableStateFlow<VehicleUxPolicyState>(
                VehicleUxPolicyState.Unavailable(
                    VehiclePropertyError.ServiceUnavailable(
                        VehiclePropertyOperation.UX_RESTRICTIONS,
                    ),
                ),
            )
        override val state: StateFlow<VehicleUxPolicyState> = mutableState.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        private val updates: Flow<VehicleUxPolicyState> =
            connection.sessions.flatMapLatest { session ->
                val gateway = session?.uxRestrictions
                if (gateway == null) {
                    flowOf(
                        VehicleUxPolicyState.Unavailable(
                            VehiclePropertyError.ServiceUnavailable(
                                VehiclePropertyOperation.UX_RESTRICTIONS,
                                description = "Car UX restrictions service is unavailable",
                            ),
                        ),
                    )
                } else {
                    observeGateway(gateway).map(PlatformUxRestrictions::toPolicyState)
                }
            }

        init {
            connection.connect()
            CoroutineScope(SupervisorJob() + dispatcher).launch {
                updates.collect { mutableState.value = it }
            }
        }

        private fun observeGateway(gateway: PlatformVehicleUxGateway): Flow<PlatformUxRestrictions> =
            callbackFlow {
                try {
                    gateway.current()?.let { trySend(it) }
                    val subscription = gateway.subscribe { trySend(it) }
                    awaitClose {
                        try {
                            subscription.close()
                        } catch (_: RuntimeException) {
                            // Connection loss already updates the policy state.
                        }
                    }
                } catch (error: PlatformVehicleException) {
                    close(error)
                } catch (error: RuntimeException) {
                    close(error)
                }
            }
    }

private fun PlatformUxRestrictions.toPolicyState(): VehicleUxPolicyState =
    when {
        !requiresDistractionOptimization -> VehicleUxPolicyState.Unrestricted
        activeRestrictions == 0 ->
            VehicleUxPolicyState.DistractionOptimizationRequired(activeRestrictions)
        else -> VehicleUxPolicyState.Restricted(activeRestrictions)
    }
