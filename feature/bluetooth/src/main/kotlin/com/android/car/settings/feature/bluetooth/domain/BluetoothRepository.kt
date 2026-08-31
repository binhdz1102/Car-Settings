package com.android.car.settings.feature.bluetooth.domain

import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.flow.Flow

interface BluetoothRepository {
    val state: Flow<BluetoothState>

    suspend fun refresh(): ActionResult

    suspend fun setBluetoothEnabled(enabled: Boolean): ActionResult

    suspend fun setUwbEnabled(enabled: Boolean): ActionResult

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
        durationSeconds: Int = 120,
    ): ActionResult

    suspend fun setProfileEnabled(
        address: String,
        profile: BluetoothProfileType,
        enabled: Boolean,
    ): ActionResult
}
