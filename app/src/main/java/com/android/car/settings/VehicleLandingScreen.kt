package com.android.car.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
        title = stringResource(R.string.settings_category_vehicle),
        subtitle = stringResource(R.string.vehicle_landing_subtitle),
        destinationKey = "vehicle",
        isRoot = true,
        onBack = onBack,
    ) {
        SettingsSection(title = stringResource(R.string.vehicle_landing_section)) {
            SettingsActionRow(
                title = stringResource(R.string.vehicle_feature_climate),
                summary = stringResource(R.string.vehicle_feature_climate_summary),
                leading = { Icon(Icons.Outlined.Air, contentDescription = null) },
                onClick = { onDestination(SettingsDestinationId.HVAC) },
            )
            SettingsActionRow(
                title = stringResource(R.string.vehicle_feature_assistance),
                summary = stringResource(R.string.vehicle_feature_assistance_summary),
                leading = { Icon(Icons.Outlined.DirectionsCar, contentDescription = null) },
                onClick = { onDestination(SettingsDestinationId.DRIVER_ASSISTANCE) },
            )
            SettingsActionRow(
                title = stringResource(R.string.vehicle_feature_seats),
                summary = stringResource(R.string.vehicle_feature_seats_summary),
                leading = { Icon(Icons.Outlined.EventSeat, contentDescription = null) },
                onClick = { onDestination(SettingsDestinationId.SEAT_CONTROL) },
            )
            SettingsActionRow(
                title = stringResource(R.string.vehicle_feature_doors),
                summary = stringResource(R.string.vehicle_feature_doors_summary),
                leading = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                onClick = { onDestination(SettingsDestinationId.DOOR_CONTROL) },
            )
            SettingsActionRow(
                title = stringResource(R.string.vehicle_feature_lighting),
                summary = stringResource(R.string.vehicle_feature_lighting_summary),
                leading = { Icon(Icons.Outlined.LightMode, contentDescription = null) },
                onClick = { onDestination(SettingsDestinationId.VEHICLE_LIGHTING) },
            )
        }
    }
}
