package com.android.car.settings.feature.driverassistance.presentation

import com.android.car.settings.core.ui.VehicleLayerSpec
import com.android.car.settings.core.ui.VehiclePreviewAnchor
import com.android.car.settings.core.ui.VehicleSceneSpec
import com.android.car.settings.feature.driverassistance.R

internal val ADAS_SCENE =
    VehicleSceneSpec(
        id = "adas_context_top_view",
        layers =
            listOf(
                VehicleLayerSpec(
                    id = "road_and_ego_vehicle",
                    drawableRes = R.drawable.vehicle_preview_driver_assistance,
                    anchor = VehiclePreviewAnchor(.5f, .5f),
                ),
            ),
    )
