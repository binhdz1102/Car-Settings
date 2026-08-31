package com.android.car.settings.feature.wifi.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val WIFI_ROUTE = "wifi"
const val NETWORK_INTERNET_ROUTE = "network-internet"
private const val WIFI_DETAILS_ROUTE = "wifi/details"
const val WIFI_HOTSPOT_ROUTE = "wifi/hotspot"
const val WIFI_PREFERENCES_ROUTE = "wifi/preferences"
const val MOBILE_NETWORK_ROUTE = "network-internet/mobile"
const val WIFI_QR_ROUTE = "wifi/qr/{kind}"

fun NavGraphBuilder.wifiGraph(
    onBack: () -> Unit,
    onNavigateForward: (String) -> Unit,
) {
    composable(NETWORK_INTERNET_ROUTE) {
        NetworkInternetRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onWifi = { onNavigateForward(WIFI_ROUTE) },
            onHotspot = { onNavigateForward(WIFI_HOTSPOT_ROUTE) },
            onMobileNetwork = { onNavigateForward(MOBILE_NETWORK_ROUTE) },
            onPreferences = { onNavigateForward(WIFI_PREFERENCES_ROUTE) },
        )
    }
    composable(WIFI_ROUTE) {
        val viewModel: WifiViewModel = hiltViewModel()
        WifiRoute(
            viewModel = viewModel,
            onBack = onBack,
            onDetails = { onNavigateForward(WIFI_DETAILS_ROUTE) },
            onHotspot = { onNavigateForward(WIFI_HOTSPOT_ROUTE) },
            onPreferences = { onNavigateForward(WIFI_PREFERENCES_ROUTE) },
        )
    }
    composable(WIFI_DETAILS_ROUTE) {
        WifiDetailsRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onShareQr = { onNavigateForward(WIFI_QR_ROUTE.replace("{kind}", "network")) },
        )
    }
    composable(WIFI_HOTSPOT_ROUTE) {
        WifiHotspotRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onShareQr = { onNavigateForward(WIFI_QR_ROUTE.replace("{kind}", "hotspot")) },
        )
    }
    composable(WIFI_PREFERENCES_ROUTE) {
        WifiPreferencesRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
        )
    }
    composable(MOBILE_NETWORK_ROUTE) {
        MobileNetworkRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
        )
    }
    composable(WIFI_QR_ROUTE) { entry ->
        WifiQrRoute(
            viewModel = hiltViewModel(),
            kind = entry.arguments?.getString("kind").orEmpty(),
            onBack = onBack,
        )
    }
}
