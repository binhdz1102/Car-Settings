package com.android.car.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import com.android.car.settings.core.settings.SettingsDestinationId
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection

/** Production Vehicle landing page. Entries are routes backed by the live VHAL ViewModels. */
@Composable
fun VehicleLandingScreen(
    onDestination: (SettingsDestinationId) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(
        title = "Vehicle",
        subtitle = "Vehicle hardware settings",
        destinationKey = "vehicle",
        isRoot = true,
        onBack = onBack,
    ) {
        SettingsSection(title = "Vehicle controls") {
            SettingsActionRow(
                title = "Climate",
                summary = "Temperature, airflow, defrost and seat comfort",
                leading = { Icon(Icons.Outlined.Air, contentDescription = null) },
                onClick = { onDestination(SettingsDestinationId.HVAC) },
            )
            SettingsActionRow(
                title = "Driver assistance",
                summary = "Collision warnings, lane keeping and cruise control",
                leading = { Icon(Icons.Outlined.DirectionsCar, contentDescription = null) },
                onClick = { onDestination(SettingsDestinationId.DRIVER_ASSISTANCE) },
            )
            SettingsActionRow(
                title = "Seats & steering wheel",
                summary = "Position, support, memory and heating",
                leading = { Icon(Icons.Outlined.EventSeat, contentDescription = null) },
                onClick = { onDestination(SettingsDestinationId.SEAT_CONTROL) },
            )
            SettingsActionRow(
                title = "Doors, windows & mirrors",
                summary = "Locks, positions, movement and folding",
                leading = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                onClick = { onDestination(SettingsDestinationId.DOOR_CONTROL) },
            )
            SettingsActionRow(
                title = "Vehicle lighting",
                summary = "Headlights, hazard and cabin lights",
                leading = { Icon(Icons.Outlined.LightMode, contentDescription = null) },
                onClick = { onDestination(SettingsDestinationId.VEHICLE_LIGHTING) },
            )
        }
    }
}
