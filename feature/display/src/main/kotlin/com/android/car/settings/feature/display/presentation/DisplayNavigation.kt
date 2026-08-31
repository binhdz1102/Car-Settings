package com.android.car.settings.feature.display.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val DISPLAY_ROUTE = "display"
const val DATE_TIME_ROUTE = "display/date-time"
const val TIME_ZONE_ROUTE = "display/date-time/time-zone"

fun NavGraphBuilder.displayGraph(
    onBack: () -> Unit,
    onNavigateForward: (String) -> Unit,
) {
    composable(DISPLAY_ROUTE) {
        DisplayRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onDateTime = { onNavigateForward(DATE_TIME_ROUTE) },
        )
    }
    composable(DATE_TIME_ROUTE) {
        DateTimeRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onTimeZone = { onNavigateForward(TIME_ZONE_ROUTE) },
        )
    }
    composable(TIME_ZONE_ROUTE) {
        TimeZoneRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
        )
    }
}
