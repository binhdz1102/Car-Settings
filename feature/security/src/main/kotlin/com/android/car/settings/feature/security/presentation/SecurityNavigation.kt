package com.android.car.settings.feature.security.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.android.car.settings.feature.security.domain.SecurityLockType

const val SECURITY_ROUTE = "security"
const val SECURITY_LOCK_TYPES_ROUTE = "security/lock-types"
private const val SECURITY_LOCK_TYPE_ARGUMENT = "lockType"
private const val SECURITY_LOCK_SETUP_ROUTE = "security/lock/{$SECURITY_LOCK_TYPE_ARGUMENT}"
const val SECURITY_DEVICE_ADMINS_ROUTE = "security/device-admins"

private fun securityLockSetupRoute(type: SecurityLockType) = "security/lock/${type.name}"

fun NavGraphBuilder.securityGraph(
    onBack: () -> Unit,
    onNavigateForward: (String) -> Unit,
) {
    composable(SECURITY_ROUTE) {
        SecurityRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onLockTypes = { onNavigateForward(SECURITY_LOCK_TYPES_ROUTE) },
            onDeviceAdmins = { onNavigateForward(SECURITY_DEVICE_ADMINS_ROUTE) },
        )
    }
    composable(SECURITY_LOCK_TYPES_ROUTE) {
        LockTypesRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onType = { onNavigateForward(securityLockSetupRoute(it)) },
        )
    }
    composable(
        route = SECURITY_LOCK_SETUP_ROUTE,
        arguments = listOf(navArgument(SECURITY_LOCK_TYPE_ARGUMENT) { type = NavType.StringType }),
    ) { entry ->
        val type =
            SecurityLockType.valueOf(
                requireNotNull(entry.arguments?.getString(SECURITY_LOCK_TYPE_ARGUMENT)),
            )
        LockSetupRoute(type, hiltViewModel(), onBack)
    }
    composable(SECURITY_DEVICE_ADMINS_ROUTE) {
        DeviceAdminsRoute(hiltViewModel(), onBack)
    }
}
