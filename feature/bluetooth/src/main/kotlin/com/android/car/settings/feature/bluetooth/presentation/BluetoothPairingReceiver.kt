@file:Suppress("DEPRECATION")

package com.android.car.settings.feature.bluetooth.presentation

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BluetoothPairingReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != BluetoothDevice.ACTION_PAIRING_REQUEST) return
        val activityIntent =
            Intent(context, BluetoothPairingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtras(intent)
            }
        context.startActivity(activityIntent)
        if (isOrderedBroadcast) abortBroadcast()
    }
}
