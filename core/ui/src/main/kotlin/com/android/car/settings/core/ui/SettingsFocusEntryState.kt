package com.android.car.settings.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusTarget

/** One explicit, forward-navigation request to focus a destination's default content item. */
@Immutable
data class SettingsFocusEntryRequest(
    val requestId: Long,
    val destinationKey: String?,
    val sourceDestinationKey: String? = null,
)

/** Exact requests belong to their key; forward requests belong to any destination but the source. */
internal fun settingsFocusEntryBelongsTo(
    request: SettingsFocusEntryRequest,
    destinationKey: String,
): Boolean =
    request.destinationKey?.let { it == destinationKey }
        ?: (request.sourceDestinationKey != destinationKey)

/** One physical LazyColumn slot and its optional logical CCP focus item. */
@Immutable
data class SettingsLazyFocusListSlot(
    val focusId: String? = null,
    val isEnabled: Boolean = true,
)

/** Default target plus the physical LazyColumn positions B-Material must reveal. */
@Immutable
data class SettingsLazyFocusListSpec(
    val firstContentFocusId: String?,
    val itemIndexByFocusId: Map<String, Int>,
)

/**
 * Builds a focus spec without losing non-focusable headers, placeholders, or disabled rows.
 *
 * The physical index remains the index in [slots], while only enabled rows participate in
 * rotary order and may become the root destination's default content focus.
 */
fun settingsLazyFocusListSpec(slots: List<SettingsLazyFocusListSlot>): SettingsLazyFocusListSpec {
    val itemIndexByFocusId = linkedMapOf<String, Int>()
    slots.forEachIndexed { index, slot ->
        val focusId = slot.focusId?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
        if (slot.isEnabled) {
            require(itemIndexByFocusId.put(focusId, index) == null) {
                "Duplicate lazy focus id: $focusId"
            }
        }
    }
    return SettingsLazyFocusListSpec(
        firstContentFocusId = itemIndexByFocusId.keys.firstOrNull(),
        itemIndexByFocusId = itemIndexByFocusId,
    )
}

/** How a pending destination handoff was settled after content gained focus. */
internal enum class SettingsFocusEntryResolution {
    /** The exact target handed to the rotary controller received real focus. */
    TargetFocused,

    /** A different detail item won focus, so replaying the handoff would be disruptive. */
    OtherContentFocused,
}

/**
 * Coordinates a one-shot focus handoff from the persistent shell into a destination.
 *
 * Root destinations deliberately do not use an unconditional fallback: doing so would steal the
 * shell Search focus during cold start. The activity creates a request only after an accepted
 * rotary forward navigation. The destination claims it and consumes it only after the requested
 * content target receives real focus.
 */
@Stable
class SettingsFocusEntryState {
    private var nextRequestId by mutableStateOf(0L)
    private var currentRequest by mutableStateOf<SettingsFocusEntryRequest?>(null)
    private var currentDispatchedTarget by mutableStateOf<RotaryFocusTarget?>(null)
    private var visibleDestinationKey: String? = null

    val pendingRequest: SettingsFocusEntryRequest?
        get() = currentRequest

    /** The target currently owned by the request, or null until content has materialized. */
    internal val dispatchedTarget: RotaryFocusTarget?
        get() = currentDispatchedTarget

    /** Replaces any older request and returns the new token. */
    fun requestDefaultFocus(destinationKey: String): SettingsFocusEntryRequest {
        require(destinationKey.isNotBlank()) { "destinationKey must not be blank" }
        val request =
            SettingsFocusEntryRequest(
                requestId = ++nextRequestId,
                destinationKey = destinationKey,
            )
        currentRequest = request
        currentDispatchedTarget = null
        return request
    }

    /** Records the destination currently owning the detail pane. */
    internal fun onDestinationVisible(destinationKey: String) {
        require(destinationKey.isNotBlank()) { "destinationKey must not be blank" }
        visibleDestinationKey = destinationKey
    }

    /** Creates an unbound request which only the next destination may claim. */
    internal fun requestNextDestinationFocus(): SettingsFocusEntryRequest {
        val request =
            SettingsFocusEntryRequest(
                requestId = ++nextRequestId,
                destinationKey = null,
                sourceDestinationKey = visibleDestinationKey,
            )
        currentRequest = request
        currentDispatchedTarget = null
        return request
    }

    /** Prepares a forward action without leaving a stale rotary request behind for touch. */
    fun prepareForwardNavigation(isInTouchMode: Boolean) {
        cancelPendingFocus()
        if (!isInTouchMode) requestNextDestinationFocus()
    }

    /** Binds an unbound forward request to a destination other than its source. */
    internal fun claimPendingFocus(destinationKey: String): SettingsFocusEntryRequest? {
        require(destinationKey.isNotBlank()) { "destinationKey must not be blank" }
        val request = currentRequest ?: return null
        if (!settingsFocusEntryBelongsTo(request, destinationKey)) return null
        if (request.destinationKey != null) return request
        return request.copy(destinationKey = destinationKey).also { currentRequest = it }
    }

    /** Cancels a request which can no longer belong to the visible forward-navigation action. */
    fun cancelPendingFocus() {
        currentRequest = null
        currentDispatchedTarget = null
    }

    /**
     * Claims [target] for the live request exactly once.
     *
     * A new target is deliberately dispatchable when data changes before focus is delivered: the
     * controller can then replace a pending lazy-item request rather than waiting for an item
     * which has already left composition.
     */
    internal fun dispatchFocusIfCurrent(
        requestId: Long,
        target: RotaryFocusTarget,
    ): Boolean {
        if (currentRequest?.requestId != requestId) return false
        if (currentDispatchedTarget == target) return false
        currentDispatchedTarget = target
        return true
    }

    /**
     * Settles a request only after a real detail [itemId] gains focus.
     *
     * A different item means the user or the platform has already established an intentional
     * detail focus, so the delayed request must not steal it later.
     */
    internal fun onContentItemFocused(
        destinationKey: String,
        itemId: FocusItemId,
    ): SettingsFocusEntryResolution? {
        // A forward click originates from the focused row, so this is a stronger source identity
        // than composition order while Navigation briefly keeps adjacent destinations alive.
        visibleDestinationKey = destinationKey
        val request = currentRequest ?: return null
        if (request.destinationKey != destinationKey) return null
        // RotaryFocusDestination may restore a previously saved item before the new destination's
        // requested target has materialized. That restoration is not user intent and must not
        // consume the new handoff; once a target has been dispatched, a different item is a real
        // focus choice and is handled by the cancellation branch below.
        val dispatchedTarget = currentDispatchedTarget ?: return null
        val resolution =
            if (dispatchedTarget.itemId == itemId) {
                SettingsFocusEntryResolution.TargetFocused
            } else {
                SettingsFocusEntryResolution.OtherContentFocused
            }
        currentRequest = null
        currentDispatchedTarget = null
        return resolution
    }
}

@Composable
fun rememberSettingsFocusEntryState(): SettingsFocusEntryState = remember { SettingsFocusEntryState() }

internal val LocalSettingsFocusEntryState = staticCompositionLocalOf<SettingsFocusEntryState?> { null }

/** Receives real focus events from content items composed inside [SettingsScaffold]. */
internal val LocalSettingsContentFocusObserver =
    staticCompositionLocalOf<(FocusItemId) -> Unit> { {} }

fun shouldRequestDefaultFocus(
    isInTouchMode: Boolean,
    navigationAccepted: Boolean,
): Boolean = !isInTouchMode && navigationAccepted
