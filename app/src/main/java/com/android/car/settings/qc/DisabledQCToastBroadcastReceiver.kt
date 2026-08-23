package com.android.car.settings.qc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Direct-boot aware broadcast receiver for displaying toast when disabled QC items are clicked.
 */
class DisabledQCToastBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Timber.d("DisabledQCToastBroadcastReceiver.onReceive intent=%s", intent)
    }
}
