package com.android.car.settings.feature.wifi.domain

enum class WifiRadioState {
    DISABLED,
    DISABLING,
    ENABLING,
    ENABLED,
    UNKNOWN,
}

enum class WifiSecurity {
    OPEN,
    OWE,
    WEP,
    WPA2_PERSONAL,
    WPA3_PERSONAL,
    WPA2_WPA3_PERSONAL,
    EAP,
    UNKNOWN,
}

enum class WifiEapMethod {
    PEAP,
    TLS,
    TTLS,
    PWD,
}

data class WifiCredentials(
    val password: String = "",
    val identity: String = "",
    val anonymousIdentity: String = "",
    val eapMethod: WifiEapMethod = WifiEapMethod.PEAP,
)

data class WifiNetwork(
    val key: String,
    val networkId: Int?,
    val ssid: String,
    val bssid: String? = null,
    val security: WifiSecurity,
    val signalLevel: Int,
    val rssi: Int,
    val isSaved: Boolean,
    val isConnected: Boolean,
    val isHidden: Boolean = false,
)

data class WifiConnectionDetails(
    val network: WifiNetwork,
    val macAddress: String? = null,
    val ipv4Address: String? = null,
    val ipv6Addresses: List<String> = emptyList(),
    val gateway: String? = null,
    val dnsServers: List<String> = emptyList(),
    val subnetMask: String? = null,
    val linkSpeedMbps: Int? = null,
    val frequencyMhz: Int? = null,
    val securityLabel: String = network.security.name,
    val isAutoJoinEnabled: Boolean = true,
    val meteredOverride: MeteredOverride = MeteredOverride.AUTOMATIC,
)

enum class MeteredOverride {
    AUTOMATIC,
    METERED,
    NOT_METERED,
}

enum class HotspotSecurity {
    OPEN,
    WPA2_PERSONAL,
    WPA3_PERSONAL,
    WPA2_WPA3_PERSONAL,
}

enum class HotspotBand {
    AUTO,
    GHZ_2_4,
    GHZ_5,
    DUAL,
}

data class HotspotConfiguration(
    val ssid: String = "",
    val password: String = "",
    val security: HotspotSecurity = HotspotSecurity.WPA2_PERSONAL,
    val band: HotspotBand = HotspotBand.AUTO,
    val autoShutdownEnabled: Boolean = true,
    val maxClients: Int = 0,
)

data class HotspotStatus(
    val isEnabled: Boolean = false,
    val isTransitioning: Boolean = false,
    val clients: Int = 0,
    val failureReason: String? = null,
    val configuration: HotspotConfiguration = HotspotConfiguration(),
    val supports5Ghz: Boolean = false,
    val supportsDualBand: Boolean = false,
)

data class WifiPreferences(
    val scanAlwaysAvailable: Boolean = false,
    val wakeupEnabled: Boolean = false,
    val openNetworkNotificationEnabled: Boolean = false,
)

data class WifiState(
    val radioState: WifiRadioState = WifiRadioState.UNKNOWN,
    val isScanning: Boolean = false,
    val connectedNetwork: WifiNetwork? = null,
    val availableNetworks: List<WifiNetwork> = emptyList(),
    val savedNetworks: List<WifiNetwork> = emptyList(),
    val connectionDetails: WifiConnectionDetails? = null,
    val hotspot: HotspotStatus = HotspotStatus(),
    val preferences: WifiPreferences = WifiPreferences(),
    val lastError: String? = null,
)
