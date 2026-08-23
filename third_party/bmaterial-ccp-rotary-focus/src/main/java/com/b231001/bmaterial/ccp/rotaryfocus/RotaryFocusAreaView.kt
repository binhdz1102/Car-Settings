package com.b231001.bmaterial.ccp.rotaryfocus

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewParent
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.android.car.ui.FocusArea
import com.android.car.ui.FocusParkingView

/** Real Car UI FocusArea which hosts arbitrary Compose layout and real FocusItemView descendants. */
@SuppressLint("ViewConstructor")
public class RotaryFocusAreaView internal constructor(
    context: Context,
    public val areaId: FocusAreaId,
    private val controller: RotaryFocusController
) : FocusArea(context) {
    private val itemViews = LinkedHashMap<FocusItemId, FocusItemView>()
    private val itemSpecs = LinkedHashMap<FocusItemId, FocusItemSpec>()
    private val renderView = RotaryAreaComposeView(context).apply {
        id = View.generateViewId()
        isFocusable = false
        isFocusableInTouchMode = false
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }
    private var firstFocusAt: FocusItemId? = null
    private var hasHandledFirstEntry: Boolean = false
    private var lastFocusedItem: FocusItemView? = null
    private var pendingBoundaryEntryTarget: FocusItemView? = null
    private var focusListenerRegistered: Boolean = false
    private var wrapAround: Boolean = false
    private var nextFocusArea: FocusAreaId? = null
    private var previousFocusArea: FocusAreaId? = null
    private var explicitFocusOrder: List<FocusItemId> = emptyList()
    private var revealHandler: FocusItemRevealHandler? = null
    private var pendingLogicalItemId: FocusItemId? = null
    private var pendingLogicalDirection: RotaryTraversalDirection? = null

    internal var layoutOrientation: FocusAreaOrientation = FocusAreaOrientation.Vertical
        private set

    public var isFocusAllowed: Boolean = true
        private set

    private val focusChangeListener = ViewTreeObserver.OnGlobalFocusChangeListener { old, new ->
        val oldInside = old != null && containsView(old)
        val newInside = new != null && containsView(new)
        RotaryFocusLogger.debug {
            "Area focus transition; area=$areaId old=${old.debugIdentity()} oldInside=$oldInside " +
                "new=${new.debugIdentity()} newInside=$newInside firstHandled=$hasHandledFirstEntry"
        }
        if (!oldInside && newInside && isFocusAllowed) {
            val boundaryTarget = pendingBoundaryEntryTarget
            pendingBoundaryEntryTarget = null
            if (boundaryTarget === new) {
                hasHandledFirstEntry = true
                RotaryFocusLogger.debug {
                    "Prepared area entry accepted; area=$areaId " +
                        "target=${new.debugIdentity()}"
                }
            } else if (!hasHandledFirstEntry) {
                hasHandledFirstEntry = true
                val target = initialFocusView()
                if (target != null && target !== new) {
                    post {
                        val success = target.canTakeFocusNow() && target.requestRotaryFocus()
                        RotaryFocusLogger.debug {
                            "Initial area focus redirected; area=$areaId first=$firstFocusAt " +
                                "target=${target.target} success=$success"
                        }
                    }
                } else if (target == null) {
                    requestConfiguredLogicalFocus()
                }
            }
        }
        if (newInside && new is FocusItemView) {
            pendingLogicalItemId = null
            pendingLogicalDirection = null
            lastFocusedItem = new
            setDefaultFocus(new)
        }
    }

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        controller.validateAreaGeometry()
    }

    init {
        id = View.generateViewId()
        orientation = VERTICAL
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        clipChildren = false
        addView(
            renderView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    internal fun setParentCompositionContext(parent: CompositionContext?) {
        renderView.setParentCompositionContext(parent)
    }

    internal fun setAreaContent(content: @Composable () -> Unit) {
        renderView.setAreaContent(content)
    }

    internal fun updateConfiguration(
        initialFocus: FocusItemId?,
        focusAllowed: Boolean,
        wrapAround: Boolean,
        nextFocusArea: FocusAreaId?,
        previousFocusArea: FocusAreaId?,
        focusOrder: List<FocusItemId>,
        revealHandler: FocusItemRevealHandler?,
        layoutOrientation: FocusAreaOrientation = FocusAreaOrientation.Vertical
    ) {
        require(focusOrder.size == focusOrder.distinct().size) {
            "focusOrder contains duplicate ids in FocusArea '$areaId': $focusOrder"
        }
        validateAreaNavigation(nextFocusArea, previousFocusArea)
        val focusedBeforeUpdate = findFocus()?.takeIf(::containsView)
        firstFocusAt = initialFocus
        this.wrapAround = wrapAround
        this.nextFocusArea = nextFocusArea
        this.previousFocusArea = previousFocusArea
        explicitFocusOrder = focusOrder.toList()
        this.revealHandler = revealHandler
        this.layoutOrientation = layoutOrientation
        setFocusAllowedInternal(focusAllowed)
        applyFocusOrder()
        updateDefaultFocus()
        if (!focusAllowed && focusedBeforeUpdate != null) {
            post { moveFocusOutsideArea(focusedBeforeUpdate) }
        }
        RotaryFocusLogger.debug {
            "Area configuration updated; area=$areaId first=$initialFocus allowed=$focusAllowed " +
                "wrap=$wrapAround order=$focusOrder previousArea=$previousFocusArea " +
                "nextArea=$nextFocusArea lazyBridge=${revealHandler != null}"
        }
    }

    internal fun registerItem(item: FocusItemView, spec: FocusItemSpec) {
        require(item.target.areaId == areaId) {
            "FocusItem ${item.target} cannot register in FocusArea '$areaId'"
        }
        if (!containsView(item)) {
            controller.validationFailure(
                "FocusItem ${item.target} is not a real View descendant of FocusArea '$areaId'"
            )
            return
        }
        val existing = itemViews[spec.id]
        if (existing != null && existing !== item) {
            controller.validationFailure("Duplicate FocusItem id '${spec.id}' in '$areaId'")
            return
        }
        itemViews[spec.id] = item
        itemSpecs[spec.id] = spec
        validateItemSpec(spec)
        applyFocusOrder()
        updateDefaultFocus()
        RotaryFocusLogger.debug {
            "Area item registered; area=$areaId item=${spec.id} live=${itemViews.keys} " +
                "logical=${orderedItemIds()}"
        }
        val pending = controller.pendingFocusTarget(areaId)
        if (
            pending?.itemId == spec.id &&
            pendingLogicalItemId == spec.id &&
            !item.canParticipateInFocusOrder()
        ) {
            continuePendingLogicalFocusAfterFailure(spec.id, "materialized-item-unfocusable")
        }
    }

    internal fun updateItem(item: FocusItemView, spec: FocusItemSpec) {
        if (itemViews[spec.id] === item) {
            itemSpecs[spec.id] = spec
            validateItemSpec(spec)
            applyFocusOrder()
            updateDefaultFocus()
        }
    }

    internal fun unregisterItem(item: FocusItemView) {
        val itemId = item.target.itemId
        if (itemViews[itemId] !== item) return
        itemViews.remove(itemId)
        itemSpecs.remove(itemId)
        if (lastFocusedItem === item) lastFocusedItem = null
        if (pendingBoundaryEntryTarget === item) pendingBoundaryEntryTarget = null
        applyFocusOrder()
        updateDefaultFocus()
        RotaryFocusLogger.debug {
            "Area item unregistered; area=$areaId item=$itemId remaining=${itemViews.keys}"
        }
    }

    internal fun requestReveal(itemId: FocusItemId): Boolean {
        val accepted = revealHandler?.requestReveal(itemId) { error ->
            post { handleRevealFailure(itemId, error) }
        } == true
        RotaryFocusLogger.debug {
            "Logical focus reveal requested; area=$areaId item=$itemId accepted=$accepted"
        }
        return accepted
    }

    /** Requests the configured first item, or the first currently focusable item. */
    public fun requestInitialFocus(): Boolean {
        if (!isFocusAllowed) return false
        val target = initialFocusView()
        if (target == null) return requestConfiguredLogicalFocus()
        hasHandledFirstEntry = true
        val success = target.requestRotaryFocus()
        RotaryFocusLogger.debug {
            "Initial focus explicitly requested; area=$areaId target=${target.target} " +
                "success=$success"
        }
        return success
    }

    /** Makes every item in this area visible-but-unfocusable without changing Compose content. */
    public fun setFocusAllowed(allowed: Boolean) {
        val focused = findFocus()?.takeIf(::containsView)
        setFocusAllowedInternal(allowed)
        if (!allowed && focused != null) post { moveFocusOutsideArea(focused) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        validateNoNestedFocusArea()
        controller.registerArea(this)
        if (!focusListenerRegistered && viewTreeObserver.isAlive) {
            viewTreeObserver.addOnGlobalFocusChangeListener(focusChangeListener)
            viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            focusListenerRegistered = true
        }
        RotaryFocusLogger.debug {
            "Area attached; area=$areaId viewId=$id parent=${parent.debugIdentity()} " +
                "window=${windowToken?.hashCode()}"
        }
    }

    override fun onDetachedFromWindow() {
        if (focusListenerRegistered && viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnGlobalFocusChangeListener(focusChangeListener)
            viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        }
        focusListenerRegistered = false
        pendingLogicalItemId = null
        pendingLogicalDirection = null
        controller.unregisterArea(this)
        RotaryFocusLogger.debug { "Area detached; area=$areaId viewId=$id" }
        super.onDetachedFromWindow()
    }

    override fun focusSearch(focused: View, direction: Int): View? {
        if (
            focused is FocusItemView &&
            containsView(focused) &&
            (direction == FOCUS_FORWARD || direction == FOCUS_BACKWARD)
        ) {
            return resolveRotaryNavigation(focused, direction)
        }
        return super.focusSearch(focused, direction)
    }

    private fun applyFocusOrder() {
        itemViews.values.forEach { view ->
            view.nextFocusForwardId = staticForwardTarget(view)?.id ?: View.NO_ID
            view.setNavigationResolver { direction -> resolveRotaryNavigation(view, direction) }
        }
    }

    private fun resolveRotaryNavigation(focused: FocusItemView, direction: Int): View? {
        if (focused.isInDirectManipulationMode) {
            RotaryFocusLogger.debug {
                "Rotary traversal held by direct manipulation; area=$areaId from=${focused.target}"
            }
            return null
        }
        val traversalDirection = direction.toTraversalDirection() ?: return null
        val localTarget = resolveLocalTarget(focused, traversalDirection)
        if (localTarget != null) {
            RotaryFocusLogger.debug {
                "Rotary traversal local; area=$areaId from=${focused.target.itemId} " +
                    "direction=$traversalDirection to=${localTarget.target.itemId} " +
                    "wrap=$wrapAround"
            }
            return localTarget
        }
        val linkedTarget = if (wrapAround || controller.hasPendingFocusRequest(areaId)) {
            null
        } else {
            controller.resolveLinkedAreaFocus(this, traversalDirection)
        }
        RotaryFocusLogger.debug {
            "Rotary traversal boundary; area=$areaId from=${focused.target.itemId} " +
                "direction=$traversalDirection wrap=$wrapAround " +
                "linked=${linkedAreaId(traversalDirection)} " +
                "result=${linkedTarget?.target ?: "none-hold"}"
        }
        return linkedTarget
    }

    private fun resolveLocalTarget(
        focused: FocusItemView,
        direction: RotaryTraversalDirection
    ): FocusItemView? {
        val orderedIds = orderedItemIds()
        val sourceIndex = orderedIds.indexOf(focused.target.itemId)
        if (sourceIndex < 0) return null
        val visited = linkedSetOf(focused.target.itemId)
        var explicitId = itemSpecs[focused.target.itemId]?.linkedItem(direction)
        while (explicitId != null && visited.add(explicitId)) {
            val explicitView = itemViews[explicitId]
            if (explicitView?.canTakeFocusNow() == true) return explicitView
            if (
                (explicitView == null || explicitView.canParticipateInFocusOrder()) &&
                queueLogicalFocus(explicitId, direction)
            ) {
                return null
            }
            explicitId = itemSpecs[explicitId]?.linkedItem(direction)
        }
        if (explicitId != null) {
            RotaryFocusLogger.warning {
                "Item navigation cycle exhausted; area=$areaId from=${focused.target.itemId} " +
                    "direction=$direction visited=$visited"
            }
        }
        for (
        index in rotaryTraversalIndices(
            currentIndex = sourceIndex,
            itemCount = orderedIds.size,
            direction = direction,
            wrapAround = wrapAround
        )
        ) {
            val itemId = orderedIds[index]
            if (itemId in visited) continue
            val candidate = itemViews[itemId]
            if (candidate?.canTakeFocusNow() == true) return candidate
            if (
                (candidate == null || candidate.canParticipateInFocusOrder()) &&
                queueLogicalFocus(itemId, direction)
            ) {
                return null
            }
        }
        return null
    }

    private fun staticForwardTarget(focused: FocusItemView): FocusItemView? {
        val orderedIds = orderedItemIds()
        val sourceIndex = orderedIds.indexOf(focused.target.itemId)
        if (sourceIndex < 0) return null
        return rotaryTraversalIndices(
            currentIndex = sourceIndex,
            itemCount = orderedIds.size,
            direction = RotaryTraversalDirection.Forward,
            wrapAround = false
        ).asSequence()
            .map(orderedIds::get)
            .mapNotNull(itemViews::get)
            .firstOrNull { it.canParticipateInFocusOrder() }
    }

    internal fun linkedAreaId(direction: RotaryTraversalDirection): FocusAreaId? =
        when (direction) {
            RotaryTraversalDirection.Forward -> nextFocusArea
            RotaryTraversalDirection.Backward -> previousFocusArea
        }

    internal fun boundaryFocusTarget(direction: RotaryTraversalDirection): FocusItemView? {
        if (!isFocusAllowed || !isAttachedToWindow || !isShown) return null
        val ids = when (direction) {
            RotaryTraversalDirection.Forward -> orderedItemIds().asSequence()
            RotaryTraversalDirection.Backward -> orderedItemIds().asReversed().asSequence()
        }
        for (itemId in ids) {
            val candidate = itemViews[itemId]
            if (candidate?.canTakeFocusNow() == true) return candidate
            if (
                (candidate == null || candidate.canParticipateInFocusOrder()) &&
                queueLogicalFocus(itemId, direction)
            ) {
                return null
            }
        }
        return null
    }

    internal fun prepareBoundaryEntry(target: FocusItemView) {
        pendingBoundaryEntryTarget = target
        RotaryFocusLogger.debug {
            "Linked area boundary entry prepared; area=$areaId target=${target.target}"
        }
    }

    internal fun prepareControllerEntry(target: FocusItemView) {
        val areaAlreadyOwnsFocus = findFocus()?.let(::containsView) == true
        if (!areaAlreadyOwnsFocus) {
            pendingBoundaryEntryTarget = target
            RotaryFocusLogger.debug {
                "Controller area entry prepared; area=$areaId target=${target.target}"
            }
        }
    }

    private fun queueLogicalFocus(
        itemId: FocusItemId,
        direction: RotaryTraversalDirection? = null
    ): Boolean {
        if (!requestReveal(itemId)) return false
        pendingLogicalItemId = itemId
        pendingLogicalDirection = direction
        controller.queuePendingFocus(RotaryFocusTarget(areaId, itemId))
        return true
    }

    private fun requestConfiguredLogicalFocus(): Boolean {
        val orderedIds = orderedItemIds()
        if (orderedIds.isEmpty()) return false
        val configuredIndex = firstFocusAt?.let(orderedIds::indexOf)?.takeIf { it >= 0 } ?: 0
        val candidateIndices = buildList {
            addAll(configuredIndex until orderedIds.size)
            addAll(0 until configuredIndex)
        }
        for (index in candidateIndices) {
            val itemId = orderedIds[index]
            val candidate = itemViews[itemId]
            if (candidate?.canTakeFocusNow() == true) {
                return controller.requestFocus(candidate.target)
            }
            if (
                (candidate == null || candidate.canParticipateInFocusOrder()) &&
                queueLogicalFocus(itemId, RotaryTraversalDirection.Forward)
            ) {
                return true
            }
        }
        return false
    }

    private fun continueAfterUnavailablePendingItem(itemId: FocusItemId) {
        val direction = pendingLogicalDirection ?: RotaryTraversalDirection.Forward
        controller.clearPendingFocus(RotaryFocusTarget(areaId, itemId))
        pendingLogicalItemId = null
        pendingLogicalDirection = null
        val orderedIds = orderedItemIds()
        val sourceIndex = orderedIds.indexOf(itemId)
        if (sourceIndex < 0) return
        for (
        index in rotaryTraversalIndices(
            currentIndex = sourceIndex,
            itemCount = orderedIds.size,
            direction = direction,
            wrapAround = wrapAround
        )
        ) {
            val candidateId = orderedIds[index]
            val candidate = itemViews[candidateId]
            if (candidate?.canTakeFocusNow() == true) {
                controller.requestFocus(candidate.target)
                return
            }
            if (
                (candidate == null || candidate.canParticipateInFocusOrder()) &&
                queueLogicalFocus(candidateId, direction)
            ) {
                return
            }
        }
        if (!wrapAround) {
            controller.resolveLinkedAreaFocus(this, direction)?.let { linkedTarget ->
                controller.requestFocus(linkedTarget.target)
            }
        }
    }

    internal fun cancelPendingLogicalFocus(itemId: FocusItemId? = null) {
        if (itemId != null && pendingLogicalItemId != itemId) return
        pendingLogicalItemId = null
        pendingLogicalDirection = null
    }

    internal fun continuePendingLogicalFocusAfterFailure(
        itemId: FocusItemId,
        reason: String
    ): Boolean {
        if (pendingLogicalItemId != itemId) return false
        RotaryFocusLogger.warning {
            "Logical focus candidate failed; area=$areaId item=$itemId reason=$reason"
        }
        continueAfterUnavailablePendingItem(itemId)
        return true
    }

    private fun handleRevealFailure(itemId: FocusItemId, error: Throwable) {
        val target = RotaryFocusTarget(areaId, itemId)
        if (controller.pendingFocusTarget(areaId) != target) {
            RotaryFocusLogger.debug {
                "Stale reveal failure ignored; area=$areaId item=$itemId " +
                    "error=${error.javaClass.simpleName}"
            }
            return
        }
        RotaryFocusLogger.warning {
            "Accepted reveal failed; area=$areaId item=$itemId " +
                "logical=${pendingLogicalItemId == itemId} error=${error.javaClass.simpleName}"
        }
        if (pendingLogicalItemId == itemId && pendingLogicalDirection != null) {
            continuePendingLogicalFocusAfterFailure(
                itemId,
                "reveal-failed-${error.javaClass.simpleName}"
            )
        } else {
            controller.failPendingReveal(target, error)
        }
    }

    private fun orderedItemIds(): List<FocusItemId> =
        mergeRotaryFocusOrder(explicitFocusOrder, itemViews.keys)

    private fun updateDefaultFocus() {
        val cached = lastFocusedItem?.takeIf { it.canParticipateInFocusOrder() }
        if (cached == null && lastFocusedItem != null) lastFocusedItem = null
        setDefaultFocus(
            if (hasHandledFirstEntry) cached ?: firstFocusableView() else initialFocusView()
        )
    }

    private fun setFocusAllowedInternal(allowed: Boolean) {
        val itemStatesAlreadyMatch = itemViews.values.all {
            it.isFocusable == (allowed && it.isEnabled)
        }
        if (isFocusAllowed == allowed && itemStatesAlreadyMatch) return
        isFocusAllowed = allowed
        descendantFocusability = if (allowed) FOCUS_AFTER_DESCENDANTS else FOCUS_BLOCK_DESCENDANTS
        itemViews.values.forEach { it.setAreaFocusAllowed(allowed) }
        RotaryFocusLogger.debug {
            "Area focus permission changed; area=$areaId allowed=$allowed " +
                "focused=${findFocus().debugIdentity()}"
        }
    }

    private fun moveFocusOutsideArea(previouslyFocused: View) {
        val focusables = arrayListOf<View>().also {
            rootView.addFocusables(it, FOCUS_FORWARD, FOCUSABLES_ALL)
        }
        val regularCandidate = focusables.firstOrNull {
            !containsView(it) && it !is FocusParkingView && it.canTakeFocusNow()
        }
        val parkingCandidate = focusables.firstOrNull {
            it is FocusParkingView && it.canTakeFocusNow()
        }
        val target = regularCandidate ?: parkingCandidate
        val success = target?.requestFocus() == true || controller.parkFocus()
        RotaryFocusLogger.warning {
            "Focused area disabled; area=$areaId previous=${previouslyFocused.debugIdentity()} " +
                "candidate=${target.debugIdentity()} success=$success"
        }
    }

    private fun initialFocusView(): FocusItemView? {
        if (!isFocusAllowed) return null
        val configuredId = firstFocusAt ?: return firstFocusableView()
        return itemViews[configuredId]?.takeIf { it.canTakeFocusNow() }
    }

    private fun firstFocusableView(): FocusItemView? = orderedItemIds().asSequence()
        .mapNotNull(itemViews::get)
        .firstOrNull { it.canTakeFocusNow() }

    private fun validateItemSpec(spec: FocusItemSpec) {
        if (spec.nextFocusItem == spec.id || spec.previousFocusItem == spec.id) {
            controller.validationFailure("FocusItem '${spec.id}' in '$areaId' points to itself")
        }
    }

    private fun validateAreaNavigation(
        nextArea: FocusAreaId?,
        previousArea: FocusAreaId?
    ) {
        if (nextArea == areaId || previousArea == areaId) {
            controller.validationFailure(
                "FocusArea '$areaId' links to itself. Use wrapAround for local wrapping."
            )
        }
    }

    private fun validateNoNestedFocusArea() {
        var ancestor: ViewParent? = parent
        while (ancestor != null) {
            if (ancestor is FocusArea) {
                controller.validationFailure(
                    "Nested FocusArea is unsupported: '$areaId' is inside viewId=${ancestor.id}"
                )
                return
            }
            ancestor = ancestor.parent
        }
    }

    private fun containsView(candidate: View): Boolean {
        var ancestor: ViewParent? = candidate.parent
        while (ancestor != null) {
            if (ancestor === this) return true
            ancestor = ancestor.parent
        }
        return false
    }

    private fun View.canTakeFocusNow(): Boolean =
        isAttachedToWindow && isShown && isFocusable && isEnabled && width > 0 && height > 0

    private fun FocusItemView.canParticipateInFocusOrder(): Boolean =
        visibility == VISIBLE && isFocusable && isEnabled

    private fun FocusItemSpec.linkedItem(direction: RotaryTraversalDirection): FocusItemId? =
        when (direction) {
            RotaryTraversalDirection.Forward -> nextFocusItem
            RotaryTraversalDirection.Backward -> previousFocusItem
        }

    private fun Int.toTraversalDirection(): RotaryTraversalDirection? = when (this) {
        FOCUS_FORWARD -> RotaryTraversalDirection.Forward
        FOCUS_BACKWARD -> RotaryTraversalDirection.Backward
        else -> null
    }

    private fun Any?.debugIdentity(): String = when (this) {
        null -> "null"
        is FocusItemView -> "FocusItem($target,id=$id)"
        is View -> "${javaClass.simpleName}(id=$id,focused=$isFocused)"
        else -> javaClass.simpleName
    }
}

/** Compose host hidden behind a normal ViewGroup accessibility class for AAOS tree traversal. */
private class RotaryAreaComposeView(context: Context) : AbstractComposeView(context) {
    private val content = mutableStateOf<(@Composable () -> Unit)?>(
        value = null,
        policy = neverEqualPolicy()
    )

    @Composable
    override fun Content() {
        content.value?.invoke()
    }

    fun setAreaContent(content: @Composable () -> Unit) {
        this.content.value = content
    }

    override fun getAccessibilityClassName(): CharSequence = "android.view.ViewGroup"
}
