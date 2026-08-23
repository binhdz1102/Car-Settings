package com.android.car.settings.feature.search.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.android.car.settings.feature.search.domain.SearchDestination

const val SEARCH_ROUTE = "search"

fun NavGraphBuilder.searchGraph(
    onBack: () -> Unit,
    onDestination: (SearchDestination) -> Unit,
) {
    composable(SEARCH_ROUTE) {
        SearchRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onDestination = onDestination,
        )
    }
}
