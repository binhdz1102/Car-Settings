package com.android.car.settings.feature.bluetooth.domain

enum class BluetoothRadioState {
    OFF,
    TURNING_OFF,
    TURNING_ON,
    ON,
    UNKNOWN,
}

enum class BluetoothBondState {
    NONE,
    BONDING,
    BONDED,
}

enum class BluetoothConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
}

enum class BluetoothDeviceType {
    CLASSIC,
    LOW_ENERGY,
    DUAL,
    UNKNOWN,
}

enum class BluetoothProfileType(
    val platformId: Int,
    val displayName: String,
) {
    HEADSET(1, "Phone calls"),
    A2DP(2, "Media audio"),
    HID_HOST(4, "Input device"),
    PAN(5, "Internet access"),
    PBAP_CLIENT(17, "Contact sharing"),
    MAP_CLIENT(18, "Text messages"),
}

enum class BluetoothProfilePolicy {
    FORBIDDEN,
    ALLOWED,
    UNKNOWN,
}

data class BluetoothProfileState(
    val profile: BluetoothProfileType,
    val connectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED,
    val policy: BluetoothProfilePolicy = BluetoothProfilePolicy.UNKNOWN,
) {
    /**
     * PAN does not expose a policy getter on this platform. For such profiles, an active
     * connection is the only trustworthy positive signal available to the UI.
     */
    val isEnabled: Boolean
        get() =
            when (policy) {
                BluetoothProfilePolicy.ALLOWED -> true
                BluetoothProfilePolicy.FORBIDDEN -> false
                BluetoothProfilePolicy.UNKNOWN ->
                    connectionState != BluetoothConnectionState.DISCONNECTED
            }
}

data class BluetoothDeviceModel(
    val address: String,
    val name: String,
    val alias: String = name,
    val bondState: BluetoothBondState = BluetoothBondState.NONE,
    val connectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED,
    val deviceType: BluetoothDeviceType = BluetoothDeviceType.UNKNOWN,
    val bluetoothClass: String? = null,
    val batteryLevel: Int? = null,
    val rssi: Int? = null,
    val uuids: List<String> = emptyList(),
    val profiles: List<BluetoothProfileState> = emptyList(),
)

data class BluetoothAdapterInfo(
    val name: String = "",
    val address: String = "",
    val radioState: BluetoothRadioState = BluetoothRadioState.UNKNOWN,
    val isDiscovering: Boolean = false,
    val isDiscoverable: Boolean = false,
    val discoverableTimeoutSeconds: Int = 0,
)

data class UwbState(
    val isSupported: Boolean = false,
    val isEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class BluetoothState(
    val adapter: BluetoothAdapterInfo = BluetoothAdapterInfo(),
    val pairedDevices: List<BluetoothDeviceModel> = emptyList(),
    val availableDevices: List<BluetoothDeviceModel> = emptyList(),
    val selectedDevice: BluetoothDeviceModel? = null,
    val uwb: UwbState = UwbState(),
    val lastError: String? = null,
)
