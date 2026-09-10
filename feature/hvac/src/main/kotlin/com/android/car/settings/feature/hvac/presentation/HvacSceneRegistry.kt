package com.android.car.settings.feature.hvac.presentation

import com.android.car.settings.core.ui.VehicleLayerSpec
import com.android.car.settings.core.ui.VehiclePreviewAnchor
import com.android.car.settings.core.ui.VehicleSceneSpec
import com.android.car.settings.feature.hvac.R

/** Runtime scene contract; design briefs describe the layer split used by future vector assets. */
internal val HVAC_SCENE =
    VehicleSceneSpec(
        id = "climate_cabin_top_view",
        layers =
            listOf(
                VehicleLayerSpec(
                    id = "cabin_base",
                    drawableRes = R.drawable.vehicle_preview_climate,
                    anchor = VehiclePreviewAnchor(.5f, .5f),
                ),
            ),
    )
