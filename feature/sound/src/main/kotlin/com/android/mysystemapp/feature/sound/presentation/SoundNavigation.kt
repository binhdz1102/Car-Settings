package com.android.car.settings.feature.sound.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.android.car.settings.feature.sound.domain.RingtoneKind

const val SOUND_ROUTE = "sound"
const val RINGTONE_ROUTE = "sound/ringtone/{kind}"

fun ringtoneRoute(kind: RingtoneKind): String = "sound/ringtone/${kind.name}"

fun NavGraphBuilder.soundGraph(
    navController: NavHostController,
    onBack: () -> Unit,
) {
    composable(SOUND_ROUTE) {
        SoundRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onRingtone = { kind -> navController.navigate(ringtoneRoute(kind)) },
        )
    }
    composable(
        route = RINGTONE_ROUTE,
        arguments = listOf(navArgument("kind") { type = NavType.StringType }),
    ) { entry ->
        val kind =
            entry.arguments
                ?.getString("kind")
                ?.let { value -> RingtoneKind.entries.firstOrNull { it.name == value } }
                ?: RingtoneKind.PHONE
        RingtonePickerRoute(
            kind = kind,
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
        )
    }
}
