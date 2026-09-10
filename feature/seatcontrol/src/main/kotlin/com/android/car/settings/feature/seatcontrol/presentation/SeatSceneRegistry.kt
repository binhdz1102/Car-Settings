package com.android.car.settings.feature.seatcontrol.presentation

import com.android.car.settings.core.ui.VehicleLayerSpec
import com.android.car.settings.core.ui.VehiclePreviewAnchor
import com.android.car.settings.core.ui.VehicleSceneSpec
import com.android.car.settings.feature.seatcontrol.R

internal val SEAT_SCENE =
    VehicleSceneSpec(
        id = "vehicle_seat_side_view",
        layers =
            listOf(
                VehicleLayerSpec(
                    id = "seat_and_steering_base",
                    drawableRes = R.drawable.vehicle_preview_seat_steering,
                    anchor = VehiclePreviewAnchor(.5f, .5f),
                ),
            ),
    )
