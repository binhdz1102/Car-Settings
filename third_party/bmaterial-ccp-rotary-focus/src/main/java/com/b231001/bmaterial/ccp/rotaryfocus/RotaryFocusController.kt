package com.b231001.bmaterial.ccp.rotaryfocus

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.doOnPreDraw
import com.android.car.ui.FocusParkingView
import java.lang.ref.WeakReference
import java.util.LinkedHashMap

private const val GEOMETRY_VALIDATION_DELAY_MILLIS = 120L

/**
 * Coordinates real View focus across Compose destinations and dialog windows.
 *
 * This controller never mirrors focus into the Compose focus system. Its state is derived solely
 * from [View.OnFocusChangeListener] callbacks emitted by [FocusItemView].
 */
public class RotaryFocusController(
    public val validationMode: RotaryValidationMode = RotaryValidationMode.Log
) {
    private val items = LinkedHashMap<RotaryFocusTarget, WeakReference<FocusItemView>>()
    private val areas = LinkedHashMap<FocusAreaId, WeakReference<RotaryFocusAreaView>>()
    private val parkingViews = LinkedHashMap<String, WeakReference<FocusParkingView>>()
    private val savedDestinations = LinkedHashMap<String, RotaryFocusTarget>()
    private val reportedOverlaps = mutableSetOf<String>()
    private val _isDirectManipulationActiveState = mutableStateOf(false)

    /** Observable Compose state indicating if any registered item is in Direct Manipulation mode. */
    public val isDirectManipulationActiveState: State<Boolean> = _isDirectManipulationActiveState

    /**
     * Compose can detach the outgoing AndroidView one frame after attaching the incoming one.
     * Validate after that hand-off settles so a one-frame replacement is not reported as a
     * persistent geometry defect. A real overlap remains visible to the same check and is still
     * logged/failed according to [validationMode].
     */
    private val geometryHandler = Handler(Looper.getMainLooper())
    private var geometryValidationPosted: Boolean = false
    private var pendingFocus: RotaryFocusTarget? = null
    private var pendingFallback: RotaryFocusTarget? = null
    private var deferredDestinationFocus: DeferredDestinationFocus? = null

    public var currentFocusTarget: RotaryFocusTarget? = null
        private set

    public val isDirectManipulationActive: Boolean
        get() = _isDirectManipulationActiveState.value ||
            currentFocusTarget?.let { items[it]?.get()?.isInDirectManipulationMode } == true ||
            items.values.any { it.get()?.isInDirectManipulationMode == true }

    public fun saveFocus(destinationKey: String): RotaryFocusTarget? {
        require(destinationKey.isNotBlank()) { "destinationKey must not be blank" }
        val current = currentFocusTarget?.takeIf { target ->
            items[target]?.get()?.destinationKey == destinationKey
        }
        if (current != null) savedDestinations[destinationKey] = current
        val target = savedDestinations[destinationKey]
        RotaryFocusLogger.debug {
            "Destination focus saved; destination=$destinationKey target=$target"
        }
        return target
    }

    public fun restoreFocus(
        destinationKey: String,
        fallback: RotaryFocusTarget? = null
    ): Boolean {
        require(destinationKey.isNotBlank()) { "destinationKey must not be blank" }
        deferredDestinationFocus = null
        val saved = savedDestinations[destinationKey]
        val target = saved ?: fallback
        RotaryFocusLogger.debug {
            "Destination focus restore requested; destination=$destinationKey target=$target"
        }
        return target != null && requestFocusInternal(
            target = target,
            fallback = fallback?.takeIf { saved != null && it != saved },
            replaceRequest = true
        )
    }

    /**
     * Records the restoration target without moving native View focus while the window is in
     * touch mode. The first rotary scroll activates this request and is consumed by that visible
     * focus hand-off, so entering a destination never paints a phantom selection after a tap.
     */
    public fun deferRestoreFocus(
        destinationKey: String,
        fallback: RotaryFocusTarget? = null
    ): Boolean {
        require(destinationKey.isNotBlank()) { "destinationKey must not be blank" }
        val saved = savedDestinations[destinationKey]
        val target = saved ?: fallback
        deferredDestinationFocus = target?.let {
            DeferredDestinationFocus(
                target = it,
                fallback = fallback?.takeIf { candidate -> saved != null && candidate != saved }
            )
        }
        RotaryFocusLogger.debug {
            "Destination focus deferred; destination=$destinationKey target=$target"
        }
        return target != null
    }

    internal val hasDeferredFocusRequest: Boolean
        get() = deferredDestinationFocus != null

    internal fun activateDeferredFocus(): Boolean {
        val deferred = deferredDestinationFocus ?: return false
        deferredDestinationFocus = null
        requestFocusInternal(
            target = deferred.target,
            fallback = deferred.fallback,
            replaceRequest = true
        )
        return true
    }

    public fun requestFocus(target: RotaryFocusTarget): Boolean =
        requestFocusInternal(target, fallback = null, replaceRequest = true)

    private fun requestFocusInternal(
        target: RotaryFocusTarget,
        fallback: RotaryFocusTarget?,
        replaceRequest: Boolean
    ): Boolean {
        if (replaceRequest) {
            pendingFocus?.let { pending ->
                areas[pending.areaId]?.get()?.cancelPendingLogicalFocus(pending.itemId)
            }
            pendingFocus = target
            pendingFallback = fallback
        } else if (pendingFocus != target) {
            return false
        }
        val item = items[target]?.get()
        if (item == null) {
            val area = areas[target.areaId]?.get()
            val revealAccepted = area?.requestReveal(target.itemId) == true
            RotaryFocusLogger.debug {
                "Focus request queued; target=$target registered=false " +
                    "revealAccepted=$revealAccepted fallback=$pendingFallback"
            }
            if (area != null && !revealAccepted) {
                area.doOnPreDraw {
                    if (pendingFocus != target) return@doOnPreDraw
                    if (items[target]?.get() == null) {
                        failPendingFocus(target, "item-unavailable-after-area-layout")
                    } else {
                        retryPendingFocus(target)
                    }
                }
            }
            return false
        }

        if (!item.canTakeRotaryFocus()) {
            if (!item.isEnabled || !item.isFocusable) {
                return failPendingFocus(target, "item-disabled-or-not-focusable")
            }
            RotaryFocusLogger.debug {
                "Focus request queued; target=$target registered=true revealAccepted=false " +
                    "fallback=$pendingFallback"
            }
            if (item.isAttachedToWindow) {
                item.doOnPreDraw { retryPendingFocus(target) }
            }
            return false
        }

        areas[target.areaId]?.get()?.prepareControllerEntry(item)
        item.post {
            if (pendingFocus != target) {
                RotaryFocusLogger.debug {
                    "Stale focus request skipped; target=$target currentPending=$pendingFocus"
                }
                return@post
            }
            val success = item.canTakeRotaryFocus() && item.requestRotaryFocus()
            if (success) {
                // Restoring a parent window after a dialog can target a View which Android kept
                // focused. requestFocus() then succeeds without another OnFocusChange callback.
                // Re-synchronize from the real View state so controller state cannot go stale.
                if (item.isFocused && currentFocusTarget != target) {
                    onItemFocusChanged(item, hasFocus = true)
                }
                clearPendingFocus(target)
            } else if (!item.isEnabled || !item.isFocusable) {
                failPendingFocus(target, "item-became-unfocusable-before-execution")
            }
            RotaryFocusLogger.debug {
                "Focus request executed; target=$target viewId=${item.id} success=$success"
            }
        }
        return true
    }

    /** Enters/exits advanced DM mode for an item configured with [DirectManipulationConfig]. */
    public fun setDirectManipulationMode(
        target: RotaryFocusTarget,
        enabled: Boolean
    ): Boolean {
        val item = items[target]?.get() ?: return false
        if (enabled && !item.isFocused) {
            RotaryFocusLogger.warning {
                "External DM request rejected because item is not focused; target=$target"
            }
            return false
        }
        return item.updateDirectManipulationMode(enabled, "controller-api")
    }

    /** Exits direct manipulation mode on any currently active focus item. */
    public fun exitDirectManipulation(): Boolean {
        var handled = false
        val current = currentFocusTarget?.let { items[it]?.get() }
        if (current?.isInDirectManipulationMode == true) {
            if (current.updateDirectManipulationMode(false, "exit-dm")) {
                handled = true
            }
        }
        for (itemRef in items.values) {
            val item = itemRef.get()
            if (item?.isInDirectManipulationMode == true) {
                if (item.updateDirectManipulationMode(false, "exit-dm")) {
                    handled = true
                }
            }
        }
        _isDirectManipulationActiveState.value = false
        RotaryFocusLogger.debug { "exitDirectManipulation executed; handled=$handled" }
        return handled
    }

    internal fun onItemDirectManipulationModeChanged(item: FocusItemView, enabled: Boolean) {
        val anyActive = enabled || items.values.any { it.get()?.isInDirectManipulationMode == true }
        _isDirectManipulationActiveState.value = anyActive
        RotaryFocusLogger.debug {
            "Controller DM state changed; target=${item.target} enabled=$enabled anyActive=$anyActive"
        }
    }

    /** Parks focus in the active window, or in [hostId] when explicitly supplied. */
    public fun parkFocus(hostId: String? = null): Boolean {
        val parking = hostId?.let { parkingViews[it]?.get() }
            ?: parkingViews.values.asSequence()
                .mapNotNull(WeakReference<FocusParkingView>::get)
                .lastOrNull { it.isAttachedToWindow && it.hasWindowFocus() }
            ?: parkingViews.values.asSequence()
                .mapNotNull(WeakReference<FocusParkingView>::get)
                .lastOrNull { it.isAttachedToWindow }
        val success = parking?.parkFocus() == true
        RotaryFocusLogger.debug { "Controller parked focus; host=$hostId success=$success" }
        return success
    }

    internal fun parkFocusOwnedBy(destinationKey: String): Boolean {
        val target = currentFocusTarget ?: return false
        val owner = items[target]?.get()?.destinationKey
        if (owner != destinationKey) {
            RotaryFocusLogger.debug {
                "Destination park skipped; destination=$destinationKey current=$target owner=$owner"
            }
            return false
        }
        return parkFocus()
    }

    /** Returns a detailed snapshot suitable for on-device log collection. */
    public fun dumpHierarchy(): String = buildString {
        appendLine("RotaryFocusController(current=$currentFocusTarget, pending=$pendingFocus)")
        appendLine("Areas:")
        areas.forEach { (id, reference) ->
            val area = reference.get()
            appendLine(
                "  $id -> viewId=${area?.id}, attached=${area?.isAttachedToWindow}, " +
                    "shown=${area?.isShown}, allowed=${area?.isFocusAllowed}"
            )
        }
        appendLine("Items:")
        items.forEach { (target, reference) ->
            val item = reference.get()
            appendLine(
                "  $target -> viewId=${item?.id}, attached=${item?.isAttachedToWindow}, " +
                    "shown=${item?.isShown}, enabled=${item?.isEnabled}, " +
                    "focusable=${item?.isFocusable}, focused=${item?.isFocused}, " +
                    "dm=${item?.isInDirectManipulationMode}"
            )
        }
        appendLine("Parking views:")
        parkingViews.forEach { (host, reference) ->
            val view = reference.get()
            appendLine(
                "  $host -> viewId=${view?.id}, attached=${view?.isAttachedToWindow}, " +
                    "focused=${view?.isFocused}, windowFocus=${view?.hasWindowFocus()}"
            )
        }
    }

    internal fun registerItem(item: FocusItemView) {
        val target = item.target
        val existing = items[target]?.get()
        if (existing != null && existing !== item) {
            validationFailure("Duplicate rotary FocusItem target: $target")
        }
        items[target] = WeakReference(item)
        RotaryFocusLogger.debug {
            "Item registered; target=$target viewId=${item.id} total=${items.size}"
        }
        if (pendingFocus == target) retryPendingFocus(target)
    }

    internal fun unregisterItem(item: FocusItemView) {
        val target = item.target
        if (items[target]?.get() === item) items.remove(target)
        if (currentFocusTarget == target) currentFocusTarget = null
        _isDirectManipulationActiveState.value = items.values.any { it.get()?.isInDirectManipulationMode == true }
        RotaryFocusLogger.debug {
            "Item unregistered; target=$target viewId=${item.id} remaining=${items.size}"
        }
    }

    internal fun onItemFocusChanged(item: FocusItemView, hasFocus: Boolean) {
        if (hasFocus) {
            val superseded = pendingFocus?.takeIf { it != item.target }
            pendingFocus = null
            pendingFallback = null
            if (superseded != null) {
                areas[superseded.areaId]?.get()
                    ?.cancelPendingLogicalFocus(superseded.itemId)
                RotaryFocusLogger.debug {
                    "Pending focus superseded by real View focus; pending=$superseded " +
                        "actual=${item.target}"
                }
            }
            currentFocusTarget = item.target
            item.destinationKey?.let { destination ->
                savedDestinations[destination] = item.target
                RotaryFocusLogger.debug {
                    "Destination last focus updated; destination=$destination target=${item.target}"
                }
            }
        } else if (currentFocusTarget == item.target) {
            currentFocusTarget = null
        }
        RotaryFocusLogger.debug {
            "SOURCE_OF_TRUTH View focus; target=${item.target} viewId=${item.id} " +
                "hasFocus=$hasFocus current=$currentFocusTarget"
        }
    }

    internal fun onItemAvailabilityChanged(item: FocusItemView) {
        if (pendingFocus == item.target && item.canTakeRotaryFocus()) {
            retryPendingFocus(item.target)
        }
    }

    internal fun registerArea(area: RotaryFocusAreaView) {
        val existing = areas[area.areaId]?.get()
        if (existing != null && existing !== area) {
            validationFailure("Duplicate rotary FocusArea id: ${area.areaId}")
        }
        areas[area.areaId] = WeakReference(area)
        RotaryFocusLogger.debug {
            "Area registered; area=${area.areaId} viewId=${area.id} total=${areas.size}"
        }
        pendingFocus?.takeIf { it.areaId == area.areaId }?.let(::retryPendingFocus)
        validateAreaGeometry()
    }

    internal fun unregisterArea(area: RotaryFocusAreaView) {
        if (areas[area.areaId]?.get() === area) areas.remove(area.areaId)
        pendingFocus?.takeIf { it.areaId == area.areaId }?.let { pending ->
            clearPendingFocus(pending)
            RotaryFocusLogger.debug {
                "Pending focus cleared with detached area; area=${area.areaId} target=$pending"
            }
        }
        reportedOverlaps.removeAll { area.areaId.value in it }
        RotaryFocusLogger.debug {
            "Area unregistered; area=${area.areaId} remaining=${areas.size}"
        }
    }

    internal fun dispatchRotaryMotionEvent(event: MotionEvent, hostId: String): Boolean {
        val target = currentFocusTarget ?: return false
        val item = items[target]?.get() ?: return false
        if (!item.isFocused || !item.isInDirectManipulationMode) return false
        val handled = item.handleRotaryMotionEvent(event, transport = "host:$hostId")
        RotaryFocusLogger.debug {
            "Host rotary routing; host=$hostId target=$target action=${event.action} " +
                "source=${event.source} handled=$handled"
        }
        return handled
    }

    internal fun queuePendingFocus(target: RotaryFocusTarget) {
        pendingFocus = target
        pendingFallback = null
        RotaryFocusLogger.debug { "Logical focus queued; target=$target" }
    }

    internal fun hasPendingFocusRequest(areaId: FocusAreaId): Boolean =
        pendingFocus?.areaId == areaId

    internal fun pendingFocusTarget(areaId: FocusAreaId): RotaryFocusTarget? =
        pendingFocus?.takeIf { it.areaId == areaId }

    internal fun clearPendingFocus(target: RotaryFocusTarget) {
        if (pendingFocus != target) return
        pendingFocus = null
        pendingFallback = null
        areas[target.areaId]?.get()?.cancelPendingLogicalFocus(target.itemId)
    }

    internal fun failPendingReveal(target: RotaryFocusTarget, error: Throwable): Boolean =
        failPendingFocus(target, "reveal-failed-${error.javaClass.simpleName}")

    internal fun resolveLinkedAreaFocus(
        source: RotaryFocusAreaView,
        direction: RotaryTraversalDirection
    ): FocusItemView? {
        val visited = linkedSetOf(source.areaId)
        var candidateId = source.linkedAreaId(direction)
        while (candidateId != null && visited.add(candidateId)) {
            val candidateArea = areas[candidateId]?.get()
            if (candidateArea == null) {
                RotaryFocusLogger.warning {
                    "Linked FocusArea unavailable; source=${source.areaId} direction=$direction " +
                        "target=$candidateId visited=$visited"
                }
                return null
            }
            val sameWindow = source.rootView === candidateArea.rootView &&
                source.windowToken === candidateArea.windowToken
            if (!sameWindow) {
                RotaryFocusLogger.warning {
                    "Cross-window FocusArea link rejected; source=${source.areaId} " +
                        "target=$candidateId direction=$direction"
                }
                return null
            }
            val target = candidateArea.boundaryFocusTarget(direction)
            if (target != null) {
                candidateArea.prepareBoundaryEntry(target)
                RotaryFocusLogger.debug {
                    "Linked FocusArea traversal resolved; source=${source.areaId} " +
                        "target=${target.target} direction=$direction visited=$visited"
                }
                return target
            }
            if (hasPendingFocusRequest(candidateArea.areaId)) {
                RotaryFocusLogger.debug {
                    "Linked FocusArea entry queued; source=${source.areaId} " +
                        "targetArea=${candidateArea.areaId} direction=$direction"
                }
                return null
            }
            candidateId = candidateArea.linkedAreaId(direction)
        }
        if (candidateId != null) {
            RotaryFocusLogger.warning {
                "Linked FocusArea cycle ignored; source=${source.areaId} direction=$direction " +
                    "cycleAt=$candidateId visited=$visited"
            }
        }
        return null
    }

    internal fun registerParkingView(hostId: String, view: FocusParkingView) {
        parkingViews[hostId] = WeakReference(view)
        view.setFocusRestorer { bestRestoreTarget(view) }
        view.setFocusRestorationDeferred { shouldDeferParkingRestore(view) }
        RotaryFocusLogger.debug { "Parking view registered; host=$hostId viewId=${view.id}" }
    }

    internal fun unregisterParkingView(hostId: String, view: FocusParkingView) {
        if (parkingViews[hostId]?.get() === view) parkingViews.remove(hostId)
        view.setFocusRestorer(null)
        view.setFocusRestorationDeferred(null)
        RotaryFocusLogger.debug { "Parking view unregistered; host=$hostId viewId=${view.id}" }
    }

    internal fun validateAreaGeometry() {
        if (validationMode == RotaryValidationMode.Off) return
        if (geometryValidationPosted) return
        geometryValidationPosted = true
        geometryHandler.postDelayed(
            {
                geometryValidationPosted = false
                validateAreaGeometryNow()
            },
            GEOMETRY_VALIDATION_DELAY_MILLIS,
        )
    }

    private fun validateAreaGeometryNow() {
        val liveAreas = areas.values.mapNotNull(WeakReference<RotaryFocusAreaView>::get)
            .filter { it.isAttachedToWindow && it.isShown && it.width > 0 && it.height > 0 }
        liveAreas.forEachIndexed { index, first ->
            for (second in liveAreas.drop(index + 1)) {
                if (first.windowToken !== second.windowToken) continue
                val firstRect = first.globalVisibleRectOrNull() ?: continue
                val secondRect = second.globalVisibleRectOrNull() ?: continue
                if (!Rect.intersects(firstRect, secondRect)) continue
                val intersection = Rect(firstRect)
                if (!intersection.intersect(secondRect) || intersection.isEmpty) continue
                val key = listOf(first.areaId.value, second.areaId.value).sorted().joinToString("|")
                if (reportedOverlaps.add(key)) {
                    validationFailure(
                        "FocusAreas overlap: ${first.areaId}=$firstRect and " +
                            "${second.areaId}=$secondRect intersection=$intersection"
                    )
                }
            }
        }
    }

    internal fun validationFailure(message: String) {
        when (validationMode) {
            RotaryValidationMode.Off -> Unit
            RotaryValidationMode.Log -> RotaryFocusLogger.error({ "VALIDATION: $message" })
            RotaryValidationMode.Strict -> throw IllegalStateException(message)
        }
    }

    private fun retryPendingFocus(target: RotaryFocusTarget): Boolean {
        if (pendingFocus != target) return false
        return requestFocusInternal(
            target = target,
            fallback = pendingFallback,
            replaceRequest = false
        )
    }

    private fun failPendingFocus(target: RotaryFocusTarget, reason: String): Boolean {
        if (pendingFocus != target) return false
        if (
            areas[target.areaId]?.get()
                ?.continuePendingLogicalFocusAfterFailure(target.itemId, reason) == true
        ) {
            return true
        }
        val fallback = pendingFallback?.takeIf { it != target }
        pendingFocus = null
        pendingFallback = null
        RotaryFocusLogger.warning {
            "Pending focus failed; target=$target reason=$reason fallback=$fallback"
        }
        return fallback?.let(::requestFocus) == true
    }

    private fun shouldDeferParkingRestore(parkingView: FocusParkingView): Boolean {
        val target = pendingFocus ?: return false
        val readyItem = items[target]?.get()
        if (
            readyItem != null &&
            readyItem.rootView === parkingView.rootView &&
            readyItem.canTakeRotaryFocus()
        ) {
            return false
        }
        val area = areas[target.areaId]?.get() ?: return false
        return area.rootView === parkingView.rootView
    }

    private fun bestRestoreTarget(parkingView: FocusParkingView): View? {
        val pending = pendingFocus?.let { items[it]?.get() }
            ?.takeIf { it.rootView === parkingView.rootView && it.canTakeRotaryFocus() }
        if (pending != null) return pending
        val current = currentFocusTarget?.let { items[it]?.get() }
            ?.takeIf { it.rootView === parkingView.rootView && it.canTakeRotaryFocus() }
        if (current != null) return current
        return items.values.asSequence()
            .mapNotNull(WeakReference<FocusItemView>::get)
            .firstOrNull { it.rootView === parkingView.rootView && it.canTakeRotaryFocus() }
    }

    private fun FocusItemView.canTakeRotaryFocus(): Boolean =
        isAttachedToWindow && isShown && isEnabled && isFocusable && width > 0 && height > 0

    private fun View.globalVisibleRectOrNull(): Rect? = Rect().takeIf { getGlobalVisibleRect(it) }
}

private data class DeferredDestinationFocus(
    val target: RotaryFocusTarget,
    val fallback: RotaryFocusTarget?
)
