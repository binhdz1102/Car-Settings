package com.android.car.settings.feature.vehiclelighting.presentation

import com.android.car.settings.core.ui.VehicleLayerSpec
import com.android.car.settings.core.ui.VehiclePreviewAnchor
import com.android.car.settings.core.ui.VehicleSceneSpec
import com.android.car.settings.feature.vehiclelighting.R

internal val LIGHTING_SCENE =
    VehicleSceneSpec(
        id = "vehicle_lighting_top_view",
        layers =
            listOf(
                VehicleLayerSpec(
                    id = "vehicle_lighting_base",
                    drawableRes = R.drawable.vehicle_preview_lighting,
                    anchor = VehiclePreviewAnchor(.5f, .5f),
                ),
            ),
    )
