package com.android.car.settings.feature.seatcontrol.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SeatControlDefinitionsTest {
    @Test fun `all seat contracts are standard and unique`() {
        assertThat(SEAT_CONTROL_DEFINITIONS).hasSize(20)
        assertThat(SEAT_CONTROL_DEFINITIONS.map { it.id }.distinct()).hasSize(20)
        assertThat(SEAT_CONTROL_DEFINITIONS.map { it.propertyId }.distinct()).hasSize(20)
    }

    @Test fun `memory controls are explicit slot actions instead of fake readable ranges`() {
        val memoryControls =
            SEAT_CONTROL_DEFINITIONS.filter {
                it.id == SeatControlId.MEMORY_RECALL || it.id == SeatControlId.MEMORY_SAVE
            }

        assertThat(memoryControls.map { it.kind }).containsExactly(
            SeatControlKind.SLOT_ACTION,
            SeatControlKind.SLOT_ACTION,
        )
    }
}
