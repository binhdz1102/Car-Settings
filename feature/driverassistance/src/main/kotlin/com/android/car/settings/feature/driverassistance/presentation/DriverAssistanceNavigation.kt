package com.android.car.settings.feature.driverassistance.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val DRIVER_ASSISTANCE_ROUTE = "vehicle/driver-assistance"

fun NavGraphBuilder.driverAssistanceGraph(onBack: () -> Unit) {
    composable(DRIVER_ASSISTANCE_ROUTE) {
        DriverAssistanceRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
}
