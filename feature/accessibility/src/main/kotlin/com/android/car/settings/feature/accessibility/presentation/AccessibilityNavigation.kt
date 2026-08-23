package com.android.car.settings.feature.accessibility.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val ACCESSIBILITY_ROUTE = "accessibility"

fun NavGraphBuilder.accessibilityGraph(onBack: () -> Unit) {
    composable(ACCESSIBILITY_ROUTE) {
        AccessibilityRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
        )
    }
}
