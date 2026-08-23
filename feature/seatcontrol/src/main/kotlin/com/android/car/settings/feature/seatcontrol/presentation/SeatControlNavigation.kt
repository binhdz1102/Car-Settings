package com.android.car.settings.feature.seatcontrol.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val SEAT_CONTROL_ROUTE = "vehicle/seat-control"

fun NavGraphBuilder.seatControlGraph(onBack: () -> Unit) {
    composable(SEAT_CONTROL_ROUTE) { SeatControlRoute(hiltViewModel(), onBack) }
}
