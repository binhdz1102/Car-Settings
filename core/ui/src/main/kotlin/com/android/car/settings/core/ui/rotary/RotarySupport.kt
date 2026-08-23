package com.android.car.settings.core.ui

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import com.b231001.bmaterial.ccp.rotaryfocus.DirectManipulationConfig
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemBringIntoViewBehavior
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRenderState
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRevealHandler
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemSemantics
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemTouchBehavior
import com.b231001.bmaterial.ccp.rotaryfocus.LocalRotaryFocusController
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusController
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusDestination
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusTarget
import com.b231001.bmaterial.ccp.rotaryfocus.setRotaryContent
import com.b231001.bmaterial.ccp.rotaryfocus.FocusArea as BMaterialFocusArea
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItem as BMaterialFocusItem

private const val ROTARY_LOG_TAG = "MySystemRotary"

/** True while rendering in a hostless Compose/test/OEM fallback tree. */
private val LocalRotaryFallback = staticCompositionLocalOf { false }

/** Whether the caller is running without an activity-level rotary host. */
@Composable
internal fun isRotaryFallbackMode(): Boolean = LocalRotaryFallback.current || LocalRotaryFocusController.current == null

/**
 * Installs B-Material's real-View rotary host while retaining a touch-safe fallback for images
 * where an OEM rotary implementation is absent or incompatible.
 */
fun ComponentActivity.setSafeRotaryContent(
    hostId: String,
    controller: RotaryFocusController = RotaryFocusController(),
    content: @Composable () -> Unit,
) {
    try {
        setRotaryContent(controller = controller, hostId = hostId, content = content)
        Log.i(ROTARY_LOG_TAG, "Rotary host installed: $hostId")
    } catch (exception: RuntimeException) {
        Log.e(
            ROTARY_LOG_TAG,
            "Rotary host failed for $hostId; falling back to touch-safe Compose content",
            exception,
        )
        setContent(content = content)
    } catch (error: LinkageError) {
        Log.e(
            ROTARY_LOG_TAG,
            "Rotary host is incompatible for $hostId; falling back to touch-safe Compose content",
            error,
        )
        setContent(content = content)
    }
}

/**
 * Stable navigation boundary used to preserve the real Android View focus target on Back.
 *
 * Compose previews, unit tests, and OEM builds that cannot install the rotary host do not
 * provide a [LocalRotaryFocusController]. In that case we deliberately render the destination
 * as ordinary Compose content instead of throwing during composition. The activity-level host
 * still uses the full B-Material focus destination whenever the controller is available.
 */
@Composable
fun RotaryDestination(
    destinationKey: String,
    fallback: RotaryFocusTarget? = null,
    content: @Composable () -> Unit,
) {
    val controller = LocalRotaryFocusController.current
    if (controller == null) {
        // Hostless Compose (tests/previews/unsupported OEM image): keep the normal Compose
        // semantics and touch interaction, while our focus wrappers bypass real View nodes.
        CompositionLocalProvider(LocalRotaryFallback provides true, content = content)
    } else {
        RotaryFocusDestination(
            destinationKey = destinationKey,
            fallback = fallback,
            parkOnDispose = true,
            controller = controller,
            content = content,
        )
    }
}

/**
 * Safe first-party boundary around B-Material's real-View [BMaterialFocusArea].
 *
 * The vendored library intentionally throws when a caller forgets to install a host. Production
 * activities always install one; previews, Compose tests, and unsupported OEM images use the
 * plain Compose branch so the same UI remains interactive and cannot crash during composition.
 */
@Composable
fun FocusArea(
    id: FocusAreaId,
    modifier: Modifier = Modifier,
    layout: FocusAreaLayout = FocusAreaLayout(),
    firstFocusAt: FocusItemId? = null,
    isFocusAllowed: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
    wrapAround: Boolean = false,
    nextFocusArea: FocusAreaId? = null,
    previousFocusArea: FocusAreaId? = null,
    focusOrder: List<FocusItemId> = emptyList(),
    onFocusItemUnavailable: FocusItemRevealHandler? = null,
    content: @Composable () -> Unit,
) {
    if (LocalRotaryFallback.current || LocalRotaryFocusController.current == null) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier,
        ) {
            content()
        }
    } else {
        BMaterialFocusArea(
            id = id,
            modifier = modifier,
            layout = layout,
            firstFocusAt = firstFocusAt,
            isFocusAllowed = isFocusAllowed,
            contentPadding = contentPadding,
            wrapAround = wrapAround,
            nextFocusArea = nextFocusArea,
            previousFocusArea = previousFocusArea,
            focusOrder = focusOrder,
            onFocusItemUnavailable = onFocusItemUnavailable,
        ) {
            content()
        }
    }
}

/** Safe first-party boundary around B-Material's real-View [BMaterialFocusItem]. */
@Composable
fun FocusItem(
    id: FocusItemId,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    nextFocusItem: FocusItemId? = null,
    previousFocusItem: FocusItemId? = null,
    onClick: (() -> Unit)? = null,
    directManipulation: DirectManipulationConfig? = null,
    semantics: FocusItemSemantics = FocusItemSemantics(),
    touchBehavior: FocusItemTouchBehavior = FocusItemTouchBehavior.View,
    layout: FocusItemLayout = FocusItemLayout(),
    bringIntoView: FocusItemBringIntoViewBehavior = FocusItemBringIntoViewBehavior.Auto,
    content: @Composable (FocusItemRenderState) -> Unit,
) {
    RotaryFocusIdRegistry.Observe(id)
    if (LocalRotaryFallback.current || LocalRotaryFocusController.current == null) {
        val fallbackState = FocusItemRenderState(isEnabled = isEnabled)
        RotaryFocusContent(
            state = fallbackState,
            content = { content(fallbackState) },
        )
    } else {
        // B-Material intentionally hides the Compose renderer from accessibility because the
        // real Android FocusItemView is the rotary source of truth. Keep an equivalent Compose
        // semantics boundary around that view as well: it makes Compose UI tests deterministic
        // and preserves discoverable labels for hostless/OEM accessibility bridges without
        // changing the vendored runtime behavior.
        // The B-Material FocusItem must be the immediate child of FocusArea.  A wrapper Box here
        // used to hide the item's parent-data and allowed the first settings row to consume the
        // complete viewport.  Keep accessibility semantics on the real item modifier instead.
        val accessibilityModifier =
            modifier.semantics(mergeDescendants = true) {
                semantics.label?.let {
                    this.contentDescription = it
                    this.text = AnnotatedString(it)
                }
                semantics.stateDescription?.let { this.stateDescription = it }
                role = Role.Button
                if (!isEnabled) disabled()
                if (onClick != null && isEnabled) {
                    onClick {
                        onClick()
                        true
                    }
                }
            }
        BMaterialFocusItem(
            id = id,
            modifier = accessibilityModifier,
            isEnabled = isEnabled,
            nextFocusItem = nextFocusItem,
            previousFocusItem = previousFocusItem,
            onClick = onClick,
            directManipulation = directManipulation,
            semantics = semantics,
            touchBehavior = touchBehavior,
            layout = layout,
            bringIntoView = bringIntoView,
            content = { state ->
                RotaryFocusContent(
                    state = state,
                    content = { content(state) },
                )
            },
        )
    }
}

/** Lightweight debug/test guard against two live FocusItems sharing an identity. */
private object RotaryFocusIdRegistry {
    private val lock = Any()
    private val activeCounts = mutableMapOf<String, Int>()

    @Composable
    fun Observe(id: FocusItemId) {
        val key = id.value
        DisposableEffect(key) {
            val duplicate =
                synchronized(lock) {
                    val current = activeCounts[key] ?: 0
                    activeCounts[key] = current + 1
                    current > 0
                }
            if (duplicate) {
                Log.e(ROTARY_LOG_TAG, "Duplicate live FocusItemId=$key")
            }
            onDispose {
                synchronized(lock) {
                    val current = activeCounts[key] ?: return@synchronized
                    if (current <= 1) activeCounts.remove(key) else activeCounts[key] = current - 1
                }
            }
        }
    }
}
