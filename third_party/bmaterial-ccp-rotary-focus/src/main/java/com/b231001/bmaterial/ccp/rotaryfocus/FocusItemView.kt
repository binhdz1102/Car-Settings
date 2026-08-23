package com.b231001.bmaterial.ccp.rotaryfocus

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.android.car.ui.utils.DirectManipulationHelper

/**
 * The real focusable Android View exposed to RotaryService.
 *
 * Its nested [ComposeView] is deliberately hidden from accessibility focus and blocked from the
 * Android focus chain. Compose is a renderer; this wrapper remains the single source of truth.
 */
@SuppressLint("ViewConstructor")
public class FocusItemView internal constructor(
    context: Context,
    public val target: RotaryFocusTarget,
    private val controller: RotaryFocusController,
    private val area: RotaryFocusAreaView
) : FrameLayout(context) {
    private val renderedContent = mutableStateOf<(@Composable (FocusItemRenderState) -> Unit)?>(
        value = null,
        policy = neverEqualPolicy()
    )
    private val renderedState = mutableStateOf(FocusItemRenderState())
    private val composeView = ComposeView(context).apply {
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isSaveEnabled = false
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    private var spec: FocusItemSpec? = null
    private var parentCompositionContext: CompositionContext? = null
    private var isAreaFocusAllowed: Boolean = true
    private var hasInstalledComposition: Boolean = false
    private var navigationResolver: ((Int) -> View?)? = null
    private var composeBringIntoViewRequest: (() -> Unit)? = null
    private var pinnableContainer: PinnableContainer? = null
    private var pinnedHandle: PinnableContainer.PinnedHandle? = null

    internal val destinationKey: String?
        get() = spec?.destinationKey

    public var isInDirectManipulationMode: Boolean = false
        private set

    init {
        id = View.generateViewId()
        isFocusable = true
        isFocusableInTouchMode = false
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        addView(
            composeView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        setOnFocusChangeListener { _, hasFocus ->
            updatePinnedState(hasFocus)
            updateRenderState(isFocused = hasFocus)
            controller.onItemFocusChanged(this, hasFocus)
            if (hasFocus) requestFocusedItemVisible()
            if (!hasFocus && isInDirectManipulationMode) {
                updateDirectManipulationMode(false, "focus-lost")
            }
        }
    }

    internal fun installComposition(parentContext: CompositionContext) {
        parentCompositionContext = parentContext
        if (hasInstalledComposition) return
        composeView.setParentCompositionContext(parentContext)
        composeView.setContent {
            CompositionLocalProvider(LocalRotaryFocusArea provides null) {
                Box(Modifier.clearAndSetSemantics { }) {
                    val state = renderedState.value
                    if (state.isDirectManipulationMode) {
                        BackHandler(enabled = true) {
                            updateDirectManipulationMode(false, "back-handler")
                        }
                    }
                    renderedContent.value?.invoke(state)
                }
            }
        }
        composeView.post(::suppressRendererAccessibility)
        hasInstalledComposition = true
        RotaryFocusLogger.debug { "Compose renderer installed; target=$target viewId=$id" }
    }

    internal fun update(
        newSpec: FocusItemSpec,
        areaFocusAllowed: Boolean
    ) {
        val previousSpec = spec
        if (isInDirectManipulationMode && newSpec.directManipulation == null) {
            updateDirectManipulationMode(false, "configuration-removed")
        }
        spec = newSpec
        isAreaFocusAllowed = areaFocusAllowed
        isEnabled = newSpec.isEnabled
        isFocusable = newSpec.isEnabled && areaFocusAllowed
        // Clickable is required for key activation (DPAD_CENTER/ENTER only performs the click on
        // clickable views). For ComposeContent items the nested renderer still wins touch
        // dispatch because onInterceptTouchEvent stays false; unhandled row areas fall back to
        // performClick, giving full-row activation.
        isClickable = newSpec.onClick != null || newSpec.directManipulation?.enterOnCenter == true
        contentDescription = newSpec.semantics.label
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            stateDescription = newSpec.semantics.stateDescription
        }
        minimumWidth = newSpec.minWidthPx
        minimumHeight = newSpec.minHeightPx
        renderedContent.value = newSpec.content
        updateRenderState(isEnabled = newSpec.isEnabled && areaFocusAllowed)
        if ((!newSpec.isEnabled || !areaFocusAllowed) && isInDirectManipulationMode) {
            updateDirectManipulationMode(false, "item-disabled")
        }
        if (previousSpec != newSpec) {
            RotaryFocusLogger.debug {
                "Item config updated; target=$target viewId=$id enabled=${newSpec.isEnabled} " +
                    "areaAllowed=$areaFocusAllowed next=${newSpec.nextFocusItem} " +
                    "previous=${newSpec.previousFocusItem} dm=${newSpec.directManipulation != null}"
            }
        }
        controller.onItemAvailabilityChanged(this)
        if (isAttachedToWindow) area.updateItem(this, newSpec)
    }

    internal fun setComposeBringIntoViewRequest(request: (() -> Unit)?) {
        composeBringIntoViewRequest = request
    }

    internal fun setPinnableContainer(container: PinnableContainer?) {
        if (pinnableContainer === container) return
        pinnedHandle?.release()
        pinnedHandle = null
        pinnableContainer = container
        updatePinnedState(isFocused)
    }

    internal fun setNavigationResolver(resolver: (Int) -> View?) {
        navigationResolver = resolver
    }

    internal fun setAreaFocusAllowed(allowed: Boolean) {
        if (isAreaFocusAllowed == allowed) return
        isAreaFocusAllowed = allowed
        isFocusable = allowed && spec?.isEnabled == true
        updateRenderState(isEnabled = allowed && spec?.isEnabled == true)
        if (!allowed && isInDirectManipulationMode) {
            updateDirectManipulationMode(false, "area-disabled")
        }
        RotaryFocusLogger.debug {
            "Area permission propagated; target=$target allowed=$allowed focusable=$isFocusable"
        }
        controller.onItemAvailabilityChanged(this)
    }

    /**
     * Requests rotary focus even when the host window is still in touch mode.
     *
     * Android normally rejects [requestFocus] for a view that is not focusable in touch mode.
     * Automotive Settings is commonly launched by an intent while the emulator/OEM window is
     * still in that mode, which used to leave the first rotary item un-focused until the driver
     * supplied an extra detent.  The flag is enabled only for this synchronous request and is
     * restored immediately so normal touch-mode focus policy remains unchanged.
     */
    internal fun requestRotaryFocus(): Boolean {
        if (!isFocusable || !isEnabled || !isAttachedToWindow) return false
        val previous = isFocusableInTouchMode
        if (!previous && isInTouchMode) isFocusableInTouchMode = true
        return try {
            requestFocus(FOCUS_DOWN)
        } finally {
            if (!previous && isFocusableInTouchMode) isFocusableInTouchMode = false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        parentCompositionContext?.let(::installComposition)
        composeView.post(::suppressRendererAccessibility)
        spec?.let { area.registerItem(this, it) }
        controller.registerItem(this)
        RotaryFocusLogger.debug {
            "Item attached; target=$target viewId=$id size=${width}x$height root=${rootView.id}"
        }
    }

    override fun onDetachedFromWindow() {
        if (isInDirectManipulationMode) updateDirectManipulationMode(false, "detached")
        pinnedHandle?.release()
        pinnedHandle = null
        area.unregisterItem(this)
        controller.unregisterItem(this)
        hasInstalledComposition = false
        RotaryFocusLogger.debug { "Item detached; target=$target viewId=$id" }
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility != VISIBLE && isInDirectManipulationMode) {
            updateDirectManipulationMode(false, "window-not-visible")
        }
        if (visibility == VISIBLE) controller.onItemAvailabilityChanged(this)
        super.onWindowVisibilityChanged(visibility)
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && height > 0) {
            // A destination or lazy item can register while it is still 0x0. Re-checking here
            // completes the queued real-View focus request as soon as layout materializes it.
            controller.onItemAvailabilityChanged(this)
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (!hasWindowFocus && isInDirectManipulationMode) {
            updateDirectManipulationMode(false, "window-focus-lost")
        }
        if (hasWindowFocus) controller.onItemAvailabilityChanged(this)
        RotaryFocusLogger.debug {
            "Item window focus changed; target=$target hasWindowFocus=$hasWindowFocus " +
                "dm=$isInDirectManipulationMode"
        }
        super.onWindowFocusChanged(hasWindowFocus)
    }

    override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?
    ) {
        RotaryFocusLogger.debug {
            "View.onFocusChanged; target=$target gain=$gainFocus direction=$direction " +
                "previousRect=$previouslyFocusedRect touchMode=$isInTouchMode"
        }
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    override fun focusSearch(direction: Int): View? {
        if (direction == FOCUS_FORWARD || direction == FOCUS_BACKWARD) {
            val result = navigationResolver?.invoke(direction)
            RotaryFocusLogger.debug {
                "Rotary focusSearch resolved; from=$target direction=$direction " +
                    "targetView=${result?.id ?: "none-hold"}"
            }
            // Never delegate rotary traversal to View.focusSearch(). Doing so sends the request to
            // the parent FocusArea and can recurse back into this View at an area boundary.
            return result
        }
        val result = super.focusSearch(direction)
        RotaryFocusLogger.verbose {
            "Framework focusSearch; from=$target direction=$direction result=${result?.id}"
        }
        return result
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        RotaryFocusLogger.debug {
            "Key event; target=$target key=${KeyEvent.keyCodeToString(event.keyCode)} " +
                "action=${event.action} repeat=${event.repeatCount} dm=$isInDirectManipulationMode"
        }
        val directConfig = spec?.directManipulation
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER && directConfig?.enterOnCenter == true) {
            if (event.action == KeyEvent.ACTION_DOWN) updateRenderState(isPressed = true)
            if (event.action == KeyEvent.ACTION_UP) {
                updateRenderState(isPressed = false)
                if (!isInDirectManipulationMode) {
                    updateDirectManipulationMode(true, "center-button")
                }
            }
            return true
        }
        if (
            event.keyCode == KeyEvent.KEYCODE_BACK &&
            isInDirectManipulationMode &&
            directConfig?.exitOnBack == true
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                event.startTracking()
                return true
            }
            if (event.action == KeyEvent.ACTION_UP) {
                updateDirectManipulationMode(false, "back-button")
                return true
            }
            return true
        }

        val nudgeDirection = event.keyCode.toNudgeDirection()
        if (isInDirectManipulationMode && nudgeDirection != null) {
            if (event.action == KeyEvent.ACTION_UP) {
                directConfig?.onNudge?.invoke(
                    RotaryNudgeEvent(nudgeDirection, event.repeatCount, event.eventTime)
                )
            }
            return directConfig?.onNudge != null
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (handleRotaryMotionEvent(event, transport = "focused-view")) return true
        return super.onGenericMotionEvent(event)
    }

    internal fun handleRotaryMotionEvent(
        event: MotionEvent,
        transport: String
    ): Boolean {
        val axes = listOf(
            MotionEvent.AXIS_SCROLL,
            MotionEvent.AXIS_VSCROLL,
            MotionEvent.AXIS_HSCROLL
        )
        val axis = axes.firstOrNull { event.getAxisValue(it) != 0f }
        val value = axis?.let(event::getAxisValue) ?: 0f
        RotaryFocusLogger.debug {
            "Motion event; target=$target transport=$transport action=${event.action} " +
                "source=${event.source} " +
                "rotary=${event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)} " +
                "axis=$axis value=$value dm=$isInDirectManipulationMode"
        }
        if (
            isInDirectManipulationMode &&
            event.action == MotionEvent.ACTION_SCROLL &&
            axis != null
        ) {
            spec?.directManipulation?.onRotary?.invoke(
                RotaryRotationEvent(value, event.eventTime, axis)
            )
            RotaryFocusLogger.debug {
                "Direct rotary callback delivered; target=$target transport=$transport " +
                    "detents=$value axis=$axis"
            }
            return true
        }
        return false
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        spec?.touchBehavior == FocusItemTouchBehavior.View || super.onInterceptTouchEvent(event)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (spec?.touchBehavior != FocusItemTouchBehavior.View) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isEnabled || !isClickable) return false
                updateRenderState(isPressed = true)
                return true
            }

            MotionEvent.ACTION_UP -> {
                val wasPressed = renderedState.value.isPressed
                updateRenderState(isPressed = false)
                val releasedInside =
                    event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()
                if (wasPressed && releasedInside) {
                    performClick()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                updateRenderState(isPressed = false)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        val click = spec?.onClick ?: return false
        if (!isEnabled || !isAreaFocusAllowed) return false
        RotaryFocusLogger.debug { "Click performed; target=$target dm=$isInDirectManipulationMode" }
        click()
        return true
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val detents = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> 1f
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> -1f
            else -> null
        }
        if (detents != null && isInDirectManipulationMode) {
            val callback = spec?.directManipulation?.onRotary ?: return false
            callback(
                RotaryRotationEvent(
                    detents = detents,
                    eventTimeMillis = SystemClock.uptimeMillis(),
                    axis = MotionEvent.AXIS_SCROLL
                )
            )
            RotaryFocusLogger.debug {
                "Direct rotary callback delivered; target=$target transport=accessibility-action " +
                    "action=$action detents=$detents"
            }
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        val currentSpec = spec ?: return
        info.className = currentSpec.semantics.role.accessibilityClassName
        info.isEnabled = currentSpec.isEnabled && isAreaFocusAllowed
        info.isClickable = currentSpec.onClick != null || currentSpec.directManipulation != null
        if (currentSpec.directManipulation != null) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            info.stateDescription = currentSpec.semantics.stateDescription
        }
    }

    internal fun updateDirectManipulationMode(enabled: Boolean, reason: String): Boolean {
        if (enabled == isInDirectManipulationMode) return true
        if (enabled && spec?.directManipulation == null) return false
        isInDirectManipulationMode = enabled
        isSelected = enabled
        DirectManipulationHelper.enableDirectManipulationMode(this, enabled)
        updateRenderState(isDirectManipulationMode = enabled, isPressed = false)
        spec?.directManipulation?.onModeChanged?.invoke(enabled)
        controller.onItemDirectManipulationModeChanged(this, enabled)
        RotaryFocusLogger.debug {
            "Direct manipulation changed; target=$target enabled=$enabled reason=$reason " +
                "focused=$isFocused"
        }
        return true
    }

    private fun requestFocusedItemVisible() {
        post {
            if (!isFocused || !isAttachedToWindow) return@post
            val requestedFromView = requestRectangleOnScreen(
                Rect(0, 0, width, height),
                true
            )
            composeBringIntoViewRequest?.invoke()
            RotaryFocusLogger.debug {
                "Bring focused item into view; target=$target nativeAccepted=$requestedFromView " +
                    "composeBridge=${composeBringIntoViewRequest != null}"
            }
        }
    }

    private fun suppressRendererAccessibility() {
        composeView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        composeView.suppressDescendantAccessibility()
        RotaryFocusLogger.verbose {
            "Renderer accessibility suppressed; target=$target childCount=${composeView.childCount}"
        }
    }

    private fun updatePinnedState(hasFocus: Boolean) {
        if (hasFocus && pinnedHandle == null) {
            pinnedHandle = pinnableContainer?.pin()
        } else if (!hasFocus) {
            pinnedHandle?.release()
            pinnedHandle = null
        }
    }

    private fun ViewGroup.suppressDescendantAccessibility() {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            child.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            if (child is ViewGroup) child.suppressDescendantAccessibility()
        }
    }

    private fun updateRenderState(
        isFocused: Boolean = renderedState.value.isFocused,
        isPressed: Boolean = renderedState.value.isPressed,
        isEnabled: Boolean = renderedState.value.isEnabled,
        isDirectManipulationMode: Boolean = renderedState.value.isDirectManipulationMode
    ) {
        renderedState.value = FocusItemRenderState(
            isFocused = isFocused,
            isPressed = isPressed,
            isEnabled = isEnabled,
            isDirectManipulationMode = isDirectManipulationMode
        )
    }

    private fun Int.toNudgeDirection(): RotaryNudgeDirection? = when (this) {
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT
        -> RotaryNudgeDirection.Left

        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT
        -> RotaryNudgeDirection.Right

        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP
        -> RotaryNudgeDirection.Up

        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN
        -> RotaryNudgeDirection.Down

        else -> null
    }
}

internal data class FocusItemSpec(
    val id: FocusItemId,
    val isEnabled: Boolean,
    val nextFocusItem: FocusItemId?,
    val previousFocusItem: FocusItemId?,
    val onClick: (() -> Unit)?,
    val directManipulation: DirectManipulationConfig?,
    val semantics: FocusItemSemantics,
    val touchBehavior: FocusItemTouchBehavior,
    val layout: FocusItemLayout,
    val widthPx: Int?,
    val heightPx: Int?,
    val minWidthPx: Int,
    val minHeightPx: Int,
    val destinationKey: String?,
    val content: @Composable (FocusItemRenderState) -> Unit
)
