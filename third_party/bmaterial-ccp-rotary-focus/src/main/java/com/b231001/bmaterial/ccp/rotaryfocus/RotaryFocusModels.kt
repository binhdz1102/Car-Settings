package com.b231001.bmaterial.ccp.rotaryfocus

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stable identifier of a rotary focus area within a [RotaryFocusController]. */
@JvmInline
public value class FocusAreaId(public val value: String) {
    init {
        require(value.isNotBlank()) { "FocusAreaId must not be blank" }
    }

    override fun toString(): String = value
}

/** Stable identifier of an item within a [FocusAreaId]. */
@JvmInline
public value class FocusItemId(public val value: String) {
    init {
        require(value.isNotBlank()) { "FocusItemId must not be blank" }
    }

    override fun toString(): String = value
}

/** Globally unambiguous focus destination used for screen and dialog restoration. */
@Immutable
public data class RotaryFocusTarget(
    val areaId: FocusAreaId,
    val itemId: FocusItemId
)

public enum class FocusAreaOrientation {
    Horizontal,
    Vertical
}

public enum class RotaryNudgeDirection {
    Left,
    Right,
    Up,
    Down
}

/** A rotary encoder event delivered while an item is in direct-manipulation mode. */
@Immutable
public data class RotaryRotationEvent(
    val detents: Float,
    val eventTimeMillis: Long,
    val axis: Int
)

/** A tilt/nudge event delivered while an item is in direct-manipulation mode. */
@Immutable
public data class RotaryNudgeEvent(
    val direction: RotaryNudgeDirection,
    val repeatCount: Int,
    val eventTimeMillis: Long
)

/** Read-only state rendered by the Compose content hosted in a real [FocusItemView]. */
@Immutable
public data class FocusItemRenderState(
    val isFocused: Boolean = false,
    val isPressed: Boolean = false,
    val isEnabled: Boolean = true,
    val isDirectManipulationMode: Boolean = false
)

/** Advanced direct-manipulation callbacks for sliders, maps, pickers, and similar controls. */
public data class DirectManipulationConfig(
    val onRotary: (RotaryRotationEvent) -> Unit,
    val onNudge: ((RotaryNudgeEvent) -> Unit)? = null,
    val onModeChanged: (Boolean) -> Unit = {},
    val enterOnCenter: Boolean = true,
    val exitOnBack: Boolean = true
)

/** Accessibility role exposed by the real Android View focus node. */
public sealed interface FocusItemRole {
    public val accessibilityClassName: String

    public data object Button : FocusItemRole {
        override val accessibilityClassName: String = "android.widget.Button"
    }

    public data object Toggle : FocusItemRole {
        override val accessibilityClassName: String = "android.widget.Switch"
    }

    public data object Adjustable : FocusItemRole {
        override val accessibilityClassName: String = "android.widget.SeekBar"
    }

    public data class Custom(override val accessibilityClassName: String) : FocusItemRole {
        init {
            require(accessibilityClassName.isNotBlank()) {
                "The custom accessibility class name must not be blank"
            }
        }
    }
}

/** Semantics are placed on the View wrapper; Compose descendants stay render-only. */
@Immutable
public data class FocusItemSemantics(
    val label: String? = null,
    val stateDescription: String? = null,
    val role: FocusItemRole = FocusItemRole.Button
)

/**
 * View layout options. Unspecified dimensions are measured from Compose content.
 * Parent-data such as weight must be supplied through [androidx.compose.ui.Modifier] at the
 * immediate Row/Column call site.
 */
@Suppress("DEPRECATION")
@Immutable
public data class FocusItemLayout(
    val width: Dp = Dp.Unspecified,
    val height: Dp = Dp.Unspecified,
    val minWidth: Dp = Dp.Unspecified,
    val minHeight: Dp = Dp.Unspecified,
    val fillCrossAxis: Boolean = true,
    @Deprecated(
        message = "FocusItemLayout.weight cannot describe nested Compose parent data. " +
            "Use Modifier.weight() in the immediate RowScope or ColumnScope.",
        level = DeprecationLevel.WARNING
    )
    val weight: Float = 0f
) {
    init {
        require(weight >= 0f) { "FocusItemLayout.weight must be >= 0" }
    }
}

/** Layout configuration for a real Android [com.android.car.ui.FocusArea]. */
@Immutable
public data class FocusAreaLayout(
    val orientation: FocusAreaOrientation = FocusAreaOrientation.Vertical,
    val itemSpacing: Dp = 0.dp
)

public enum class RotaryValidationMode {
    Off,
    Log,
    Strict
}

/** Controls whether the wrapper or nested Compose rendering consumes touch events. */
public enum class FocusItemTouchBehavior {
    /** Recommended: the real View owns click and touch; Compose only renders. */
    View,

    /** Opt-in for touch-rich Compose content. Android View still owns rotary focus. */
    ComposeContent
}

/** Controls whether real View focus asks Compose and Android scroll parents to reveal the item. */
public enum class FocusItemBringIntoViewBehavior {
    /** Recommended. A focused item is scrolled fully into the visible viewport. */
    Auto,

    /** The application owns all scrolling for this item. */
    Disabled
}

/**
 * Materializes an item which is part of an area's logical order but has no real View yet.
 *
 * This is primarily used by lazy layouts. Return `true` when the request was accepted. Once the
 * item is composed and its [FocusItemView] is registered, the controller completes the queued
 * real-View focus request.
 */
public fun interface FocusItemRevealHandler {
    public fun requestReveal(itemId: FocusItemId): Boolean

    /**
     * Failure-aware overload used by FocusArea. Existing one-argument custom handlers remain
     * source compatible; asynchronous handlers should invoke [onFailure] if an accepted request
     * later proves impossible.
     */
    public fun requestReveal(
        itemId: FocusItemId,
        onFailure: (Throwable) -> Unit
    ): Boolean = requestReveal(itemId)
}
