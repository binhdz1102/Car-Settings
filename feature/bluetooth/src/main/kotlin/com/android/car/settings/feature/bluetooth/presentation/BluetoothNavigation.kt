package com.android.car.settings.feature.bluetooth.presentation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val BLUETOOTH_ROUTE = "bluetooth"
private const val BLUETOOTH_DETAILS_ROUTE = "bluetooth/details"

fun NavGraphBuilder.bluetoothGraph(
    onBack: () -> Unit,
    onNavigateForward: (String) -> Unit,
) {
    composable(BLUETOOTH_ROUTE) {
        val viewModel: BluetoothViewModel = hiltViewModel()
        BluetoothRoute(
            viewModel = viewModel,
            onBack = onBack,
            onDeviceDetails = { address ->
                viewModel.selectDevice(address)
                onNavigateForward(BLUETOOTH_DETAILS_ROUTE)
            },
        )
    }
    composable(BLUETOOTH_DETAILS_ROUTE) {
        BluetoothDeviceDetailsRoute(
            viewModel = hiltViewModel(),
            onBack = onBack,
        )
    }
}
