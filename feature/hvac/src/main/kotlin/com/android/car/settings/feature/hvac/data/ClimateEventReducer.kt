package com.android.car.settings.feature.hvac.data

import com.android.car.settings.feature.hvac.domain.ClimateControl
import com.android.car.settings.feature.hvac.domain.ClimateControlKind
import com.android.car.settings.feature.hvac.domain.ClimateState
import com.android.car.settings.feature.hvac.domain.ClimateValueStatus

/** Pure reducer kept separate from Android callbacks so area isolation is regression-testable. */
internal object ClimateEventReducer {
    fun reduce(
        state: ClimateState,
        propertyId: Int,
        areaId: Int,
        available: Boolean,
        value: Any?,
    ): ClimateState =
        state.copy(
            controls =
                state.controls.map { control ->
                    if (control.capability.propertyId != propertyId ||
                        control.capability.zone.areaId != areaId
                    ) {
                        control
                    } else {
                        control.withVehicleValue(available, value)
                    }
                },
        )

    private fun ClimateControl.withVehicleValue(
        available: Boolean,
        value: Any?,
    ): ClimateControl {
        if (!available) {
            return copy(
                status = ClimateValueStatus.UNAVAILABLE,
                unavailableReason = "Unavailable in the current vehicle state",
                observedBooleanValue = null,
                observedIntValue = null,
                observedFloatValue = null,
                observedTimestampNanos = null,
            )
        }
        return when (capability.kind) {
            ClimateControlKind.TOGGLE ->
                copy(
                    booleanValue = value as? Boolean,
                    observedBooleanValue = value as? Boolean,
                    status = ClimateValueStatus.AVAILABLE,
                    unavailableReason = null,
                )
            ClimateControlKind.FLOAT_RANGE,
            ClimateControlKind.READ_ONLY_FLOAT,
            ->
                copy(
                    floatValue = (value as? Number)?.toFloat(),
                    observedFloatValue = (value as? Number)?.toFloat(),
                    status = ClimateValueStatus.AVAILABLE,
                    unavailableReason = null,
                )
            ClimateControlKind.INT_RANGE,
            ClimateControlKind.INT_OPTIONS,
            ->
                copy(
                    intValue = (value as? Number)?.toInt(),
                    observedIntValue = (value as? Number)?.toInt(),
                    status = ClimateValueStatus.AVAILABLE,
                    unavailableReason = null,
                )
        }
    }
}
