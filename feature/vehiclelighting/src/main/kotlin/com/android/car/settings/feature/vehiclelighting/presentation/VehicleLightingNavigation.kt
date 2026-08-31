package com.android.car.settings.feature.vehiclelighting.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val VEHICLE_LIGHTING_ROUTE = "vehicle/lighting"

fun NavGraphBuilder.vehicleLightingGraph(onBack: () -> Unit) {
    composable(VEHICLE_LIGHTING_ROUTE) { VehicleLightingRoute(hiltViewModel(), onBack) }
}
