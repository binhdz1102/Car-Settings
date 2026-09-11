package com.android.car.settings.setupservice

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import timber.log.Timber

/**
 * Direct-boot aware service stub for lockscreen setup during initial setup wizard / CarService.
 */
class InitialLockSetupService : Service() {
    private val binder = Binder()

    override fun onBind(intent: Intent?): IBinder {
        Timber.d("InitialLockSetupService bound with intent=%s", intent)
        return binder
    }
}
