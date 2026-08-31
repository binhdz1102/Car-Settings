package com.android.car.settings.feature.doorcontrol.presentation

import com.android.car.settings.feature.doorcontrol.domain.DoorControlId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DoorControlArtworkTest {
    @Test
    fun everyDoorControlHasAUniqueGuide() {
        val resources = DoorControlId.entries.map(::doorArtwork)
        assertThat(resources).doesNotContain(0)
        assertThat(resources).hasSize(DoorControlId.entries.size)
        assertThat(resources.toSet()).hasSize(DoorControlId.entries.size)
    }
}
