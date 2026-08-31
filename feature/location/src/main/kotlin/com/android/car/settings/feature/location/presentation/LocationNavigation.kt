package com.android.car.settings.feature.location.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val LOCATION_ROUTE = "location"
const val LOCATION_APPS_ROUTE = "location/apps"

fun NavGraphBuilder.locationGraph(
    onBack: () -> Unit,
    onNavigateForward: (String) -> Unit,
) {
    composable(LOCATION_ROUTE) {
        LocationRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onAppPermissions = { onNavigateForward(LOCATION_APPS_ROUTE) },
        )
    }
    composable(LOCATION_APPS_ROUTE) {
        LocationAppsRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
        )
    }
}
