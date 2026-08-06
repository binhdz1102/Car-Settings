package com.android.car.settings.feature.wifi.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

const val WIFI_ROUTE = "wifi"
private const val WIFI_DETAILS_ROUTE = "wifi/details"
const val WIFI_HOTSPOT_ROUTE = "wifi/hotspot"
const val WIFI_PREFERENCES_ROUTE = "wifi/preferences"

fun NavGraphBuilder.wifiGraph(
    navController: NavHostController,
    onBack: () -> Unit,
) {
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
        )
    }
    composable(WIFI_HOTSPOT_ROUTE) {
        WifiHotspotRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
        )
    }
    composable(WIFI_PREFERENCES_ROUTE) {
        WifiPreferencesRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
        )
    }
}
