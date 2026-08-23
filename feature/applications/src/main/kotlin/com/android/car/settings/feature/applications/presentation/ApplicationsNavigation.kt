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
const val APPLICATION_PERMISSIONS_ROUTE = "applications/permissions"
const val PERMISSION_GROUP_ARGUMENT = "groupName"
const val PERMISSION_GROUP_ROUTE = "applications/permissions/group/{$PERMISSION_GROUP_ARGUMENT}"
const val APPLICATION_PERMISSIONS_DETAIL_ROUTE = "applications/permissions/app/{$APPLICATION_PACKAGE_ARGUMENT}"
const val UNUSED_APPLICATIONS_ROUTE = "applications/unused"
const val DEFAULT_APPLICATIONS_ROUTE = "applications/defaults"
const val OPENING_LINKS_ROUTE = "applications/defaults/opening-links"

fun applicationDetailsRoute(packageName: String): String = "applications/details/$packageName"

fun applicationStorageRoute(packageName: String): String = "applications/storage/$packageName"

fun specialAccessRoute(type: String): String = "applications/special-access/$type"

fun permissionGroupRoute(groupName: String): String = "applications/permissions/group/$groupName"

fun applicationPermissionsRoute(packageName: String): String = "applications/permissions/app/$packageName"

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
            onPermissions = { navController.navigate(APPLICATION_PERMISSIONS_ROUTE) },
            onUnusedApplications = { navController.navigate(UNUSED_APPLICATIONS_ROUTE) },
            onDefaultApps = { navController.navigate(DEFAULT_APPLICATIONS_ROUTE) },
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
            onPermissions = { navController.navigate(applicationPermissionsRoute(it)) },
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
    composable(APPLICATION_PERMISSIONS_ROUTE) {
        PermissionGroupsRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onGroup = { navController.navigate(permissionGroupRoute(it)) },
        )
    }
    composable(
        route = PERMISSION_GROUP_ROUTE,
        arguments = listOf(navArgument(PERMISSION_GROUP_ARGUMENT) { type = NavType.StringType }),
    ) {
        PermissionGroupApplicationsRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onApplication = { navController.navigate(applicationPermissionsRoute(it)) },
        )
    }
    composable(
        route = APPLICATION_PERMISSIONS_DETAIL_ROUTE,
        arguments = listOf(navArgument(APPLICATION_PACKAGE_ARGUMENT) { type = NavType.StringType }),
    ) {
        ApplicationPermissionsRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
        )
    }
    composable(UNUSED_APPLICATIONS_ROUTE) {
        UnusedApplicationsRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onApplication = { navController.navigate(applicationDetailsRoute(it)) },
        )
    }
    composable(DEFAULT_APPLICATIONS_ROUTE) {
        DefaultApplicationsRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onOpeningLinks = { navController.navigate(OPENING_LINKS_ROUTE) },
        )
    }
    composable(OPENING_LINKS_ROUTE) {
        OpeningLinksRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
        )
    }
}
