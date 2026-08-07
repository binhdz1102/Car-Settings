package com.android.car.settings.feature.applications.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

const val APPLICATIONS_ROUTE = "applications"
const val ALL_APPLICATIONS_ROUTE = "applications/all"
const val APPLICATION_PACKAGE_ARGUMENT = "packageName"
const val APPLICATION_DETAILS_ROUTE = "applications/details/{$APPLICATION_PACKAGE_ARGUMENT}"
const val APPLICATION_STORAGE_ROUTE = "applications/storage/{$APPLICATION_PACKAGE_ARGUMENT}"
const val SPECIAL_APP_ACCESS_ROUTE = "applications/special-access"
const val SPECIAL_ACCESS_TYPE_ARGUMENT = "accessType"
const val SPECIAL_ACCESS_LIST_ROUTE = "applications/special-access/{$SPECIAL_ACCESS_TYPE_ARGUMENT}"
const val PERFORMANCE_IMPACTING_APPS_ROUTE = "applications/performance-impacting"

fun applicationDetailsRoute(packageName: String): String = "applications/details/$packageName"

fun applicationStorageRoute(packageName: String): String = "applications/storage/$packageName"

fun specialAccessRoute(type: String): String = "applications/special-access/$type"

fun NavGraphBuilder.applicationsGraph(
    navController: NavHostController,
    onBack: () -> Unit,
) {
    composable(APPLICATIONS_ROUTE) {
        ApplicationsRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onAllApplications = { navController.navigate(ALL_APPLICATIONS_ROUTE) },
            onApplication = { navController.navigate(applicationDetailsRoute(it)) },
            onSpecialAccess = { navController.navigate(SPECIAL_APP_ACCESS_ROUTE) },
            onPerformanceImpactingApps = { navController.navigate(PERFORMANCE_IMPACTING_APPS_ROUTE) },
        )
    }
    composable(ALL_APPLICATIONS_ROUTE) {
        AllApplicationsRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onApplication = { navController.navigate(applicationDetailsRoute(it)) },
        )
    }
    composable(
        route = APPLICATION_DETAILS_ROUTE,
        arguments = listOf(navArgument(APPLICATION_PACKAGE_ARGUMENT) { type = NavType.StringType }),
    ) {
        ApplicationDetailsRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onStorage = { navController.navigate(applicationStorageRoute(it)) },
        )
    }
    composable(
        route = APPLICATION_STORAGE_ROUTE,
        arguments = listOf(navArgument(APPLICATION_PACKAGE_ARGUMENT) { type = NavType.StringType }),
    ) {
        ApplicationStorageRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
        )
    }
    composable(SPECIAL_APP_ACCESS_ROUTE) {
        SpecialAppAccessRoute(
            onBack = navController::popBackStack,
            onSpecialAccessType = { navController.navigate(specialAccessRoute(it.name)) },
        )
    }
    composable(
        route = SPECIAL_ACCESS_LIST_ROUTE,
        arguments = listOf(navArgument(SPECIAL_ACCESS_TYPE_ARGUMENT) { type = NavType.StringType }),
    ) {
        SpecialAccessListRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
        )
    }
    composable(PERFORMANCE_IMPACTING_APPS_ROUTE) {
        PerformanceImpactingApplicationsRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onApplication = { navController.navigate(applicationDetailsRoute(it)) },
        )
    }
}
