package com.android.car.settings.feature.system.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

const val SYSTEM_ROUTE = "system"
const val SYSTEM_ABOUT_ROUTE = "system/about"
private const val HARDWARE_ROUTE = "system/about/hardware"
const val LEGAL_ROUTE = "system/legal"
const val RESET_OPTIONS_ROUTE = "system/reset"
private const val RESET_NETWORK_ROUTE = "system/reset/network"
private const val RESET_APP_PREFERENCES_ROUTE = "system/reset/app-preferences"
private const val FACTORY_RESET_ROUTE = "system/reset/factory"
private const val DATE_TIME_ROUTE = "display/date-time"
const val LANGUAGE_INPUT_ROUTE = "system/language-input"
private const val LANGUAGE_PICKER_ROUTE = "system/language-input/language"
private const val AUTOFILL_ROUTE = "system/language-input/autofill"
private const val KEYBOARD_ROUTE = "system/language-input/keyboard"
private const val TEXT_TO_SPEECH_ROUTE = "system/language-input/text-to-speech"
const val UNITS_ROUTE = "system/units"
const val STORAGE_ROUTE = "system/storage"

fun NavGraphBuilder.systemGraph(
    navController: NavHostController,
    onBack: () -> Unit,
) {
    composable(SYSTEM_ROUTE) {
        SystemRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onAbout = { navController.navigate(SYSTEM_ABOUT_ROUTE) },
            onLegal = { navController.navigate(LEGAL_ROUTE) },
            onResetOptions = { navController.navigate(RESET_OPTIONS_ROUTE) },
            onDateTime = { navController.navigate(DATE_TIME_ROUTE) },
            onLanguageInput = { navController.navigate(LANGUAGE_INPUT_ROUTE) },
            onUnits = { navController.navigate(UNITS_ROUTE) },
            onStorage = { navController.navigate(STORAGE_ROUTE) },
        )
    }
    composable(SYSTEM_ABOUT_ROUTE) {
        AboutRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onHardware = { navController.navigate(HARDWARE_ROUTE) },
        )
    }
    composable(HARDWARE_ROUTE) {
        HardwareInfoRoute(viewModel = hiltViewModel(), onBack = navController::popBackStack)
    }
    composable(LANGUAGE_INPUT_ROUTE) {
        LanguageInputRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onLanguage = { navController.navigate(LANGUAGE_PICKER_ROUTE) },
            onAutofill = { navController.navigate(AUTOFILL_ROUTE) },
            onKeyboard = { navController.navigate(KEYBOARD_ROUTE) },
            onTextToSpeech = { navController.navigate(TEXT_TO_SPEECH_ROUTE) },
        )
    }
    composable(LANGUAGE_PICKER_ROUTE) {
        SystemLanguageRoute(viewModel = hiltViewModel(), onBack = navController::popBackStack)
    }
    composable(AUTOFILL_ROUTE) {
        AutofillRoute(viewModel = hiltViewModel(), onBack = navController::popBackStack)
    }
    composable(KEYBOARD_ROUTE) {
        KeyboardRoute(viewModel = hiltViewModel(), onBack = navController::popBackStack)
    }
    composable(TEXT_TO_SPEECH_ROUTE) {
        TextToSpeechRoute(viewModel = hiltViewModel(), onBack = navController::popBackStack)
    }
    composable(UNITS_ROUTE) {
        UnitsRoute(viewModel = hiltViewModel(), onBack = navController::popBackStack)
    }
    composable(STORAGE_ROUTE) {
        StorageRoute(viewModel = hiltViewModel(), onBack = navController::popBackStack)
    }
    composable(LEGAL_ROUTE) {
        LegalRoute(viewModel = hiltViewModel(), onBack = navController::popBackStack)
    }
    composable(RESET_OPTIONS_ROUTE) {
        ResetOptionsRoute(
            viewModel = hiltViewModel(),
            onBack = navController::popBackStack,
            onNetworkReset = { navController.navigate(RESET_NETWORK_ROUTE) },
            onResetAppPreferences = { navController.navigate(RESET_APP_PREFERENCES_ROUTE) },
            onFactoryReset = { navController.navigate(FACTORY_RESET_ROUTE) },
        )
    }
    composable(RESET_NETWORK_ROUTE) {
        ResetNetworkRoute(viewModel = hiltViewModel(), onBack = navController::popBackStack)
    }
    composable(RESET_APP_PREFERENCES_ROUTE) {
        ResetAppPreferencesRoute(viewModel = hiltViewModel(), onBack = navController::popBackStack)
    }
    composable(FACTORY_RESET_ROUTE) {
        FactoryResetRoute(viewModel = hiltViewModel(), onBack = navController::popBackStack)
    }
}
