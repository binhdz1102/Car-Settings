package com.android.car.settings.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Direct-boot aware broadcast receiver for storage unmount events.
 */
class StorageUnmountReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        Timber.d("StorageUnmountReceiver.onReceive intent=%s", intent)
    }
}
