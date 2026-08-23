package com.android.car.settings.feature.hvac.presentation

import com.android.car.settings.core.ui.VehicleEditorUiKind
import com.android.car.settings.feature.hvac.domain.ClimateCapability
import com.android.car.settings.feature.hvac.domain.ClimateControl
import com.android.car.settings.feature.hvac.domain.ClimateControlId
import com.android.car.settings.feature.hvac.domain.ClimateControlKind
import com.android.car.settings.feature.hvac.domain.ClimateState
import com.android.car.settings.feature.hvac.domain.ClimateValueStatus
import com.android.car.settings.feature.hvac.domain.ClimateZone
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HvacScreenMappingTest {
    @Test
    fun everyClimateControlHasAUniqueGuide() {
        val resources = ClimateControlId.entries.map(::hvacArtwork)
        assertThat(resources).doesNotContain(0)
        assertThat(resources).hasSize(ClimateControlId.entries.size)
        assertThat(resources.toSet()).hasSize(ClimateControlId.entries.size)
    }

    @Test
    fun missingCapabilities_areSafeDiscoverableFallbacks() {
        val presentations =
            ClimateControlId.entries.map { id ->
                ClimatePresentation(
                    id = id,
                    title = id.name,
                    categoryKey = "CATEGORY",
                    categoryTitle = "Category",
                    expectedKind = ClimateControlKind.TOGGLE,
                    info = "Guide for ${id.name}",
                )
            }

        val controls =
            mapClimateControls(
                state = ClimateState(connected = false),
                presentations = presentations,
                limitations = "Limits",
                dependencies = "Dependencies",
                onLabel = "On",
                offLabel = "Off",
                unavailableLabel = "Unavailable",
            )

        assertThat(controls).hasSize(ClimateControlId.entries.size)
        assertThat(controls.map { it.supported }.distinct()).containsExactly(false)
        assertThat(controls.map { it.available }.distinct()).containsExactly(false)
        assertThat(controls.map { it.writable }.distinct()).containsExactly(false)
        assertThat(controls.map { it.key }.toSet()).hasSize(ClimateControlId.entries.size)
    }

    @Test
    fun floatTemperature_preservesRangePrecisionAndPendingSafety() {
        val presentation =
            ClimatePresentation(
                id = ClimateControlId.TEMPERATURE_SET,
                title = "Set temperature",
                categoryKey = "TEMPERATURE",
                categoryTitle = "Temperature",
                expectedKind = ClimateControlKind.FLOAT_RANGE,
                info = "Temperature guide",
                illustrationRes = 42,
            )
        val runtimeControl =
            ClimateControl(
                key = "TEMPERATURE_SET:1",
                capability =
                    ClimateCapability(
                        id = ClimateControlId.TEMPERATURE_SET,
                        propertyId = 100,
                        zone = ClimateZone(1, "Driver"),
                        kind = ClimateControlKind.FLOAT_RANGE,
                        writable = true,
                        min = 16f,
                        max = 30f,
                    ),
                title = "Set temperature",
                section = "Temperature",
                status = ClimateValueStatus.PENDING,
                floatValue = 22.5f,
            )

        val mapped =
            mapClimateControls(
                state = ClimateState(connected = true, controls = listOf(runtimeControl)),
                presentations = listOf(presentation),
                limitations = "Limits",
                dependencies = "Dependencies",
                onLabel = "On",
                offLabel = "Off",
                unavailableLabel = "Unavailable",
            ).single()

        assertThat(mapped.editor).isEqualTo(VehicleEditorUiKind.SLIDER)
        assertThat(mapped.usesFloatSlider).isTrue()
        assertThat(mapped.numericValue).isEqualTo(22.5f)
        assertThat(mapped.range).isEqualTo(16f..30f)
        assertThat(mapped.steps).isEqualTo(27)
        assertThat(mapped.pending).isTrue()
        assertThat(mapped.available).isTrue()
        assertThat(mapped.illustrationRes).isEqualTo(42)
    }

    @Test
    fun mirrorHeat_usesIntegerRangeContractInsteadOfBooleanSwitch() {
        val presentation =
            ClimatePresentation(
                id = ClimateControlId.MIRROR_HEAT,
                title = "Mirror heat",
                categoryKey = "DEFROST",
                categoryTitle = "Defrost",
                expectedKind = ClimateControlKind.INT_RANGE,
                info = "Mirror heat guide",
            )
        val runtimeControl =
            ClimateControl(
                key = "MIRROR_HEAT:1",
                capability =
                    ClimateCapability(
                        id = ClimateControlId.MIRROR_HEAT,
                        propertyId = 0x1440050C,
                        zone = ClimateZone(1, "Left mirror"),
                        kind = ClimateControlKind.INT_RANGE,
                        writable = true,
                        min = 0f,
                        max = 3f,
                    ),
                title = "Mirror heat",
                section = "Defrost",
                status = ClimateValueStatus.AVAILABLE,
                intValue = 2,
            )

        val mapped =
            mapClimateControls(
                state = ClimateState(connected = true, controls = listOf(runtimeControl)),
                presentations = listOf(presentation),
                limitations = "Limits",
                dependencies = "Dependencies",
                onLabel = "On",
                offLabel = "Off",
                unavailableLabel = "Unavailable",
            ).single()

        assertThat(mapped.editor).isEqualTo(VehicleEditorUiKind.SLIDER)
        assertThat(mapped.usesFloatSlider).isFalse()
        assertThat(mapped.numericValue).isEqualTo(2f)
        assertThat(mapped.range).isEqualTo(0f..3f)
        assertThat(mapped.available).isTrue()
    }

    @Test
    fun transientError_isReportedOnceWithoutDisablingEveryControl() {
        val presentations =
            listOf(
                ClimatePresentation(
                    id = ClimateControlId.POWER,
                    title = "Power",
                    categoryKey = "SYSTEM",
                    categoryTitle = "System",
                    expectedKind = ClimateControlKind.TOGGLE,
                    info = "Power guide",
                ),
                ClimatePresentation(
                    id = ClimateControlId.AUTO,
                    title = "Auto",
                    categoryKey = "SYSTEM",
                    categoryTitle = "System",
                    expectedKind = ClimateControlKind.TOGGLE,
                    info = "Auto guide",
                ),
            )

        val controls =
            mapClimateControls(
                state = ClimateState(),
                presentations = presentations,
                limitations = "Limits",
                dependencies = "Dependencies",
                onLabel = "On",
                offLabel = "Off",
                unavailableLabel = "Unavailable",
                transientMessage = "Write rejected",
            )

        assertThat(controls.mapNotNull { it.errorMessage }).containsExactly("Write rejected")
    }
}
