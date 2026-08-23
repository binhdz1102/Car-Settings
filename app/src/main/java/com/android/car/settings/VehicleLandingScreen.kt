package com.android.car.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.android.car.settings.core.settings.SettingsDestinationId
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.core.ui.SettingsSection

internal data class VehicleFeatureLandingItem(
    val destination: SettingsDestinationId,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val icon: ImageVector,
)

internal val vehicleFeatureLandingItems =
    listOf(
        VehicleFeatureLandingItem(
            SettingsDestinationId.HVAC,
            R.string.vehicle_feature_climate,
            R.string.vehicle_feature_climate_summary,
            Icons.Outlined.Air,
        ),
        VehicleFeatureLandingItem(
            SettingsDestinationId.DRIVER_ASSISTANCE,
            R.string.vehicle_feature_assistance,
            R.string.vehicle_feature_assistance_summary,
            Icons.Outlined.DirectionsCar,
        ),
        VehicleFeatureLandingItem(
            SettingsDestinationId.SEAT_CONTROL,
            R.string.vehicle_feature_seats,
            R.string.vehicle_feature_seats_summary,
            Icons.Outlined.EventSeat,
        ),
        VehicleFeatureLandingItem(
            SettingsDestinationId.DOOR_CONTROL,
            R.string.vehicle_feature_doors,
            R.string.vehicle_feature_doors_summary,
            Icons.Outlined.Lock,
        ),
        VehicleFeatureLandingItem(
            SettingsDestinationId.VEHICLE_LIGHTING,
            R.string.vehicle_feature_lighting,
            R.string.vehicle_feature_lighting_summary,
            Icons.Outlined.LightMode,
        ),
    )

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
            vehicleFeatureLandingItems.forEach { item ->
                SettingsActionRow(
                    title = stringResource(item.titleRes),
                    summary = stringResource(item.summaryRes),
                    leading = { Icon(item.icon, contentDescription = null) },
                    onClick = { onDestination(item.destination) },
                )
            }
        }
    }
}
