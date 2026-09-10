package com.android.car.settings.feature.doorcontrol.presentation

import com.android.car.settings.core.ui.VehicleLayerSpec
import com.android.car.settings.core.ui.VehiclePreviewAnchor
import com.android.car.settings.core.ui.VehicleSceneSpec
import com.android.car.settings.feature.doorcontrol.R

internal val DOOR_SCENE =
    VehicleSceneSpec(
        id = "vehicle_doors_top_view",
        layers =
            listOf(
                VehicleLayerSpec(
                    id = "vehicle_body_base",
                    drawableRes = R.drawable.vehicle_preview_doors,
                    anchor = VehiclePreviewAnchor(.5f, .5f),
                ),
            ),
    )
