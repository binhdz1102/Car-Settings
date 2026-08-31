package com.android.car.settings.core.vehicle

import android.car.Car
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide CarService lifecycle shared by vehicle and system-settings integrations.
 *
 * The app used to create one Car connection per feature (display, sound, profiles, watchdog,
 * cockpit and vehicle properties). Apart from wasting binder resources, that made reconnect and
 * safe fallback behaviour disagree between screens. This provider owns one connection and fans
 * lifecycle events out to consumers; consumers only acquire the manager they actually need.
 */
@Singleton
class CarServiceProvider
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val applicationContext = context.applicationContext
        private val callbackThread = HandlerThread("MySystemApp-CarService").apply { start() }
        private val callbackHandler = Handler(callbackThread.looper)
        private val listeners = CopyOnWriteArraySet<CarServiceListener>()
        private val lock = Any()
        private var started = false

        @Volatile private var connectedCar: Car? = null

        init {
            connectIfNeeded()
        }

        /** Register for ready/not-ready lifecycle changes. Closing is idempotent. */
        fun register(listener: CarServiceListener): CarServiceRegistration {
            val closed = AtomicBoolean(false)
            listeners += listener
            connectedCar?.let { car ->
                callbackHandler.post {
                    if (!closed.get()) listener.onCarLifecycleChanged(car, true)
                }
            }
            connectIfNeeded()
            return CarServiceRegistration {
                if (closed.compareAndSet(false, true)) listeners -= listener
            }
        }

        /** Returns the current ready Car, or null while the service/capability is unavailable. */
        fun current(): Car? = connectedCar

        /** Resolve a manager from the shared ready connection without creating another Car. */
        fun <T> manager(managerClass: Class<T>): T? = runCatching { connectedCar?.getCarManager(managerClass) }.getOrNull()

        private fun connectIfNeeded() {
            synchronized(lock) {
                if (started) return
                started = true
                runCatching {
                    Car.createCar(
                        applicationContext,
                        callbackHandler,
                        Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                    ) { car, ready ->
                        connectedCar = car.takeIf { ready }
                        listeners.forEach { listener ->
                            runCatching { listener.onCarLifecycleChanged(car, ready) }
                        }
                    }
                }.onFailure {
                    // Keep the provider alive with a null current car. Consumers expose their
                    // existing unsupported/error fallback and can retry via process restart.
                    connectedCar = null
                }
            }
        }
    }

fun interface CarServiceListener {
    fun onCarLifecycleChanged(
        car: Car,
        ready: Boolean,
    )
}

fun interface CarServiceRegistration {
    fun close()
}
