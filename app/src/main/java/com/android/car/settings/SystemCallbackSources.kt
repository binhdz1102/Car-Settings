package com.android.car.settings

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Display
import androidx.annotation.RequiresApi
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackEmitter
import com.b231001.bmaterial.runtime.localcallback.LocalCallback
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackConfig
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackKey
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackRegistration
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackRegistry
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackSource
import com.b231001.bmaterial.runtime.localcallback.localCallbackSource
import java.util.concurrent.Executor

/**
 * Immutable value copied at the platform callback boundary.
 *
 * Do not pass mutable callback-owned objects (for example SensorEvent.values) to asynchronous
 * consumers. Keeping one event shape also lets the benchmark compare callback families uniformly.
 */
data class SystemCallbackEvent(
    val source: String,
    val kind: String,
    val detail: String,
    val elapsedRealtimeNanos: Long = SystemClock.elapsedRealtimeNanos(),
)

/**
 * One physical system callback family. [newSource] must return a fresh adapter because the direct
 * baseline registers one independent callback per consumer.
 */
data class SystemCallbackScenario(
    val id: String,
    val displayName: String,
    val automotiveUse: String,
    val newSource: () -> LocalCallbackSource<SystemCallbackEvent>,
)

/**
 * Real Android/AAOS callback adapters used by the demo and device benchmark.
 *
 * The catalog owns the Car connection and callback HandlerThread. In production, put equivalent
 * objects in an application-scoped repository/DI component and keep one LocalCallbackKey per
 * physical registration configuration.
 */
class AutomotiveSystemCallbackCatalog(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val callbackThread = HandlerThread("system-callback-benchmark").apply { start() }
    private val callbackHandler = Handler(callbackThread.looper)
    private val callbackExecutor = Executor(callbackHandler::post)

    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val displayManager = appContext.getSystemService(DisplayManager::class.java)
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val car = Car.createCar(appContext)
    private val carPropertyManager =
        car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager

    val scenarios: List<SystemCallbackScenario> =
        buildList {
            add(
                SystemCallbackScenario(
                    id = "car-property",
                    displayName = "CarPropertyManager",
                    automotiveUse = "vehicle speed / VHAL telemetry",
                    newSource = {
                        carPropertySource(
                            manager = carPropertyManager,
                            propertyId = VehiclePropertyIds.PERF_VEHICLE_SPEED,
                            sampleRateHz = 10f,
                        )
                    },
                ),
            )
            add(
                SystemCallbackScenario(
                    id = "sensor",
                    displayName = "SensorManager",
                    automotiveUse = "motion, orientation and driving-context signals",
                    newSource = {
                        sensorSource(
                            manager = sensorManager,
                            sensor =
                                checkNotNull(
                                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                                ) { "Accelerometer is unavailable" },
                            samplingPeriodUs = 20_000,
                            handler = callbackHandler,
                        )
                    },
                ),
            )
            add(
                SystemCallbackScenario(
                    id = "connectivity",
                    displayName = "ConnectivityManager",
                    automotiveUse = "default-network and capability changes",
                    newSource = {
                        defaultNetworkSource(connectivityManager, callbackHandler)
                    },
                ),
            )
            add(
                SystemCallbackScenario(
                    id = "audio",
                    displayName = "AudioManager",
                    automotiveUse = "USB/Bluetooth audio device routing changes",
                    newSource = { audioDeviceSource(audioManager, callbackHandler) },
                ),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(
                    SystemCallbackScenario(
                        id = "wifi",
                        displayName = "WifiManager",
                        automotiveUse = "scan-result refresh for connectivity UI",
                        newSource = {
                            wifiScanResultsSource(wifiManager, callbackExecutor)
                        },
                    ),
                )
            }
            add(
                SystemCallbackScenario(
                    id = "bluetooth",
                    displayName = "BluetoothManager",
                    automotiveUse = "adapter state used by pairing and media UI",
                    newSource = {
                        bluetoothAdapterStateSource(appContext, bluetoothManager)
                    },
                ),
            )
            add(
                SystemCallbackScenario(
                    id = "display",
                    displayName = "DisplayManager",
                    automotiveUse = "cluster/passenger-display topology changes",
                    newSource = { displaySource(displayManager, callbackHandler) },
                ),
            )
        }

    override fun close() {
        car.disconnect()
        callbackThread.quitSafely()
    }
}

/**
 * Application/repository-scoped usage example.
 *
 * Create this owner once (for example through an application Singleton component), inject the
 * exposed LocalCallbacks into screens/ViewModels, and close it only when that owner is permanently
 * destroyed. Creating one hub per ViewModel would recreate the system registrations and defeat
 * sharing.
 */
@RequiresApi(Build.VERSION_CODES.R)
class AutomotiveLocalCallbackHub(context: Context) : AutoCloseable {
    private val catalog = AutomotiveSystemCallbackCatalog(context)
    private val registry = LocalCallbackRegistry()
    private val sources = catalog.scenarios.associateBy(SystemCallbackScenario::id)

    private val carSpeedKey =
        LocalCallbackKey.create<SystemCallbackEvent>("car-property:speed:global:10hz")
    private val accelerometerKey =
        LocalCallbackKey.create<SystemCallbackEvent>("sensor:accelerometer:50hz")
    private val defaultNetworkKey =
        LocalCallbackKey.create<SystemCallbackEvent>("connectivity:default-network")
    private val audioDevicesKey =
        LocalCallbackKey.create<SystemCallbackEvent>("audio:device-events")
    private val wifiScanKey =
        LocalCallbackKey.create<SystemCallbackEvent>("wifi:scan-results")
    private val bluetoothStateKey =
        LocalCallbackKey.create<SystemCallbackEvent>("bluetooth:adapter-state")
    private val displaysKey =
        LocalCallbackKey.create<SystemCallbackEvent>("display:topology")

    val carSpeed: LocalCallback<SystemCallbackEvent> =
        state(carSpeedKey, "car-property")
    val accelerometer: LocalCallback<SystemCallbackEvent> =
        state(accelerometerKey, "sensor")
    val defaultNetwork: LocalCallback<SystemCallbackEvent> =
        state(defaultNetworkKey, "connectivity")
    val bluetoothState: LocalCallback<SystemCallbackEvent> =
        state(bluetoothStateKey, "bluetooth")

    val audioDeviceEvents: LocalCallback<SystemCallbackEvent> =
        events(audioDevicesKey, "audio")
    val wifiScanEvents: LocalCallback<SystemCallbackEvent> =
        events(wifiScanKey, "wifi")
    val displayEvents: LocalCallback<SystemCallbackEvent> =
        events(displaysKey, "display")

    private fun state(
        key: LocalCallbackKey<SystemCallbackEvent>,
        scenarioId: String,
    ): LocalCallback<SystemCallbackEvent> =
        registry.getOrCreate(
            key = key,
            config =
                LocalCallbackConfig.state(
                    sourceBufferCapacity = 256,
                    subscriberBufferCapacity = 64,
                    stopTimeoutMillis = 5_000L,
                ),
            sourceFactory = scenario(scenarioId).newSource,
        )

    private fun events(
        key: LocalCallbackKey<SystemCallbackEvent>,
        scenarioId: String,
    ): LocalCallback<SystemCallbackEvent> =
        registry.getOrCreate(
            key = key,
            config =
                LocalCallbackConfig.events(
                    sourceBufferCapacity = 256,
                    subscriberBufferCapacity = 64,
                ),
            sourceFactory = scenario(scenarioId).newSource,
        )

    private fun scenario(id: String): SystemCallbackScenario =
        checkNotNull(sources[id]) { "Unknown callback scenario: $id" }

    override fun close() {
        registry.close()
        catalog.close()
    }
}

@Suppress("DEPRECATION")
fun carPropertySource(
    manager: CarPropertyManager,
    propertyId: Int,
    sampleRateHz: Float,
): LocalCallbackSource<SystemCallbackEvent> =
    localCallbackSource(
        callbackFactory = { emitter ->
            object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    emitter.emit(
                        SystemCallbackEvent(
                            source = "CarPropertyManager",
                            kind = "change",
                            detail =
                                "property=${value.propertyId}, area=${value.areaId}, " +
                                    "value=${value.value}",
                        ),
                    )
                }

                override fun onErrorEvent(propertyId: Int, areaId: Int) {
                    emitter.emit(
                        SystemCallbackEvent(
                            source = "CarPropertyManager",
                            kind = "error",
                            detail = "property=$propertyId, area=$areaId",
                        ),
                    )
                }
            }
        },
        register = { callback ->
            check(manager.registerCallback(callback, propertyId, sampleRateHz)) {
                "CarPropertyManager rejected property=$propertyId at ${sampleRateHz}Hz"
            }
        },
        unregister = { callback -> manager.unregisterCallback(callback, propertyId) },
    )

fun sensorSource(
    manager: SensorManager,
    sensor: Sensor,
    samplingPeriodUs: Int,
    handler: Handler,
): LocalCallbackSource<SystemCallbackEvent> =
    localCallbackSource(
        callbackFactory = { emitter ->
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val copiedValues = event.values.copyOf()
                    emitter.emit(
                        SystemCallbackEvent(
                            source = "SensorManager",
                            kind = "sample",
                            detail =
                                "type=${event.sensor.type}, accuracy=${event.accuracy}, " +
                                    "values=${copiedValues.joinToString(limit = 3)}",
                        ),
                    )
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    emitter.emit(
                        SystemCallbackEvent(
                            source = "SensorManager",
                            kind = "accuracy",
                            detail = "type=${sensor.type}, accuracy=$accuracy",
                        ),
                    )
                }
            }
        },
        register = { listener ->
            check(manager.registerListener(listener, sensor, samplingPeriodUs, handler)) {
                "SensorManager rejected ${sensor.name}"
            }
        },
        unregister = manager::unregisterListener,
    )

sealed interface AudioDeviceEvent {
    val devices: List<AudioDeviceInfo>

    data class Added(override val devices: List<AudioDeviceInfo>) : AudioDeviceEvent

    data class Removed(override val devices: List<AudioDeviceInfo>) : AudioDeviceEvent
}

fun audioDeviceSource(
    manager: AudioManager,
    handler: Handler,
): LocalCallbackSource<SystemCallbackEvent> =
    localCallbackSource(
        callbackFactory = { emitter ->
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                    emitAudioEvent(emitter, AudioDeviceEvent.Added(addedDevices.toList()))
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                    emitAudioEvent(emitter, AudioDeviceEvent.Removed(removedDevices.toList()))
                }
            }
        },
        register = { callback -> manager.registerAudioDeviceCallback(callback, handler) },
        unregister = manager::unregisterAudioDeviceCallback,
    )

private fun emitAudioEvent(
    emitter: LocalCallbackEmitter<SystemCallbackEvent>,
    event: AudioDeviceEvent,
) {
    emitter.emit(
        SystemCallbackEvent(
            source = "AudioManager",
            kind = if (event is AudioDeviceEvent.Added) "added" else "removed",
            detail = event.devices.joinToString { "${it.id}:${it.type}" },
        ),
    )
}

fun defaultNetworkSource(
    manager: ConnectivityManager,
    handler: Handler,
): LocalCallbackSource<SystemCallbackEvent> =
    localCallbackSource(
        callbackFactory = { emitter ->
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    emitter.emit(networkEvent("available", network))
                }

                override fun onLost(network: Network) {
                    emitter.emit(networkEvent("lost", network))
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    emitter.emit(
                        SystemCallbackEvent(
                            source = "ConnectivityManager",
                            kind = "capabilities",
                            detail =
                                "network=$network, transports=" +
                                    listOf(
                                        NetworkCapabilities.TRANSPORT_WIFI,
                                        NetworkCapabilities.TRANSPORT_CELLULAR,
                                        NetworkCapabilities.TRANSPORT_ETHERNET,
                                    ).filter(networkCapabilities::hasTransport),
                        ),
                    )
                }
            }
        },
        register = { callback -> manager.registerDefaultNetworkCallback(callback, handler) },
        unregister = manager::unregisterNetworkCallback,
    )

private fun networkEvent(kind: String, network: Network) =
    SystemCallbackEvent(
        source = "ConnectivityManager",
        kind = kind,
        detail = "network=$network",
    )

@RequiresApi(Build.VERSION_CODES.R)
fun wifiScanResultsSource(
    manager: WifiManager,
    executor: Executor,
): LocalCallbackSource<SystemCallbackEvent> =
    localCallbackSource(
        callbackFactory = { emitter ->
            object : WifiManager.ScanResultsCallback() {
                override fun onScanResultsAvailable() {
                    emitter.emit(
                        SystemCallbackEvent(
                            source = "WifiManager",
                            kind = "scan-results",
                            detail = "scan results changed",
                        ),
                    )
                }
            }
        },
        register = { callback -> manager.registerScanResultsCallback(executor, callback) },
        unregister = manager::unregisterScanResultsCallback,
    )

fun bluetoothAdapterStateSource(
    context: Context,
    manager: BluetoothManager,
): LocalCallbackSource<SystemCallbackEvent> =
    LocalCallbackSource { emitter ->
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val state =
                        intent.getIntExtra(
                            BluetoothAdapter.EXTRA_STATE,
                            manager.adapter?.state ?: BluetoothAdapter.STATE_OFF,
                        )
                    emitter.emit(
                        SystemCallbackEvent(
                            source = "BluetoothManager",
                            kind = "adapter-state",
                            detail = "state=$state",
                        ),
                    )
                }
            }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        LocalCallbackRegistration { context.unregisterReceiver(receiver) }
    }

fun displaySource(
    manager: DisplayManager,
    handler: Handler,
): LocalCallbackSource<SystemCallbackEvent> =
    localCallbackSource(
        callbackFactory = { emitter ->
            object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) {
                    emitter.emit(displayEvent(manager, "added", displayId))
                }

                override fun onDisplayRemoved(displayId: Int) {
                    emitter.emit(displayEvent(manager, "removed", displayId))
                }

                override fun onDisplayChanged(displayId: Int) {
                    emitter.emit(displayEvent(manager, "changed", displayId))
                }
            }
        },
        register = { listener -> manager.registerDisplayListener(listener, handler) },
        unregister = manager::unregisterDisplayListener,
    )

private fun displayEvent(
    manager: DisplayManager,
    kind: String,
    displayId: Int,
): SystemCallbackEvent {
    val display: Display? = manager.getDisplay(displayId)
    return SystemCallbackEvent(
        source = "DisplayManager",
        kind = kind,
        detail = "id=$displayId, name=${display?.name ?: "removed"}",
    )
}
