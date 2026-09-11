package com.android.car.settings.qc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Direct-boot aware broadcast receiver for Quick Controls toggle actions.
 */
class SettingsQCBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        Timber.d("SettingsQCBroadcastReceiver.onReceive intent=%s", intent)
    }
}
