package com.android.car.settings.core.ui

import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFocusEntryStateTest {
    @Test
    fun requestDefaultFocus_replacesOlderRequestWithMonotonicId() {
        val state = SettingsFocusEntryState()

        val first = state.requestDefaultFocus("vehicle")
        val second = state.requestDefaultFocus("display")

        assertEquals(1L, first.requestId)
        assertEquals(2L, second.requestId)
        assertEquals(second, state.pendingRequest)
    }

    @Test
    fun nextDestinationRequest_isClaimedOnlyByADifferentDestination() {
        val state = SettingsFocusEntryState()
        state.onDestinationVisible("applications")

        val request = state.requestNextDestinationFocus()

        assertNull(request.destinationKey)
        assertEquals("applications", request.sourceDestinationKey)
        assertFalse(settingsFocusEntryBelongsTo(request, "applications"))
        assertTrue(settingsFocusEntryBelongsTo(request, "all-applications"))
        assertNull(state.claimPendingFocus("applications"))

        val claimed = state.claimPendingFocus("all-applications")

        assertEquals(request.requestId, claimed?.requestId)
        assertEquals("all-applications", claimed?.destinationKey)
        assertEquals(claimed, state.pendingRequest)
    }

    @Test
    fun prepareForwardNavigation_createsOnlyARotaryRequestAndClearsTouchState() {
        val state = SettingsFocusEntryState()
        state.onContentItemFocused("applications", FocusItemId("all-apps"))

        state.prepareForwardNavigation(isInTouchMode = false)

        assertNull(state.pendingRequest?.destinationKey)
        assertEquals("applications", state.pendingRequest?.sourceDestinationKey)

        state.prepareForwardNavigation(isInTouchMode = true)

        assertNull(state.pendingRequest)
    }

    @Test
    fun dispatchFocusIfCurrent_dispatchesOncePerTargetAndReplacesAnUnavailableTarget() {
        val state = SettingsFocusEntryState()
        val request = state.requestDefaultFocus("sound")
        val staleTarget = target("sound", "sound-volume-1")
        val replacementTarget = target("sound", "sound-volume-2")

        assertTrue(state.dispatchFocusIfCurrent(request.requestId, staleTarget))
        assertFalse(state.dispatchFocusIfCurrent(request.requestId, staleTarget))
        assertEquals(staleTarget, state.dispatchedTarget)
        assertTrue(state.dispatchFocusIfCurrent(request.requestId, replacementTarget))
        assertEquals(replacementTarget, state.dispatchedTarget)
        assertEquals(request, state.pendingRequest)
    }

    @Test
    fun dispatchFocusIfCurrent_ignoresAStaleRequest() {
        val state = SettingsFocusEntryState()
        val staleRequest = state.requestDefaultFocus("vehicle")
        val currentRequest = state.requestDefaultFocus("display")

        assertFalse(state.dispatchFocusIfCurrent(staleRequest.requestId, target("vehicle", "climate")))
        assertNull(state.dispatchedTarget)
        assertEquals(currentRequest, state.pendingRequest)
    }

    @Test
    fun onContentItemFocused_consumesOnlyAfterTheDispatchedTargetReceivesFocus() {
        val state = SettingsFocusEntryState()
        val request = state.requestDefaultFocus("sound")
        val target = target("sound", "sound-volume-1")
        state.dispatchFocusIfCurrent(request.requestId, target)

        assertEquals(
            SettingsFocusEntryResolution.TargetFocused,
            state.onContentItemFocused("sound", target.itemId),
        )
        assertNull(state.pendingRequest)
        assertNull(state.dispatchedTarget)
    }

    @Test
    fun onContentItemFocused_ignoresRestorationBeforeTheNewTargetIsDispatched() {
        val state = SettingsFocusEntryState()
        val request = state.requestDefaultFocus("sound")

        assertNull(state.onContentItemFocused("sound", FocusItemId("settings-item-saved")))
        assertEquals(request, state.pendingRequest)
        assertNull(state.dispatchedTarget)
    }

    @Test
    fun onContentItemFocused_cancelsALateHandoffWhenTheUserMovesToAnotherDetailItem() {
        val state = SettingsFocusEntryState()
        val request = state.requestDefaultFocus("sound")
        state.dispatchFocusIfCurrent(request.requestId, target("sound", "sound-volume-1"))

        assertEquals(
            SettingsFocusEntryResolution.OtherContentFocused,
            state.onContentItemFocused("sound", FocusItemId("settings-item-other")),
        )
        assertNull(state.pendingRequest)
        assertNull(state.dispatchedTarget)
    }

    @Test
    fun settingsLazyFocusListSpec_preservesPhysicalSlotsWhileSkippingDisabledRows() {
        val spec =
            settingsLazyFocusListSpec(
                listOf(
                    SettingsLazyFocusListSlot(),
                    SettingsLazyFocusListSlot("screen-lock", isEnabled = false),
                    SettingsLazyFocusListSlot("device-admin"),
                    SettingsLazyFocusListSlot("clear-credentials", isEnabled = false),
                ),
            )

        assertEquals("device-admin", spec.firstContentFocusId)
        assertEquals(mapOf("device-admin" to 2), spec.itemIndexByFocusId)
    }

    @Test
    fun firstContentItem_waitsForTheInitialRepositorySnapshot() {
        val declared = FocusItemId("declared")
        val discovered = FocusItemId("discovered")

        assertNull(
            settingsFirstContentItemToFocus(
                isContentFocusReady = false,
                declaredItemId = declared,
                discoveredItemId = discovered,
            ),
        )
        assertEquals(
            declared,
            settingsFirstContentItemToFocus(
                isContentFocusReady = true,
                declaredItemId = declared,
                discoveredItemId = discovered,
            ),
        )
    }

    @Test
    fun cancelPendingFocus_clearsTheRequest() {
        val state = SettingsFocusEntryState()
        state.requestDefaultFocus("vehicle")

        state.cancelPendingFocus()

        assertNull(state.pendingRequest)
        assertNull(state.dispatchedTarget)
    }

    @Test
    fun shouldRequestDefaultFocus_requiresAcceptedRotaryNavigationForRootOrChild() {
        assertTrue(
            shouldRequestDefaultFocus(
                isInTouchMode = false,
                navigationAccepted = true,
            ),
        )
        assertFalse(
            shouldRequestDefaultFocus(
                isInTouchMode = true,
                navigationAccepted = true,
            ),
        )
        assertTrue(
            shouldRequestDefaultFocus(isInTouchMode = false, navigationAccepted = true),
        )
        assertFalse(
            shouldRequestDefaultFocus(
                isInTouchMode = false,
                navigationAccepted = false,
            ),
        )
    }

    private fun target(
        destinationKey: String,
        focusId: String,
    ): RotaryFocusTarget =
        RotaryFocusTarget(
            areaId = FocusAreaId("settings-detail-$destinationKey"),
            itemId = FocusItemId("settings-item-$focusId"),
        )
}
