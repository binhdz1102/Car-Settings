package com.b231001.bmaterial.ccp.rotaryfocus

import android.content.Context
import android.view.MotionEvent
import android.view.InputDevice
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import com.android.car.ui.FocusParkingView

/** Controller consumed by [FocusArea]. Null means the caller forgot to install a host. */
public val LocalRotaryFocusController: ProvidableCompositionLocal<RotaryFocusController?> =
    staticCompositionLocalOf { null }

/**
 * Mirrors Android View touch mode for the current host window.
 *
 * `false` means the window is in non-touch navigation mode (rotary, d-pad, or keyboard). This is
 * observational state only; Compose focus is never used as the rotary source of truth.
 */
public val LocalIsInTouchMode: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { true }

/** Remembers one controller for a single-Activity application. */
@Composable
public fun rememberRotaryFocusController(
    validationMode: RotaryValidationMode = RotaryValidationMode.Log
): RotaryFocusController = remember(validationMode) { RotaryFocusController(validationMode) }

/**
 * Native root used by the recommended [ComponentActivity.setRotaryContent] integration.
 * [FocusParkingView] is the first real focusable View in the window.
 */
public class RotaryFocusHostView @JvmOverloads constructor(
    context: Context,
    public val controller: RotaryFocusController = RotaryFocusController(),
    public val hostId: String = "activity"
) : FrameLayout(context) {
    private val currentContent = mutableStateOf<(@Composable () -> Unit)?>(
        value = null,
        policy = neverEqualPolicy()
    )
    private val touchModeState = mutableStateOf(true)
    private val parkingView = FocusParkingView(context).apply {
        id = View.generateViewId()
        setShouldRestoreFocus(true)
    }
    private val renderView = RotaryRenderComposeView(context).apply {
        id = View.generateViewId()
        isFocusable = false
        isFocusableInTouchMode = false
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    }
    private var compositionInstalled: Boolean = false
    private var parentCompositionContext: CompositionContext? = null
    private var focusListenerRegistered: Boolean = false
    private var touchModeListenerRegistered: Boolean = false

    internal val observedIsInTouchMode: Boolean
        get() = touchModeState.value

    private val focusChangeListener = ViewTreeObserver.OnGlobalFocusChangeListener { old, new ->
        RotaryFocusLogger.debug {
            "HOST View focus event; host=$hostId old=${old.debugIdentity()} " +
                "new=${new.debugIdentity()} window=${windowToken?.hashCode()}"
        }
    }

    init {
        require(hostId.isNotBlank()) { "hostId must not be blank" }
        isFocusable = false
        isFocusableInTouchMode = false
        clipChildren = false
        addView(parkingView, LayoutParams(1, 1))
        addView(
            renderView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    public fun setParentCompositionContext(parent: CompositionContext?) {
        parentCompositionContext = parent
        if (!compositionInstalled && parent != null) renderView.setParentCompositionContext(parent)
    }

    public fun setContent(content: @Composable () -> Unit) {
        currentContent.value = content
        ensureComposition()
    }

    public fun parkFocus(): Boolean = parkingView.parkFocus()

    public fun setOnDismissPopupWindow(handler: (() -> Unit)?) {
        parkingView.setOnDismissPopupWindow(handler)
    }

    public fun dumpFocusHierarchy(): String = controller.dumpHierarchy()

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val deferredHandoff =
            shouldActivateDeferredFocusForRotary(
                isInTouchMode = touchModeState.value,
                hasDeferredFocus = controller.hasDeferredFocusRequest,
                isRotaryScroll =
                    event.action == MotionEvent.ACTION_SCROLL &&
                        event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
            )
        if (deferredHandoff) {
            // A physical rotary action is authoritative even if ViewTreeObserver publishes the
            // mode transition one frame later. This also makes the restored ring visible in the
            // same interaction instead of requiring a sacrificial detent.
            touchModeState.value = false
            if (controller.activateDeferredFocus()) return true
        }
        if (controller.dispatchRotaryMotionEvent(event, hostId)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    private val touchModeChangeListener =
        ViewTreeObserver.OnTouchModeChangeListener { inTouchMode ->
            touchModeState.value = inTouchMode
            RotaryFocusLogger.debug {
                "Host input mode changed; host=$hostId " +
                    "mode=${if (inTouchMode) "TOUCH" else "NON_TOUCH_FOCUS"}"
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureComposition()
        controller.registerParkingView(hostId, parkingView)
        touchModeState.value = isInTouchMode
        if (!focusListenerRegistered && viewTreeObserver.isAlive) {
            viewTreeObserver.addOnGlobalFocusChangeListener(focusChangeListener)
            focusListenerRegistered = true
        }
        if (!touchModeListenerRegistered && viewTreeObserver.isAlive) {
            viewTreeObserver.addOnTouchModeChangeListener(touchModeChangeListener)
            touchModeListenerRegistered = true
        }
        RotaryFocusLogger.debug {
            "Host attached; host=$hostId viewId=$id window=${windowToken?.hashCode()} " +
                "childCount=$childCount"
        }
    }

    override fun onDetachedFromWindow() {
        if (focusListenerRegistered && viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnGlobalFocusChangeListener(focusChangeListener)
        }
        if (touchModeListenerRegistered && viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnTouchModeChangeListener(touchModeChangeListener)
        }
        focusListenerRegistered = false
        touchModeListenerRegistered = false
        controller.unregisterParkingView(hostId, parkingView)
        compositionInstalled = false
        RotaryFocusLogger.debug { "Host detached; host=$hostId viewId=$id" }
        super.onDetachedFromWindow()
    }

    private fun ensureComposition() {
        if (compositionInstalled || currentContent.value == null) return
        parentCompositionContext?.let(renderView::setParentCompositionContext)
        renderView.setRotaryContent {
            CompositionLocalProvider(
                LocalRotaryFocusController provides controller,
                LocalIsInTouchMode provides touchModeState.value
            ) {
                currentContent.value?.invoke()
            }
        }
        compositionInstalled = true
    }

    private fun Any?.debugIdentity(): String = when (this) {
        null -> "null"
        is FocusItemView -> "FocusItem($target,id=$id)"
        is FocusParkingView -> "FocusParkingView(id=$id)"
        is View -> "${javaClass.simpleName}(id=$id,focused=$isFocused)"
        else -> javaClass.simpleName
    }
}

internal fun shouldActivateDeferredFocusForRotary(
    isInTouchMode: Boolean,
    hasDeferredFocus: Boolean,
    isRotaryScroll: Boolean
): Boolean = isInTouchMode && hasDeferredFocus && isRotaryScroll

/**
 * Recommended Activity entry point. It avoids a standard ComposeView accessibility ancestor so
 * AAOS can use the real View focus chain and `nextFocusForwardId` exactly as it does for XML UI.
 */
public fun ComponentActivity.setRotaryContent(
    controller: RotaryFocusController = RotaryFocusController(),
    hostId: String = "activity",
    content: @Composable () -> Unit
): RotaryFocusController {
    val host = RotaryFocusHostView(this, controller, hostId)
    setContentView(host)
    host.setContent(content)
    return controller
}

/**
 * Interop host for an existing Compose tree.
 *
 * Prefer [ComponentActivity.setRotaryContent] for production AAOS. A standard outer ComposeView is
 * treated as a virtual hierarchy by some RotaryService versions, which can reduce support for
 * custom next/previous links even though focus nodes remain real Views.
 */
@Composable
public fun RotaryFocusHost(
    modifier: Modifier = Modifier,
    controller: RotaryFocusController = rememberRotaryFocusController(),
    hostId: String = "compose-host",
    content: @Composable () -> Unit
) {
    val parentContext = rememberCompositionContext()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            RotaryFocusHostView(context, controller, hostId).apply {
                setParentCompositionContext(parentContext)
                setContent(content)
            }
        },
        update = { host ->
            host.setParentCompositionContext(parentContext)
            host.setContent(content)
        }
    )
}

/**
 * ComposeView with a non-Compose accessibility class name. All interactive descendants of this
 * host are real FocusItemViews; virtual Compose descendants are hidden at each FocusItem boundary.
 */
private class RotaryRenderComposeView(context: Context) : AbstractComposeView(context) {
    private val content = mutableStateOf<(@Composable () -> Unit)?>(
        value = null,
        policy = neverEqualPolicy()
    )

    @Composable
    override fun Content() {
        content.value?.invoke()
    }

    fun setRotaryContent(content: @Composable () -> Unit) {
        this.content.value = content
    }

    override fun getAccessibilityClassName(): CharSequence = "android.view.ViewGroup"
}
