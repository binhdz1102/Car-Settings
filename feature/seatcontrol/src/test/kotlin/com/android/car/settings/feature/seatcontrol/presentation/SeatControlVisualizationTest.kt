package com.android.car.settings.feature.seatcontrol.presentation

import com.android.car.settings.core.ui.VehicleControlUiModel
import com.android.car.settings.core.ui.VehicleEditorUiKind
import com.android.car.settings.core.ui.VehicleObservedSnapshot
import com.android.car.settings.core.ui.VehicleObservationStatus
import com.android.car.settings.core.ui.VehicleSliderUiKind
import com.android.car.settings.feature.seatcontrol.domain.SeatControlKind
import org.junit.Assert.assertEquals
import org.junit.Test

class SeatControlVisualizationTest {
    @Test
    fun rangeControls_usePositionSliderContract() {
        val spec = seatSliderUiSpec(SeatControlKind.RANGE)
        assertEquals(VehicleSliderUiKind.POSITION, spec.kind)
        assertEquals(0f, spec.centerMarker)
    }

    @Test
    fun normalizedPosition_clampsStartMiddleAndEnd() {
        assertEquals(0f, normalizedSeatPosition(0f, 0f..100f), 0.0001f)
        assertEquals(.5f, normalizedSeatPosition(50f, 0f..100f), 0.0001f)
        assertEquals(1f, normalizedSeatPosition(120f, 0f..100f), 0.0001f)
    }

    @Test
    fun missingOrInvalidPosition_usesNeutralFallback() {
        assertEquals(.5f, normalizedSeatPosition(null, 0f..100f), 0.0001f)
        assertEquals(.5f, normalizedSeatPosition(Float.NaN, 0f..100f), 0.0001f)
        assertEquals(.5f, normalizedSeatPosition(10f, 10f..10f), 0.0001f)
    }

    @Test
    fun visualMotion_usesActualPositionPropertiesAndIgnoresUnrelatedRows() {
        val controls =
            listOf(
                control("FORE_AFT", 25f, 0f..100f),
                control("HEIGHT", 80f, 0f..100f),
                control("SEAT_BELT", null),
            )

        val motion = seatVisualMotion(controls)

        assertEquals(.25f, motion.foreAft, 0.0001f)
        assertEquals(.8f, motion.height, 0.0001f)
        assertEquals(.5f, motion.backrest, 0.0001f)
    }

    @Test
    fun interpolation_supportsIntermediateAndInterruptedLatestTarget() {
        val start = SeatVisualMotion(foreAft = 0f, height = 0f)
        val firstTarget = SeatVisualMotion(foreAft = 1f, height = 1f)
        val latestTarget = SeatVisualMotion(foreAft = .25f, height = .75f)

        assertEquals(0f, interpolateSeatMotion(start, firstTarget, 0f).foreAft, 0.0001f)
        assertEquals(.5f, interpolateSeatMotion(start, firstTarget, .5f).foreAft, 0.0001f)
        assertEquals(1f, interpolateSeatMotion(start, firstTarget, 1f).foreAft, 0.0001f)
        assertEquals(.25f, interpolateSeatMotion(firstTarget, latestTarget, 1f).foreAft, 0.0001f)
    }

    private fun control(
        key: String,
        numericValue: Float?,
        range: ClosedFloatingPointRange<Float> = 0f..1f,
    ): VehicleControlUiModel =
        VehicleControlUiModel(
            key = key,
            propertyId = key.hashCode(),
            areaId = 1,
            section = "Seat",
            title = key,
            summary = "",
            info = "",
            limitations = "",
            dependencies = "",
            editor = VehicleEditorUiKind.SLIDER,
            readable = true,
            writable = true,
            available = true,
            pending = false,
            numericValue = numericValue,
            range = range,
            observedSnapshot =
                VehicleObservedSnapshot(
                    numericValue = numericValue,
                    status =
                        if (numericValue == null) VehicleObservationStatus.UNKNOWN
                        else VehicleObservationStatus.CONFIRMED,
                ),
        )
}
