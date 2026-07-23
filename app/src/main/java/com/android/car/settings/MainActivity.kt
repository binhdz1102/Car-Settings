package com.android.car.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.ui.CarPropertyDemoScreen
import com.android.car.settings.ui.CarPropertyDemoViewModel
import com.android.car.settings.ui.theme.CarSettingTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CarPropertyDemoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CarSettingTheme(dynamicColor = false) {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                CarPropertyDemoScreen(
                    uiState = uiState,
                    onDecreaseDriverTemperature = viewModel::decreaseDriverTemperature,
                    onIncreaseDriverTemperature = viewModel::increaseDriverTemperature,
                    onDecreasePassengerTemperature = viewModel::decreasePassengerTemperature,
                    onIncreasePassengerTemperature = viewModel::increasePassengerTemperature,
                    onDecreaseFanSpeed = viewModel::decreaseFanSpeed,
                    onIncreaseFanSpeed = viewModel::increaseFanSpeed,
                    onToggleAc = viewModel::toggleAc,
                    onRefresh = viewModel::refresh,
                    onRunLargeBatchRead = viewModel::runLargeBatchRead,
                    onRunConfirmedBatchWrite = viewModel::runConfirmedBatchWrite,
                    onRunErrorScenarios = viewModel::runErrorScenarios,
                )
            }
        }
    }
}
