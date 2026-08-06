package com.android.car.settings.feature.notifications.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

const val NOTIFICATIONS_ROUTE = "notifications"
private const val NOTIFICATION_APP_ARGUMENT = "packageName"
private const val NOTIFICATION_APP_DETAILS_ROUTE = "notifications/app/{$NOTIFICATION_APP_ARGUMENT}"

private fun notificationAppDetailsRoute(packageName: String) = "notifications/app/$packageName"

fun NavGraphBuilder.notificationsGraph(navController: NavHostController, onBack: () -> Unit) {
    composable(NOTIFICATIONS_ROUTE) {
        NotificationsRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onApp = { navController.navigate(notificationAppDetailsRoute(it)) },
        )
    }
    composable(
        route = NOTIFICATION_APP_DETAILS_ROUTE,
        arguments = listOf(navArgument(NOTIFICATION_APP_ARGUMENT) { type = NavType.StringType }),
    ) {
        NotificationAppDetailsRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
        )
    }
}
