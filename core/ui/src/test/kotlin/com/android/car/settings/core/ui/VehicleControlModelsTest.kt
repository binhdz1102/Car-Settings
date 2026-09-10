package com.android.car.settings.core.ui

import androidx.compose.ui.geometry.Size
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class VehicleControlModelsTest {
    @Test
    fun presentation_keepsSelectedZoneAndCategoryOrderStable() {
        val zones =
            listOf(
                VehicleZoneOption(areaId = 0, label = "Global"),
                VehicleZoneOption(areaId = 1, label = "Driver"),
                VehicleZoneOption(areaId = 4, label = "Passenger"),
            )
        assertEquals(4, effectiveVehicleAreaId(zones, selectedAreaId = 4))
        assertEquals(1, effectiveVehicleAreaId(zones, selectedAreaId = 99))

        val controls =
            listOf(
                minimalControl(key = "a", categoryKey = "Seat", supported = true),
                minimalControl(key = "b", categoryKey = "Door", supported = false),
                minimalControl(key = "c", categoryKey = "Seat", supported = true),
            )
        assertEquals(listOf("Seat"), vehicleCategoryKeys(visibleVehicleControls(controls, false)))
        assertEquals(listOf("Seat", "Door"), vehicleCategoryKeys(visibleVehicleControls(controls, true)))
    }

    @Test
    fun overviewPresentation_precomputesStableCategoriesAndFocusOrder() {
        val supportedSeat = minimalControl(key = "seat-a", categoryKey = "Seat", supported = true)
        val duplicateSeat = supportedSeat.copy(summary = "Updated")
        val unsupportedDoor = minimalControl(key = "door-a", categoryKey = "Door", supported = false)

        val presentation =
            buildVehicleOverviewPresentation(
                title = "Vehicle",
                controls = listOf(supportedSeat, duplicateSeat, unsupportedDoor),
                controlsForDisplay = listOf(supportedSeat, duplicateSeat, unsupportedDoor),
            )

        assertEquals(listOf("Seat", "Door"), presentation.categories.map { it.key })
        assertEquals(
            listOf("seat-a"),
            presentation.categories
                .first()
                .summaryControls
                .map { it.key },
        )
        assertEquals(true, presentation.hasUnavailableControls)
        assertEquals(presentation.focusOrder.first(), presentation.firstFocusId)
        assertEquals(
            listOf(
                "${presentation.areaId}-${vehicleFocusKey("Seat")}",
                "${presentation.areaId}-${vehicleFocusKey("Door")}",
            ),
            presentation.focusOrder.drop(1).map { it.value },
        )
    }

    @Test
    fun selectedControl_preservesIdentityWhenValueSummaryChanges() {
        val original =
            minimalControl(key = "seat-a", categoryKey = "Seat", supported = true)
                .copy(areaId = 1)
        val refreshed = original.copy(summary = "New value", booleanValue = true)

        assertEquals(
            refreshed,
            selectVehicleControl(
                listOf(refreshed),
                selectedKey = original.key,
                selectedAreaId = original.areaId,
            ),
        )
        assertEquals(
            refreshed,
            selectVehicleControl(listOf(refreshed), selectedKey = "missing", selectedAreaId = 1),
        )
    }

    @Test
    fun controlsDoNotLeakAcrossUnrelatedPropertyAreas() {
        val controls =
            listOf(
                VehicleControlUiModel(
                    key = "DOOR_LOCK",
                    propertyId = 1,
                    areaId = 16,
                    section = "Doors",
                    title = "Door lock",
                    summary = "Summary",
                    info = "Info",
                    limitations = "Limits",
                    dependencies = "Dependencies",
                    editor = VehicleEditorUiKind.SWITCH,
                    readable = false,
                    writable = false,
                    supported = false,
                    available = false,
                    pending = false,
                ),
            )

        assertEquals(emptyList<VehicleControlUiModel>(), filterVehicleControlsForArea(controls, selectedAreaId = 1))
    }

    @Test
    fun overviewFallbackTargetsRegisteredContentArea() {
        val presentation =
            buildVehicleOverviewPresentation(
                title = "Vehicle",
                controls = listOf(minimalControl("seat", "Seat", supported = true)),
                controlsForDisplay = listOf(minimalControl("seat", "Seat", supported = true)),
            )

        val target = vehicleOverviewFallbackTarget(presentation, "content-area", loading = false)

        assertEquals(FocusAreaId("content-area"), target?.areaId)
        assertEquals(presentation.firstFocusId, target?.itemId)
    }

    @Test
    fun emptyOverviewFallbackTargetsItsActionOnlyAfterLoading() {
        val presentation =
            buildVehicleOverviewPresentation(
                title = "Vehicle",
                controls = emptyList(),
                controlsForDisplay = emptyList(),
            )

        val target = vehicleOverviewFallbackTarget(presentation, "content-area", loading = false)

        assertEquals(FocusAreaId("content-area"), target?.areaId)
        assertEquals(FocusItemId("content-area-action"), target?.itemId)
        assertEquals(null, vehicleOverviewFallbackTarget(presentation, "content-area", loading = true))
    }

    @Test
    fun zonesWithOverlappingBitsRemainScopedToTheirCategory() {
        val zones =
            listOf(
                VehicleZoneOption(areaId = 1, label = "Left door", propertyFamily = "DOORS"),
                VehicleZoneOption(areaId = 1, label = "Windshield", propertyFamily = "WINDOWS"),
                VehicleZoneOption(areaId = 1, label = "Left mirror", propertyFamily = "MIRRORS"),
            )

        assertEquals(listOf("Left door"), vehicleZonesForCategory(zones, "DOORS").map { it.label })
        assertEquals(listOf("Windshield"), vehicleZonesForCategory(zones, "WINDOWS").map { it.label })
        assertEquals(listOf("Left mirror"), vehicleZonesForCategory(zones, "MIRRORS").map { it.label })
    }

    @Test
    fun unavailableZoneSelectorHasNoRotaryStops() {
        val zones =
            listOf(
                VehicleZoneOption(areaId = 1, label = "Driver"),
                VehicleZoneOption(areaId = 2, label = "Passenger", enabled = false),
            )

        assertEquals(false, vehicleZoneSelectionEnabled(connected = false, hasControls = true))
        assertEquals(emptyList<Int>(), actionableVehicleZoneIds(zones, selectionEnabled = false))
        assertEquals(listOf(1), actionableVehicleZoneIds(zones, selectionEnabled = true))
    }

    @Test
    fun rotaryEligibility_excludesUnavailableAndReadOnlyRows() {
        val base =
            VehicleControlUiModel(
                key = "SEAT_HEAT",
                propertyId = 2,
                areaId = 1,
                section = "Seat",
                title = "Seat heat",
                summary = "On",
                info = "Info",
                limitations = "Limits",
                dependencies = "Dependencies",
                editor = VehicleEditorUiKind.SWITCH,
                readable = true,
                writable = true,
                supported = true,
                available = true,
                pending = false,
                booleanValue = false,
            )

        assertEquals(true, isVehicleControlRotaryActionable(base, connected = true, restricted = false))
        assertEquals(
            false,
            isVehicleControlRotaryActionable(base.copy(available = false), connected = true, restricted = false),
        )
        assertEquals(
            false,
            isVehicleControlRotaryActionable(base.copy(writable = false), connected = true, restricted = false),
        )
        assertEquals(
            false,
            isVehicleControlRotaryActionable(base.copy(requiresUnrestrictedUx = true), connected = true, restricted = true),
        )
    }

    @Test
    fun rotaryEligibility_requiresAConcreteValueForEditors() {
        val noValue =
            VehicleControlUiModel(
                key = "LEVEL",
                propertyId = 3,
                areaId = 0,
                section = "Test",
                title = "Level",
                summary = "Unavailable",
                info = "Info",
                limitations = "Limits",
                dependencies = "Dependencies",
                editor = VehicleEditorUiKind.SLIDER,
                readable = true,
                writable = true,
                supported = true,
                available = true,
                pending = false,
                numericValue = null,
            )
        assertEquals(false, isVehicleControlRotaryActionable(noValue, connected = true, restricted = false))
    }

    @Test
    fun interactionModel_keepsCenterActionPureAndCapabilityGated() {
        val switch =
            minimalControl(key = "SWITCH", categoryKey = "Test", supported = true).copy(
                editor = VehicleEditorUiKind.SWITCH,
                available = true,
                writable = true,
                booleanValue = false,
            )
        val enabled = buildVehicleControlInteraction(switch, connected = true, restricted = false)
        assertEquals(true, enabled.canWrite)
        assertEquals(VehicleCenterAction.Toggle(true), enabled.centerAction)

        val blocked = buildVehicleControlInteraction(switch, connected = false, restricted = false)
        assertEquals(false, blocked.canWrite)
        assertEquals(VehicleCenterAction.OpenInfo, blocked.centerAction)
    }

    @Test
    fun interactionModel_skipsDisabledEnumAndFallsBackForNonNumericKeys() {
        val numeric =
            minimalControl(key = "MODE", categoryKey = "Test", supported = true).copy(
                editor = VehicleEditorUiKind.ENUM,
                available = true,
                writable = true,
                selectedEnumKey = "0",
                enumOptions =
                    listOf(
                        VehicleEnumOption("0", "Off"),
                        VehicleEnumOption("1", "Low", enabled = false),
                        VehicleEnumOption("2", "High"),
                    ),
            )
        assertEquals(
            VehicleCenterAction.SelectEnum(2),
            buildVehicleControlInteraction(numeric, connected = true, restricted = false).centerAction,
        )

        val textKey = numeric.copy(selectedEnumKey = "0", enumOptions = listOf(VehicleEnumOption("high", "High")))
        assertEquals(
            VehicleCenterAction.OpenInfo,
            buildVehicleControlInteraction(textKey, connected = true, restricted = false).centerAction,
        )
    }

    @Test
    fun focusLayout_keepsActionOrderAndStableIdentityAcrossValueChanges() {
        val first =
            VehicleControlUiModel(
                key = "SEAT_HEAT",
                propertyId = 2,
                areaId = 1,
                section = "Seat",
                title = "Seat heat",
                summary = "Off",
                info = "Info",
                limitations = "Limits",
                dependencies = "Dependencies",
                editor = VehicleEditorUiKind.SWITCH,
                readable = true,
                writable = true,
                supported = true,
                available = true,
                pending = false,
                booleanValue = false,
            )
        val second = first.copy(key = "SEAT_VENT", title = "Seat ventilation", booleanValue = true)
        val initial =
            buildVehicleControlFocusLayout(
                controls = listOf(first, second),
                selectedAreaId = 1,
                controlsAreaId = "controls-seat",
                connected = true,
                restricted = false,
                hasRetryAction = true,
            )
        val refreshed =
            buildVehicleControlFocusLayout(
                controls = listOf(first.copy(summary = "On"), second.copy(summary = "Off")),
                selectedAreaId = 1,
                controlsAreaId = "controls-seat",
                connected = true,
                restricted = false,
                hasRetryAction = true,
            )

        assertEquals(5, initial.focusOrder.size)
        assertEquals(initial.focusOrder, refreshed.focusOrder)
        assertEquals(initial.entries.map { it.focusId }, refreshed.entries.map { it.focusId })
        assertEquals(initial.entries.map { it.infoFocusId }, refreshed.entries.map { it.infoFocusId })
        assertEquals(initial.focusOrder[1], initial.firstMeaningfulFocusId)
    }

    @Test
    fun focusLayout_keepsReadOnlyRowsAccessibleThroughInfoWithoutCreatingDeadStops() {
        val readOnlyStatus =
            VehicleControlUiModel(
                key = "SEAT_OCCUPANCY",
                propertyId = 4,
                areaId = 1,
                section = "Seat",
                title = "Occupancy",
                summary = "Passenger detected",
                info = "Info",
                limitations = "Limits",
                dependencies = "Dependencies",
                editor = VehicleEditorUiKind.STATUS,
                readable = true,
                writable = false,
                supported = true,
                available = true,
                pending = false,
            )

        val layout =
            buildVehicleControlFocusLayout(
                controls = listOf(readOnlyStatus),
                selectedAreaId = 1,
                controlsAreaId = "controls-seat",
                connected = true,
                restricted = false,
                hasRetryAction = false,
            )

        assertEquals(false, layout.entries.single().rotaryActionable)
        assertEquals(listOf(layout.entries.single().infoFocusId), layout.focusOrder)
        assertEquals(layout.entries.single().infoFocusId, layout.firstMeaningfulFocusId)
    }

    @Test
    fun focusLayout_canRemoveInfoStopsWhenVisualPolicyDisablesGuide() {
        val control = minimalControl(key = "AUTO", categoryKey = "Climate", supported = true)
        val layout =
            buildVehicleControlFocusLayout(
                controls = listOf(control),
                selectedAreaId = 1,
                controlsAreaId = "controls-climate",
                connected = true,
                restricted = false,
                hasRetryAction = false,
                includeInfo = false,
            )

        assertEquals(emptyList<FocusItemId>(), layout.focusOrder)
        assertNull(layout.firstMeaningfulFocusId)
    }

    @Test
    fun focusLayout_rejectsDuplicateStableIds() {
        val duplicate = minimalControl(key = "DUPLICATE", categoryKey = "Test", supported = true).copy(areaId = 1)

        assertThrows(IllegalStateException::class.java) {
            buildVehicleControlFocusLayout(
                controls = listOf(duplicate, duplicate),
                selectedAreaId = 1,
                controlsAreaId = "vehicle-controls-test",
                connected = true,
                restricted = false,
                hasRetryAction = false,
            )
        }
    }

    @Test
    fun sliderFraction_clampsToRange() {
        assertEquals(0f, vehicleSliderFraction(-20f, 0f..100f))
        assertEquals(0.25f, vehicleSliderFraction(25f, 0f..100f))
        assertEquals(1f, vehicleSliderFraction(120f, 0f..100f))
    }

    @Test
    fun sliderFraction_invalidInputFallsBackToZero() {
        assertEquals(0f, vehicleSliderFraction(Float.NaN, 0f..100f))
        assertEquals(0f, vehicleSliderFraction(5f, 10f..10f))
        assertEquals(0f, vehicleSliderFraction(5f, Float.NEGATIVE_INFINITY..10f))
    }

    @Test
    fun sliderSync_keepsDraggedThumbUntilAcknowledgement() {
        val dragging =
            resolveVehicleSliderValue(
                externalValue = 10f,
                currentValue = 80f,
                interactionActive = true,
                submittedValue = null,
                pending = false,
                hasError = false,
            )
        assertEquals(80f, dragging.value)
        assertEquals(false, dragging.clearSubmittedValue)

        val pending =
            resolveVehicleSliderValue(
                externalValue = 10f,
                currentValue = 80f,
                interactionActive = false,
                submittedValue = 80f,
                pending = true,
                hasError = false,
            )
        assertEquals(80f, pending.value)

        val staleCallback =
            resolveVehicleSliderValue(
                externalValue = 10f,
                currentValue = 80f,
                interactionActive = false,
                submittedValue = 80f,
                pending = false,
                hasError = false,
            )
        assertEquals(80f, staleCallback.value)
        assertEquals(false, staleCallback.clearSubmittedValue)

        val acknowledgement =
            resolveVehicleSliderValue(
                externalValue = 80f,
                currentValue = 80f,
                interactionActive = false,
                submittedValue = 80f,
                pending = false,
                hasError = false,
            )
        assertEquals(80f, acknowledgement.value)
        assertEquals(true, acknowledgement.clearSubmittedValue)
    }

    @Test
    fun sliderSync_revertsToExternalValueOnWriteError() {
        val error =
            resolveVehicleSliderValue(
                externalValue = 10f,
                currentValue = 80f,
                interactionActive = false,
                submittedValue = 80f,
                pending = false,
                hasError = true,
            )
        assertEquals(10f, error.value)
        assertEquals(true, error.clearSubmittedValue)
    }

    @Test
    fun sliderStep_isBoundedAndHonoursDeclaredSteps() {
        assertEquals(5f, vehicleSliderStep(0f..100f, steps = 19))
        assertEquals(1f, vehicleSliderStep(0f..20f, steps = 0))
        assertEquals(5f, vehicleSliderStep(0f..100f, steps = 0))
        assertEquals(1f, vehicleSliderStep(10f..10f, steps = 4))
    }

    @Test
    fun sliderUiSpec_boundsCenterMarkerAndDiscreteTicks() {
        assertEquals(.5f, vehicleSliderCenterFraction(0f, -10f..10f))
        assertEquals(null, vehicleSliderCenterFraction(0f, 0f..10f))
        assertEquals(null, vehicleSliderCenterFraction(Float.NaN, -10f..10f))
        val spec = VehicleSliderUiSpec(showTicks = true)
        assertEquals(true, vehicleSliderShowsTicks(spec, steps = 5))
        assertEquals(false, vehicleSliderShowsTicks(spec, steps = 20))
    }

    @Test
    fun normalizedPreviewGeometry_clampsValuesAndScalesAnchors() {
        assertEquals(.5f, normalizedVehiclePreviewValue(5f, 0f..10f))
        assertEquals(1f, normalizedVehiclePreviewValue(20f, 0f..10f))
        assertEquals(.25f, normalizedVehiclePreviewValue(null, 0f..10f, fallback = .25f))
        val offset = VehiclePreviewAnchor(.25f, .75f).offsetIn(Size(400f, 200f))
        assertEquals(100f, offset.x)
        assertEquals(150f, offset.y)
    }

    @Test
    fun rotarySliderValue_clampsBurstsAndRejectsInvalidInputs() {
        assertEquals(1f, nextVehicleSliderValue(.9f, detents = 2f, step = .2f, valueRange = 0f..1f), 0.0001f)
        assertEquals(0f, nextVehicleSliderValue(.1f, detents = -2f, step = .2f, valueRange = 0f..1f), 0.0001f)
        assertEquals(0f, nextVehicleSliderValue(Float.NaN, detents = 1f, step = .2f, valueRange = 0f..1f), 0.0001f)
        assertEquals(0f, nextVehicleSliderValue(.2f, detents = 1f, step = Float.POSITIVE_INFINITY, valueRange = 0f..1f), 0.0001f)
    }

    @Test
    fun nextEnumOption_skipsDisabledAndWrapsUnknownSelection() {
        val options =
            listOf(
                VehicleEnumOption("0", "Off"),
                VehicleEnumOption("1", "Low", enabled = false),
                VehicleEnumOption("2", "High"),
            )
        assertEquals("2", nextVehicleEnumOption(options, "0")?.key)
        assertEquals("0", nextVehicleEnumOption(options, "2")?.key)
        assertEquals("0", nextVehicleEnumOption(options, "missing")?.key)
        assertEquals(null, nextVehicleEnumOption(options.map { it.copy(enabled = false) }, "0"))
    }

    @Test
    fun topViewZone_rejectsCoordinatesOutsideImage() {
        assertThrows(IllegalArgumentException::class.java) {
            VehicleTopViewZone(
                areaId = 1,
                label = "Driver",
                shortLabel = "D",
                horizontalFraction = 1.1f,
                verticalFraction = 0.5f,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            VehicleTopViewZone(
                areaId = 1,
                label = "Driver",
                shortLabel = "D",
                horizontalFraction = 0.5f,
                verticalFraction = Float.NaN,
            )
        }
    }

    private fun minimalControl(
        key: String,
        categoryKey: String,
        supported: Boolean,
    ) = VehicleControlUiModel(
        key = key,
        propertyId = key.hashCode(),
        areaId = 0,
        section = categoryKey,
        categoryKey = categoryKey,
        title = key,
        summary = "",
        info = "",
        limitations = "",
        dependencies = "",
        editor = VehicleEditorUiKind.STATUS,
        readable = false,
        writable = false,
        supported = supported,
        available = false,
        pending = false,
    )
}
