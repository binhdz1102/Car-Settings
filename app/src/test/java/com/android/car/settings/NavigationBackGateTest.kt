package com.android.car.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavigationBackGateTest {
    @Test
    fun duplicateBackFromSameEntryIsConsumedOnce() {
        val gate = NavigationBackGate()

        assertThat(gate.tryStart("details-entry")).isTrue()
        assertThat(gate.tryStart("details-entry")).isFalse()
    }

    @Test
    fun newDestinationUnlocksBackNavigation() {
        val gate = NavigationBackGate()
        gate.tryStart("details-entry")

        gate.onDestinationChanged("root-entry")

        assertThat(gate.tryStart("root-entry")).isTrue()
    }

    @Test
    fun missingEntryCannotStartNavigation() {
        assertThat(NavigationBackGate().tryStart(null)).isFalse()
    }
}
