package com.android.car.settings.feature.sound.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.android.car.settings.feature.sound.domain.RingtoneKind

const val SOUND_ROUTE = "sound"
const val RINGTONE_ROUTE = "sound/ringtone/{kind}"

fun ringtoneRoute(kind: RingtoneKind): String = "sound/ringtone/${kind.name}"

fun NavGraphBuilder.soundGraph(
    onBack: () -> Unit,
    onNavigateForward: (String) -> Unit,
) {
    composable(SOUND_ROUTE) {
        SoundRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onRingtone = { kind -> onNavigateForward(ringtoneRoute(kind)) },
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
            onBack = onBack,
        )
    }
}
