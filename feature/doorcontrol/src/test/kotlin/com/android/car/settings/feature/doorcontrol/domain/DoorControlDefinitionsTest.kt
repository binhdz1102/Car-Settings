package com.android.car.settings.feature.doorcontrol.domain

import com.android.car.settings.feature.doorcontrol.presentation.doorValueLabel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DoorControlDefinitionsTest {
    @Test fun `body-control property contracts are unique`() {
        assertThat(DOOR_CONTROL_DEFINITIONS).hasSize(13)
        assertThat(DOOR_CONTROL_DEFINITIONS.map { it.propertyId }.distinct()).hasSize(13)
    }

    @Test fun `position labels remain numeric while movement labels are enum text`() {
        val movementLabels = mapOf(-1 to "Close", 0 to "Stop", 1 to "Open")

        assertThat(doorValueLabel(DoorControlKind.RANGE, movementLabels, 0)).isEqualTo("0")
        assertThat(doorValueLabel(DoorControlKind.ENUM, movementLabels, 0)).isEqualTo("Stop")
    }
}
