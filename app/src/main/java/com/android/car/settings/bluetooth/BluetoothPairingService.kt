package com.android.car.settings.bluetooth

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import timber.log.Timber

/**
 * Direct-boot aware service stub for background Bluetooth pairing orchestration.
 */
class BluetoothPairingService : Service() {

    private val binder = Binder()

    override fun onBind(intent: Intent?): IBinder {
        Timber.d("BluetoothPairingService bound with intent=%s", intent)
        return binder
    }
}
