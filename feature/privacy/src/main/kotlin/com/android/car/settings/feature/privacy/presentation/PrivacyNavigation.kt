package com.android.car.settings.feature.privacy.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.android.car.settings.feature.privacy.domain.PrivacyPermissionType

const val PRIVACY_ROUTE = "privacy"
const val PRIVACY_MICROPHONE_ROUTE = "privacy/microphone"
const val PRIVACY_CAMERA_ROUTE = "privacy/camera"
const val PRIVACY_LOCATION_ROUTE = "privacy/location"
private const val PRIVACY_PERMISSION_TYPE_ARGUMENT = "permissionType"
private const val PRIVACY_PERMISSION_APPS_ROUTE = "privacy/permissions/{$PRIVACY_PERMISSION_TYPE_ARGUMENT}"

private fun privacyPermissionAppsRoute(type: PrivacyPermissionType) = "privacy/permissions/${type.name}"

fun NavGraphBuilder.privacyGraph(
    navController: NavHostController,
    onBack: () -> Unit,
) {
    composable(PRIVACY_ROUTE) {
        PrivacyRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onMicrophone = { navController.navigate(PRIVACY_MICROPHONE_ROUTE) },
            onCamera = { navController.navigate(PRIVACY_CAMERA_ROUTE) },
            onLocation = { navController.navigate(PRIVACY_LOCATION_ROUTE) },
        )
    }
    composable(PRIVACY_MICROPHONE_ROUTE) {
        SensorPrivacyRoute(
            type = PrivacyPermissionType.MICROPHONE,
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onManagePermissions = { navController.navigate(privacyPermissionAppsRoute(it)) },
        )
    }
    composable(PRIVACY_CAMERA_ROUTE) {
        SensorPrivacyRoute(
            type = PrivacyPermissionType.CAMERA,
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onManagePermissions = { navController.navigate(privacyPermissionAppsRoute(it)) },
        )
    }
    composable(PRIVACY_LOCATION_ROUTE) {
        LocationPrivacyRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onManagePermissions = { navController.navigate(privacyPermissionAppsRoute(it)) },
        )
    }
    composable(
        route = PRIVACY_PERMISSION_APPS_ROUTE,
        arguments = listOf(navArgument(PRIVACY_PERMISSION_TYPE_ARGUMENT) { type = NavType.StringType }),
    ) { entry ->
        val type =
            PrivacyPermissionType.valueOf(
                requireNotNull(entry.arguments?.getString(PRIVACY_PERMISSION_TYPE_ARGUMENT)),
            )
        PermissionAppsRoute(
            type = type,
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
        )
    }
}
