package com.android.car.settings.feature.doorcontrol.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val DOOR_CONTROL_ROUTE = "vehicle/door-control"

fun NavGraphBuilder.doorControlGraph(onBack: () -> Unit) {
    composable(DOOR_CONTROL_ROUTE) { DoorControlRoute(hiltViewModel(), onBack) }
}
