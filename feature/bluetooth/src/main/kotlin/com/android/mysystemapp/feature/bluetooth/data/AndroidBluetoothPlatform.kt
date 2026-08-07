@file:Suppress("DEPRECATION")

package com.android.car.settings.feature.bluetooth.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.feature.bluetooth.domain.BluetoothAdapterInfo
import com.android.car.settings.feature.bluetooth.domain.BluetoothBondState
import com.android.car.settings.feature.bluetooth.domain.BluetoothConnectionState
import com.android.car.settings.feature.bluetooth.domain.BluetoothDeviceModel
import com.android.car.settings.feature.bluetooth.domain.BluetoothDeviceType
import com.android.car.settings.feature.bluetooth.domain.BluetoothProfileState
import com.android.car.settings.feature.bluetooth.domain.BluetoothProfileType
import com.android.car.settings.feature.bluetooth.domain.BluetoothRadioState
import com.android.car.settings.feature.bluetooth.domain.BluetoothState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AndroidBluetoothPlatform
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : BluetoothPlatform {
        private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val discoveredDevices = ConcurrentHashMap<String, DiscoveredDevice>()
        private val mutableState = MutableStateFlow(BluetoothState())
        private var selectedAddress: String? = null

        override val state: StateFlow<BluetoothState> = mutableState.asStateFlow()

        private val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device =
                                intent.getParcelableExtra<BluetoothDevice>(
                                    BluetoothDevice.EXTRA_DEVICE,
                                )
                            val rssi =
                                intent.getShortExtra(
                                    BluetoothDevice.EXTRA_RSSI,
                                    Short.MIN_VALUE,
                                )
                            device?.let {
                                discoveredDevices[it.address] =
                                    DiscoveredDevice(
                                        device = it,
                                        rssi =
                                            rssi
                                                .takeUnless { value -> value == Short.MIN_VALUE }
                                                ?.toInt(),
                                    )
                            }
                        }

                        BluetoothAdapter.ACTION_DISCOVERY_STARTED -> Unit
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> Unit
                        BluetoothAdapter.ACTION_STATE_CHANGED -> {
                            if (adapter?.state == BluetoothAdapter.STATE_OFF) {
                                discoveredDevices.clear()
                            }
                        }
                    }
                    scope.launch { refresh() }
                }
            }

        init {
            val filter =
                IntentFilter().apply {
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
                    addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothDevice.ACTION_NAME_CHANGED)
                    addAction(BluetoothDevice.ACTION_ALIAS_CHANGED)
                    addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                    addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    addAction(BluetoothDevice.ACTION_UUID)
                    addAction(ACTION_BATTERY_LEVEL_CHANGED)
                }
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            scope.launch { refresh() }
        }

        override suspend fun refresh(): ActionResult =
            execute {
                val localAdapter = requireNotNull(adapter) { "Bluetooth is not supported" }
                val bonded =
                    runCatching { localAdapter.bondedDevices.orEmpty() }
                        .getOrDefault(emptySet())
                        .map { it.toDomain() }
                        .sortedBy { it.alias.lowercase() }
                val available =
                    discoveredDevices.values
                        .filterNot { it.device.address in bonded.map(BluetoothDeviceModel::address) }
                        .map { it.device.toDomain(it.rssi) }
                        .sortedWith(
                            compareByDescending<BluetoothDeviceModel> { it.rssi ?: Int.MIN_VALUE }
                                .thenBy { it.alias.lowercase() },
                        )
                val selected =
                    (bonded + available).firstOrNull { it.address == selectedAddress }
                mutableState.value =
                    BluetoothState(
                        adapter =
                            BluetoothAdapterInfo(
                                name = runCatching { localAdapter.name }.getOrNull().orEmpty(),
                                address = runCatching { localAdapter.address }.getOrNull().orEmpty(),
                                radioState = localAdapter.state.toDomain(),
                                isDiscovering = localAdapter.isDiscovering,
                                isDiscoverable =
                                    localAdapter.scanMode ==
                                        BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE,
                                discoverableTimeoutSeconds =
                                    if (localAdapter.scanMode ==
                                        BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
                                    ) {
                                        120
                                    } else {
                                        0
                                    },
                            ),
                        pairedDevices = bonded,
                        availableDevices = available,
                        selectedDevice = selected,
                    )
            }

        override suspend fun setEnabled(enabled: Boolean): ActionResult =
            execute {
                val localAdapter = requireNotNull(adapter) { "Bluetooth is not supported" }
                val accepted = if (enabled) localAdapter.enable() else localAdapter.disable()
                check(accepted) { "The platform rejected the Bluetooth state change" }
            }

        override suspend fun startDiscovery(): ActionResult =
            execute {
                val localAdapter = requireNotNull(adapter)
                discoveredDevices.clear()
                if (localAdapter.isDiscovering) localAdapter.cancelDiscovery()
                check(localAdapter.startDiscovery()) {
                    "The platform rejected Bluetooth discovery"
                }
                refresh()
            }

        override suspend fun stopDiscovery(): ActionResult =
            execute {
                val localAdapter = requireNotNull(adapter)
                if (localAdapter.isDiscovering) {
                    check(localAdapter.cancelDiscovery()) { "Unable to stop discovery" }
                }
                refresh()
            }

        override suspend fun pair(address: String): ActionResult =
            execute {
                adapter?.takeIf(BluetoothAdapter::isDiscovering)?.cancelDiscovery()
                val device = getDevice(address)
                check(device.createBond()) { "Unable to start pairing with ${device.safeName()}" }
                selectedAddress = address
                refresh()
            }

        override suspend fun cancelPairing(address: String): ActionResult =
            invokeDeviceBoolean(address, "cancelBondProcess", "Unable to cancel pairing")

        override suspend fun unpair(address: String): ActionResult =
            invokeDeviceBoolean(address, "removeBond", "Unable to forget Bluetooth device")

        override suspend fun connect(address: String): ActionResult =
            invokeDeviceBoolean(address, "connect", "Unable to connect Bluetooth device")

        override suspend fun disconnect(address: String): ActionResult =
            invokeDeviceBoolean(address, "disconnect", "Unable to disconnect Bluetooth device")

        override suspend fun selectDevice(address: String): ActionResult =
            execute {
                getDevice(address)
                selectedAddress = address
                refresh()
            }

        override suspend fun renameDevice(
            address: String,
            alias: String,
        ): ActionResult =
            execute {
                require(alias.isNotBlank()) { "Device name cannot be empty" }
                val device = getDevice(address)
                val result =
                    BluetoothDevice::class.java
                        .getMethod("setAlias", String::class.java)
                        .invoke(device, alias.trim())
                checkReflectionResult(result) { "Unable to rename Bluetooth device" }
                refresh()
            }

        override suspend fun renameAdapter(name: String): ActionResult =
            execute {
                require(name.isNotBlank()) { "Bluetooth name cannot be empty" }
                check(requireNotNull(adapter).setName(name.trim())) {
                    "Unable to rename this device"
                }
                refresh()
            }

        override suspend fun setDiscoverable(
            enabled: Boolean,
            durationSeconds: Int,
        ): ActionResult =
            execute {
                val localAdapter = requireNotNull(adapter)
                val scanMode =
                    if (enabled) {
                        BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
                    } else {
                        BluetoothAdapter.SCAN_MODE_CONNECTABLE
                    }
                val methods = BluetoothAdapter::class.java.methods
                val timedMethod =
                    methods.firstOrNull {
                        it.name == "setScanMode" &&
                            it.parameterTypes.contentEquals(
                                arrayOf(
                                    Int::class.javaPrimitiveType,
                                    Int::class.javaPrimitiveType,
                                ),
                            )
                    }
                val simpleMethod =
                    methods.firstOrNull {
                        it.name == "setScanMode" &&
                            it.parameterTypes.contentEquals(
                                arrayOf(Int::class.javaPrimitiveType),
                            )
                    }
                val result =
                    when {
                        timedMethod != null ->
                            timedMethod.invoke(
                                localAdapter,
                                scanMode,
                                durationSeconds.coerceIn(1, 3_600),
                            )

                        simpleMethod != null -> simpleMethod.invoke(localAdapter, scanMode)
                        else -> error("Discoverable mode API is unavailable")
                    }
                checkReflectionResult(result) { "Unable to change discoverable mode" }
                refresh()
            }

        override suspend fun setProfileEnabled(
            address: String,
            profile: BluetoothProfileType,
            enabled: Boolean,
        ): ActionResult =
            try {
                withContext(dispatcher) {
                    val device = getDevice(address)
                    val proxy = getProfileProxy(profile.platformId)
                    try {
                        val policy =
                            if (enabled) {
                                CONNECTION_POLICY_ALLOWED
                            } else {
                                CONNECTION_POLICY_FORBIDDEN
                            }
                        val policyMethod =
                            proxy.javaClass.methods.firstOrNull {
                                it.name == "setConnectionPolicy" && it.parameterTypes.size == 2
                            }
                                ?: error("${profile.displayName} policy API is unavailable")
                        checkReflectionResult(policyMethod.invoke(proxy, device, policy)) {
                            "Unable to update ${profile.displayName} policy"
                        }

                        val action = if (enabled) "connect" else "disconnect"
                        val actionMethod =
                            proxy.javaClass.methods.firstOrNull {
                                it.name == action &&
                                    it.parameterTypes.contentEquals(
                                        arrayOf(BluetoothDevice::class.java),
                                    )
                            }
                        actionMethod?.let {
                            checkReflectionResult(it.invoke(proxy, device)) {
                                "Unable to $action ${profile.displayName}"
                            }
                        }
                    } finally {
                        adapter?.closeProfileProxy(profile.platformId, proxy)
                    }
                    refresh()
                    ActionResult.Success
                }
            } catch (throwable: Throwable) {
                fail(throwable)
            }

        private suspend fun getProfileProxy(profileId: Int): BluetoothProfile {
            val result = CompletableDeferred<BluetoothProfile>()
            val accepted =
                adapter?.getProfileProxy(
                    context,
                    object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(
                            profile: Int,
                            proxy: BluetoothProfile,
                        ) {
                            result.complete(proxy)
                        }

                        override fun onServiceDisconnected(profile: Int) = Unit
                    },
                    profileId,
                ) == true
            check(accepted) { "Bluetooth profile $profileId is unavailable" }
            return withTimeout(PROFILE_PROXY_TIMEOUT_MS) { result.await() }
        }

        private suspend fun invokeDeviceBoolean(
            address: String,
            methodName: String,
            failureMessage: String,
        ): ActionResult =
            execute {
                val device = getDevice(address)
                val method = BluetoothDevice::class.java.getMethod(methodName)
                checkReflectionResult(method.invoke(device)) { failureMessage }
                refresh()
            }

        private fun getDevice(address: String): BluetoothDevice {
            require(BluetoothAdapter.checkBluetoothAddress(address)) {
                "Invalid Bluetooth address"
            }
            return requireNotNull(adapter).getRemoteDevice(address)
        }

        private suspend fun execute(block: suspend () -> Unit): ActionResult =
            withContext(dispatcher) {
                try {
                    block()
                    ActionResult.Success
                } catch (throwable: Throwable) {
                    fail(throwable)
                }
            }

        private fun fail(throwable: Throwable): ActionResult.Failure {
            val message =
                throwable.cause?.message ?: throwable.message ?: "Unknown Bluetooth error"
            mutableState.value = mutableState.value.copy(lastError = message)
            return ActionResult.Failure(message, throwable)
        }
    }

private data class DiscoveredDevice(
    val device: BluetoothDevice,
    val rssi: Int?,
)

private fun BluetoothDevice.toDomain(rssi: Int? = null): BluetoothDeviceModel {
    val overallConnected = isConnectedCompat()
    val uuids = uuids.orEmpty().map { it.uuid.toString() }
    val profiles = supportedProfiles(uuids, bluetoothClass, overallConnected)
    return BluetoothDeviceModel(
        address = address,
        name = safeName(),
        alias = runCatching { alias }.getOrNull().takeUnless { it.isNullOrBlank() } ?: safeName(),
        bondState = bondState.toDomainBond(),
        connectionState =
            if (overallConnected) {
                BluetoothConnectionState.CONNECTED
            } else {
                BluetoothConnectionState.DISCONNECTED
            },
        deviceType = type.toDomainType(),
        bluetoothClass = bluetoothClass?.readableClass(),
        batteryLevel =
            runCatching {
                BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(this) as? Int
            }.getOrNull()
                ?.takeIf { it in 0..100 },
        rssi = rssi,
        uuids = uuids,
        profiles = profiles,
    )
}

private fun BluetoothDevice.safeName(): String = runCatching { name }.getOrNull().takeUnless { it.isNullOrBlank() } ?: address

private fun BluetoothDevice.isConnectedCompat(): Boolean =
    runCatching {
        BluetoothDevice::class.java.getMethod("isConnected").invoke(this) as? Boolean
    }.getOrNull() == true

private fun supportedProfiles(
    uuids: List<String>,
    bluetoothClass: BluetoothClass?,
    connected: Boolean,
): List<BluetoothProfileState> {
    val normalized = uuids.map(String::lowercase)
    val audio =
        bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
    val selected =
        buildSet {
            if (audio || normalized.any { it.contains("0000110b") || it.contains("0000110a") }) {
                add(BluetoothProfileType.A2DP)
            }
            if (audio || normalized.any { it.contains("00001108") || it.contains("0000111e") }) {
                add(BluetoothProfileType.HEADSET)
            }
            if (normalized.any { it.contains("00001124") }) add(BluetoothProfileType.HID_HOST)
            if (normalized.any { it.contains("00001115") || it.contains("00001116") }) {
                add(BluetoothProfileType.PAN)
            }
            if (normalized.any { it.contains("0000112f") }) {
                add(BluetoothProfileType.PBAP_CLIENT)
            }
            if (normalized.any { it.contains("00001132") }) {
                add(BluetoothProfileType.MAP_CLIENT)
            }
        }
    return selected.map { profile ->
        BluetoothProfileState(
            profile = profile,
            connectionState =
                if (connected) {
                    BluetoothConnectionState.CONNECTED
                } else {
                    BluetoothConnectionState.DISCONNECTED
                },
        )
    }
}

private fun Int.toDomain(): BluetoothRadioState =
    when (this) {
        BluetoothAdapter.STATE_OFF -> BluetoothRadioState.OFF
        BluetoothAdapter.STATE_TURNING_OFF -> BluetoothRadioState.TURNING_OFF
        BluetoothAdapter.STATE_TURNING_ON -> BluetoothRadioState.TURNING_ON
        BluetoothAdapter.STATE_ON -> BluetoothRadioState.ON
        else -> BluetoothRadioState.UNKNOWN
    }

private fun Int.toDomainBond(): BluetoothBondState =
    when (this) {
        BluetoothDevice.BOND_BONDED -> BluetoothBondState.BONDED
        BluetoothDevice.BOND_BONDING -> BluetoothBondState.BONDING
        else -> BluetoothBondState.NONE
    }

private fun Int.toDomainType(): BluetoothDeviceType =
    when (this) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> BluetoothDeviceType.CLASSIC
        BluetoothDevice.DEVICE_TYPE_LE -> BluetoothDeviceType.LOW_ENERGY
        BluetoothDevice.DEVICE_TYPE_DUAL -> BluetoothDeviceType.DUAL
        else -> BluetoothDeviceType.UNKNOWN
    }

private fun BluetoothClass.readableClass(): String =
    when (majorDeviceClass) {
        BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio / video"
        BluetoothClass.Device.Major.COMPUTER -> "Computer"
        BluetoothClass.Device.Major.HEALTH -> "Health"
        BluetoothClass.Device.Major.IMAGING -> "Imaging"
        BluetoothClass.Device.Major.MISC -> "Miscellaneous"
        BluetoothClass.Device.Major.NETWORKING -> "Networking"
        BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
        BluetoothClass.Device.Major.PHONE -> "Phone"
        BluetoothClass.Device.Major.TOY -> "Toy"
        BluetoothClass.Device.Major.WEARABLE -> "Wearable"
        else -> "Uncategorized"
    }

private inline fun checkReflectionResult(
    result: Any?,
    lazyMessage: () -> String,
) {
    when (result) {
        is Boolean -> check(result, lazyMessage)
        is Int -> check(result >= 0, lazyMessage)
        else -> Unit
    }
}

private const val ACTION_BATTERY_LEVEL_CHANGED =
    "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
private const val CONNECTION_POLICY_FORBIDDEN = 0
private const val CONNECTION_POLICY_ALLOWED = 100
private const val PROFILE_PROXY_TIMEOUT_MS = 10_000L
