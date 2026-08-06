package com.android.car.settings.feature.display.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

const val DISPLAY_ROUTE = "display"
const val DATE_TIME_ROUTE = "display/date-time"
const val TIME_ZONE_ROUTE = "display/date-time/time-zone"

fun NavGraphBuilder.displayGraph(
    navController: NavHostController,
    onBack: () -> Unit,
) {
    composable(DISPLAY_ROUTE) {
        DisplayRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onDateTime = { navController.navigate(DATE_TIME_ROUTE) },
        )
    }
    composable(DATE_TIME_ROUTE) {
        DateTimeRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onTimeZone = { navController.navigate(TIME_ZONE_ROUTE) },
        )
    }
    composable(TIME_ZONE_ROUTE) {
        TimeZoneRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
        )
    }
}
