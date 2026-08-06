package com.android.car.settings.feature.hvac.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val HVAC_ROUTE = "hvac"

fun NavGraphBuilder.hvacGraph(onBack: () -> Unit) {
    composable(HVAC_ROUTE) {
        HvacRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
        )
    }
}
