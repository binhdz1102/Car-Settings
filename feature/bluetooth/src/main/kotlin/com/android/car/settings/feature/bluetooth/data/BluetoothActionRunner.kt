package com.android.car.settings.feature.bluetooth.data

import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.CancellationException

internal suspend fun runBluetoothAction(
    block: suspend () -> Unit,
    onFailure: (Throwable) -> ActionResult.Failure,
): ActionResult =
    try {
        block()
        ActionResult.Success
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (throwable: Throwable) {
        onFailure(throwable)
    }
