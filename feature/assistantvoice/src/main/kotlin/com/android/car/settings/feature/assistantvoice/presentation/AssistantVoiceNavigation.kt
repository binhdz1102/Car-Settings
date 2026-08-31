package com.android.car.settings.feature.assistantvoice.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val ASSISTANT_VOICE_ROUTE = "assistant-voice"

fun NavGraphBuilder.assistantVoiceGraph(onBack: () -> Unit) {
    composable(ASSISTANT_VOICE_ROUTE) {
        AssistantVoiceRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
        )
    }
}
