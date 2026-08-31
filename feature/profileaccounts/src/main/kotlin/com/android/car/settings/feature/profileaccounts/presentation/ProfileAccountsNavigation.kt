package com.android.car.settings.feature.profileaccounts.presentation

import android.net.Uri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

const val PROFILE_ACCOUNTS_ROUTE = "profile-accounts"
const val PROFILES_ROUTE = "profile-accounts/profiles"
const val ADD_PROFILE_ROUTE = "profile-accounts/profiles/add"
const val PROFILE_ID_ARGUMENT = "userId"
const val PROFILE_DETAILS_ROUTE = "profile-accounts/profiles/{$PROFILE_ID_ARGUMENT}"
const val ADD_ACCOUNT_ROUTE = "profile-accounts/accounts/add"
const val ACCOUNT_NAME_ARGUMENT = "accountName"
const val ACCOUNT_TYPE_ARGUMENT = "accountType"
const val ACCOUNT_DETAILS_ROUTE = "profile-accounts/accounts/{$ACCOUNT_NAME_ARGUMENT}/{$ACCOUNT_TYPE_ARGUMENT}"

fun profileDetailsRoute(userId: Int) = "profile-accounts/profiles/$userId"

fun accountDetailsRoute(
    name: String,
    type: String,
) = "profile-accounts/accounts/${Uri.encode(name)}/${Uri.encode(type)}"

fun NavGraphBuilder.profileAccountsGraph(
    onBack: () -> Unit,
    onNavigateForward: (String) -> Unit,
) {
    composable(PROFILE_ACCOUNTS_ROUTE) {
        ProfileAccountsRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onProfiles = { onNavigateForward(PROFILES_ROUTE) },
            onProfile = { onNavigateForward(profileDetailsRoute(it)) },
            onAddAccount = { onNavigateForward(ADD_ACCOUNT_ROUTE) },
            onAccount = { name, type -> onNavigateForward(accountDetailsRoute(name, type)) },
        )
    }
    composable(PROFILES_ROUTE) {
        ProfilesRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onProfile = { onNavigateForward(profileDetailsRoute(it)) },
            onAddProfile = { onNavigateForward(ADD_PROFILE_ROUTE) },
        )
    }
    composable(ADD_PROFILE_ROUTE) {
        AddProfileRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
    composable(
        PROFILE_DETAILS_ROUTE,
        arguments = listOf(navArgument(PROFILE_ID_ARGUMENT) { type = NavType.IntType }),
    ) {
        ProfileDetailsRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
    composable(ADD_ACCOUNT_ROUTE) {
        AddAccountRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
    composable(
        ACCOUNT_DETAILS_ROUTE,
        arguments =
            listOf(
                navArgument(ACCOUNT_NAME_ARGUMENT) { type = NavType.StringType },
                navArgument(ACCOUNT_TYPE_ARGUMENT) { type = NavType.StringType },
            ),
    ) {
        AccountDetailsRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
}
