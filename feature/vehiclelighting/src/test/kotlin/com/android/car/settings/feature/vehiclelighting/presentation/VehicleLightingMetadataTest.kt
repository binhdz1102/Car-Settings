package com.android.car.settings.feature.vehiclelighting.presentation

import com.android.car.settings.core.ui.VehicleEditorUiKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VehicleLightingMetadataTest {
    @Test
    fun `all AAOS lighting switch definitions map to enum rows`() {
        val modeLabels = mapOf(0 to "Off", 1 to "Automatic", 2 to "On", 3 to "Flash")
        val metadata =
            buildVehicleLightingMetadata(
                titles = Array(8) { "title-$it" },
                descriptions = Array(8) { "description-$it" },
                sections = Array(3) { "section-$it" },
                modeLabels = modeLabels,
                limitations = "limitations",
                dependencies = "dependencies",
            )

        assertThat(metadata).hasSize(8)
        assertThat(metadata.map { it.editor }).containsExactlyElementsIn(List(8) { VehicleEditorUiKind.ENUM })
        metadata.forEach { assertThat(it.enumLabels).isEqualTo(modeLabels) }
        assertThat(metadata.map { it.illustrationRes }).doesNotContain(0)
    }

    @Test
    fun `value labels resolve VehicleLightSwitch values and fall back for unknown vendor values`() {
        val modeLabels = mapOf(0 to "Off", 1 to "Automatic", 2 to "On", 3 to "Flash")
        val metadata =
            buildVehicleLightingMetadata(
                titles = Array(8) { "title-$it" },
                descriptions = Array(8) { "description-$it" },
                sections = Array(3) { "section-$it" },
                modeLabels = modeLabels,
                limitations = "limitations",
                dependencies = "dependencies",
            )

        val row = metadata.first()
        assertThat(row.valueLabel(2)).isEqualTo("On")
        assertThat(row.valueLabel(0)).isEqualTo("Off")
        assertThat(row.valueLabel(256)).isEqualTo("256")
    }
}
