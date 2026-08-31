package com.android.car.settings.feature.system.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
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
    onBack: () -> Unit,
    onNavigateForward: (String) -> Unit,
) {
    composable(SYSTEM_ROUTE) {
        SystemRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onAbout = { onNavigateForward(SYSTEM_ABOUT_ROUTE) },
            onLegal = { onNavigateForward(LEGAL_ROUTE) },
            onResetOptions = { onNavigateForward(RESET_OPTIONS_ROUTE) },
            onDateTime = { onNavigateForward(DATE_TIME_ROUTE) },
            onLanguageInput = { onNavigateForward(LANGUAGE_INPUT_ROUTE) },
            onUnits = { onNavigateForward(UNITS_ROUTE) },
            onStorage = { onNavigateForward(STORAGE_ROUTE) },
        )
    }
    composable(SYSTEM_ABOUT_ROUTE) {
        AboutRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onHardware = { onNavigateForward(HARDWARE_ROUTE) },
        )
    }
    composable(HARDWARE_ROUTE) {
        HardwareInfoRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
    composable(LANGUAGE_INPUT_ROUTE) {
        LanguageInputRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onLanguage = { onNavigateForward(LANGUAGE_PICKER_ROUTE) },
            onAutofill = { onNavigateForward(AUTOFILL_ROUTE) },
            onKeyboard = { onNavigateForward(KEYBOARD_ROUTE) },
            onTextToSpeech = { onNavigateForward(TEXT_TO_SPEECH_ROUTE) },
        )
    }
    composable(LANGUAGE_PICKER_ROUTE) {
        SystemLanguageRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
    composable(AUTOFILL_ROUTE) {
        AutofillRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
    composable(KEYBOARD_ROUTE) {
        KeyboardRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
    composable(TEXT_TO_SPEECH_ROUTE) {
        TextToSpeechRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
    composable(UNITS_ROUTE) {
        UnitsRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
    composable(STORAGE_ROUTE) {
        StorageRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
    composable(LEGAL_ROUTE) {
        LegalRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
    composable(RESET_OPTIONS_ROUTE) {
        ResetOptionsRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
            onNetworkReset = { onNavigateForward(RESET_NETWORK_ROUTE) },
            onResetAppPreferences = { onNavigateForward(RESET_APP_PREFERENCES_ROUTE) },
            onFactoryReset = { onNavigateForward(FACTORY_RESET_ROUTE) },
        )
    }
    composable(RESET_NETWORK_ROUTE) {
        ResetNetworkRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
    composable(RESET_APP_PREFERENCES_ROUTE) {
        ResetAppPreferencesRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
    composable(FACTORY_RESET_ROUTE) {
        FactoryResetRoute(viewModel = hiltViewModel(), onBack = onBack)
    }
}
