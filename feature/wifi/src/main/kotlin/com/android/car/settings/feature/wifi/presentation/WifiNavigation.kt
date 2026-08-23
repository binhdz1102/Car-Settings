package com.android.car.settings.feature.wifi.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

const val WIFI_ROUTE = "wifi"
const val NETWORK_INTERNET_ROUTE = "network-internet"
private const val WIFI_DETAILS_ROUTE = "wifi/details"
const val WIFI_HOTSPOT_ROUTE = "wifi/hotspot"
const val WIFI_PREFERENCES_ROUTE = "wifi/preferences"
const val MOBILE_NETWORK_ROUTE = "network-internet/mobile"
const val WIFI_QR_ROUTE = "wifi/qr/{kind}"

fun NavGraphBuilder.wifiGraph(
    navController: NavHostController,
    onBack: () -> Unit,
) {
    composable(NETWORK_INTERNET_ROUTE) {
        NetworkInternetRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onWifi = { navController.navigate(WIFI_ROUTE) },
            onHotspot = { navController.navigate(WIFI_HOTSPOT_ROUTE) },
            onMobileNetwork = { navController.navigate(MOBILE_NETWORK_ROUTE) },
            onPreferences = { navController.navigate(WIFI_PREFERENCES_ROUTE) },
        )
    }
    composable(WIFI_ROUTE) {
        val viewModel: WifiViewModel = hiltViewModel()
        WifiRoute(
            viewModel = viewModel,
            onBack = onBack,
            onDetails = { navController.navigate(WIFI_DETAILS_ROUTE) },
            onHotspot = { navController.navigate(WIFI_HOTSPOT_ROUTE) },
            onPreferences = { navController.navigate(WIFI_PREFERENCES_ROUTE) },
        )
    }
    composable(WIFI_DETAILS_ROUTE) {
        WifiDetailsRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onShareQr = { navController.navigate(WIFI_QR_ROUTE.replace("{kind}", "network")) },
        )
    }
    composable(WIFI_HOTSPOT_ROUTE) {
        WifiHotspotRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onShareQr = { navController.navigate(WIFI_QR_ROUTE.replace("{kind}", "hotspot")) },
        )
    }
    composable(WIFI_PREFERENCES_ROUTE) {
        WifiPreferencesRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
        )
    }
    composable(MOBILE_NETWORK_ROUTE) {
        MobileNetworkRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
        )
    }
    composable(WIFI_QR_ROUTE) { entry ->
        WifiQrRoute(
            viewModel = hiltViewModel(),
            kind = entry.arguments?.getString("kind").orEmpty(),
            onBack = navController::popBackStack,
        )
    }
}
