package com.android.car.settings.feature.doorcontrol.presentation

import com.android.car.settings.core.ui.VehicleControlUiModel
import com.android.car.settings.core.ui.VehicleEditorUiKind
import com.android.car.settings.core.ui.VehicleSliderUiKind
import com.android.car.settings.feature.doorcontrol.domain.DoorControlKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DoorControlVisualizationTest {
    @Test
    fun rangeControls_usePositionSliderContract() {
        val spec = doorSliderUiSpec(DoorControlKind.RANGE)
        assertEquals(VehicleSliderUiKind.POSITION, spec.kind)
        assertEquals(0f, spec.centerMarker)
    }

    @Test
    fun visualMotion_usesPositionAndStatePropertiesOnly() {
        val motion =
            doorVisualMotion(
                listOf(
                    control("DOOR_POSITION", numeric = 50f, range = 0f..100f),
                    control("WINDOW_POSITION", numeric = 25f, range = 0f..100f),
                    control("MIRROR_FOLD", boolean = true),
                    control("DOOR_LOCK", boolean = true),
                    // Commands must not be treated as diagram state.
                    control("DOOR_MOVE", numeric = 100f, range = 0f..100f),
                ),
            )

        assertEquals(.5f, motion.doorOpen, 0.0001f)
        assertEquals(.25f, motion.windowOpen, 0.0001f)
        assertEquals(true, motion.mirrorFolded)
        assertEquals(true, motion.locked)
    }

    @Test
    fun interpolation_clampsAndAppliesLatestDiscreteStateAtEnd() {
        val from = DoorVisualMotion(doorOpen = 0f, windowOpen = 0f, locked = false)
        val to = DoorVisualMotion(doorOpen = 1f, windowOpen = .5f, locked = true)

        assertEquals(0f, interpolateDoorMotion(from, to, 0f).doorOpen, 0.0001f)
        assertEquals(.5f, interpolateDoorMotion(from, to, .5f).doorOpen, 0.0001f)
        assertEquals(true, interpolateDoorMotion(from, to, 1.5f).locked)
    }

    private fun control(
        key: String,
        numeric: Float? = null,
        boolean: Boolean? = null,
        range: ClosedFloatingPointRange<Float> = 0f..1f,
    ): VehicleControlUiModel =
        VehicleControlUiModel(
            key = key,
            propertyId = key.hashCode(),
            areaId = 1,
            section = "Door",
            title = key,
            summary = "",
            info = "",
            limitations = "",
            dependencies = "",
            editor = if (boolean != null) VehicleEditorUiKind.SWITCH else VehicleEditorUiKind.SLIDER,
            readable = true,
            writable = true,
            available = true,
            pending = false,
            numericValue = numeric,
            booleanValue = boolean,
            range = range,
        )
}
