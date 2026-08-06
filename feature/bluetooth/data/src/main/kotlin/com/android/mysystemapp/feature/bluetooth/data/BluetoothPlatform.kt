package com.android.car.settings.feature.bluetooth.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.bluetooth.domain.BluetoothProfileType
import com.android.car.settings.feature.bluetooth.domain.BluetoothState
import kotlinx.coroutines.flow.StateFlow

internal interface BluetoothPlatform {
    val state: StateFlow<BluetoothState>

    suspend fun refresh(): ActionResult

    suspend fun setEnabled(enabled: Boolean): ActionResult

    suspend fun startDiscovery(): ActionResult

    suspend fun stopDiscovery(): ActionResult

    suspend fun pair(address: String): ActionResult

    suspend fun cancelPairing(address: String): ActionResult

    suspend fun unpair(address: String): ActionResult

    suspend fun connect(address: String): ActionResult

    suspend fun disconnect(address: String): ActionResult

    suspend fun selectDevice(address: String): ActionResult

    suspend fun renameDevice(
        address: String,
        alias: String,
    ): ActionResult

    suspend fun renameAdapter(name: String): ActionResult

    suspend fun setDiscoverable(
        enabled: Boolean,
        durationSeconds: Int,
    ): ActionResult

    suspend fun setProfileEnabled(
        address: String,
        profile: BluetoothProfileType,
        enabled: Boolean,
    ): ActionResult
}
