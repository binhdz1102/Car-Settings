package com.b231001.bmaterial.ccp.rotaryfocus

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@DslMarker
public annotation class RotaryFocusDsl

/**
 * Receiver retained for source-compatible `FocusArea { ... }` syntax.
 *
 * [FocusItem] is intentionally a top-level composable. It can therefore be called from any
 * descendant composable, including extracted functions and lazy-list item content.
 */
@RotaryFocusDsl
public class FocusAreaScope internal constructor() {
    /** Source-compatible member form; extracted composables may call the top-level [FocusItem]. */
    @Composable
    public fun FocusItem(
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
        content: @Composable (FocusItemRenderState) -> Unit
    ) {
        com.b231001.bmaterial.ccp.rotaryfocus.FocusItem(
            id = id,
            modifier = modifier,
            isEnabled = isEnabled,
            nextFocusItem = nextFocusItem,
            previousFocusItem = previousFocusItem,
            onClick = onClick,
            directManipulation = directManipulation,
            semantics = semantics,
            touchBehavior = touchBehavior,
            layout = layout,
            bringIntoView = bringIntoView,
            content = content
        )
    }

    /** Lowercase alias retained for existing DSL call sites. */
    @SuppressLint("ComposableNaming")
    @Composable
    public fun item(
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
        content: @Composable (FocusItemRenderState) -> Unit
    ) {
        FocusItem(
            id = id,
            modifier = modifier,
            isEnabled = isEnabled,
            nextFocusItem = nextFocusItem,
            previousFocusItem = previousFocusItem,
            onClick = onClick,
            directManipulation = directManipulation,
            semantics = semantics,
            touchBehavior = touchBehavior,
            layout = layout,
            bringIntoView = bringIntoView,
            content = content
        )
    }
}

internal val LocalRotaryFocusArea: ProvidableCompositionLocal<RotaryFocusAreaView?> =
    staticCompositionLocalOf { null }

/**
 * Compose DSL backed by an actual [com.android.car.ui.FocusArea]. Compose only performs layout and
 * rendering; every [FocusItem] still inserts a real [FocusItemView] descendant into the Android
 * View hierarchy.
 *
 * [focusOrder] overrides layout/declaration order. For example, `listOf(one, three, two)` with
 * [wrapAround] enabled traverses `one -> three -> two -> one`. [onFocusItemUnavailable] supports
 * logical items which do not have a View yet, most commonly offscreen lazy-list items.
 */
@Composable
public fun FocusArea(
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
    content: @Composable FocusAreaScope.() -> Unit
) {
    check(LocalRotaryFocusArea.current == null) {
        "FocusArea '$id' is nested in another FocusArea. FocusArea nesting is unsupported."
    }
    val controller = LocalRotaryFocusController.current
        ?: error("FocusArea must be placed inside RotaryFocusHost or setRotaryContent")
    val parentContext = rememberCompositionContext()
    val scope = remember(id) { FocusAreaScope() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            RotaryFocusAreaView(context, id, controller).apply {
                setParentCompositionContext(parentContext)
                updateConfiguration(
                    initialFocus = firstFocusAt,
                    focusAllowed = isFocusAllowed,
                    wrapAround = wrapAround,
                    nextFocusArea = nextFocusArea,
                    previousFocusArea = previousFocusArea,
                    focusOrder = focusOrder,
                    revealHandler = onFocusItemUnavailable,
                    layoutOrientation = layout.orientation
                )
                setAreaContent {
                    FocusAreaLayoutContent(
                        area = this,
                        scope = scope,
                        layout = layout,
                        contentPadding = contentPadding,
                        content = content
                    )
                }
            }
        },
        update = { area ->
            area.setParentCompositionContext(parentContext)
            area.updateConfiguration(
                initialFocus = firstFocusAt,
                focusAllowed = isFocusAllowed,
                wrapAround = wrapAround,
                nextFocusArea = nextFocusArea,
                previousFocusArea = previousFocusArea,
                focusOrder = focusOrder,
                revealHandler = onFocusItemUnavailable,
                layoutOrientation = layout.orientation
            )
            area.setAreaContent {
                FocusAreaLayoutContent(
                    area = area,
                    scope = scope,
                    layout = layout,
                    contentPadding = contentPadding,
                    content = content
                )
            }
        }
    )
}

/**
 * Inserts one real focusable [FocusItemView] at the current Compose layout position.
 *
 * This function may be called anywhere below [FocusArea], including from an extracted composable.
 * It must not be placed in a Compose `Dialog`/`Popup`; use [RotaryFocusDialog] and a separate
 * [FocusArea] because a popup belongs to a different Android window.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun FocusItem(
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
    content: @Composable (FocusItemRenderState) -> Unit
) {
    val area = LocalRotaryFocusArea.current
        ?: error("FocusItem '$id' must be composed below a FocusArea")
    val controller = LocalRotaryFocusController.current
        ?: error("FocusItem '$id' requires RotaryFocusHost or setRotaryContent")
    val destinationKey = LocalRotaryFocusDestinationKey.current
    val pinnableContainer = LocalPinnableContainer.current
    val parentContext = rememberCompositionContext()
    val density = LocalDensity.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val target = remember(area.areaId, id) { RotaryFocusTarget(area.areaId, id) }
    val spec = with(density) {
        FocusItemSpec(
            id = id,
            isEnabled = isEnabled,
            nextFocusItem = nextFocusItem,
            previousFocusItem = previousFocusItem,
            onClick = onClick,
            directManipulation = directManipulation,
            semantics = semantics,
            touchBehavior = touchBehavior,
            layout = layout,
            widthPx = roundOrNull(layout.width),
            heightPx = roundOrNull(layout.height),
            minWidthPx = roundOrZero(layout.minWidth),
            minHeightPx = roundOrZero(layout.minHeight),
            destinationKey = destinationKey,
            content = content
        )
    }
    val itemModifier = modifier
        .applyFocusItemLayout(layout, area.layoutOrientation)
        .then(
            if (bringIntoView == FocusItemBringIntoViewBehavior.Auto) {
                Modifier.bringIntoViewRequester(bringIntoViewRequester)
            } else {
                Modifier
            }
        )

    key(target) {
        AndroidView(
            modifier = itemModifier,
            factory = { context ->
                FocusItemView(
                    context = context,
                    target = target,
                    controller = controller,
                    area = area
                ).apply {
                    installComposition(parentContext)
                    setPinnableContainer(pinnableContainer)
                    update(spec, area.isFocusAllowed)
                }
            },
            update = { item ->
                item.installComposition(parentContext)
                item.setPinnableContainer(pinnableContainer)
                item.setComposeBringIntoViewRequest(
                    if (bringIntoView == FocusItemBringIntoViewBehavior.Auto) {
                        {
                            coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                        }
                    } else {
                        null
                    }
                )
                item.update(spec, area.isFocusAllowed)
            }
        )
    }
}

@Composable
private fun FocusAreaLayoutContent(
    area: RotaryFocusAreaView,
    scope: FocusAreaScope,
    layout: FocusAreaLayout,
    contentPadding: PaddingValues,
    content: @Composable FocusAreaScope.() -> Unit
) {
    CompositionLocalProvider(LocalRotaryFocusArea provides area) {
        when (layout.orientation) {
            FocusAreaOrientation.Vertical -> Column(
                modifier =
                    (if (layout.fillMainAxis) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                        .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(layout.itemSpacing)
            ) {
                content(scope)
            }

            FocusAreaOrientation.Horizontal -> Row(
                modifier =
                    (if (layout.fillMainAxis) Modifier.fillMaxSize() else Modifier.fillMaxHeight())
                        .padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(layout.itemSpacing)
            ) {
                content(scope)
            }
        }
    }
}

private fun Modifier.applyFocusItemLayout(
    layout: FocusItemLayout,
    orientation: FocusAreaOrientation
): Modifier {
    var result = this
    result = when {
        layout.width.isSpecified() -> result.width(layout.width)
        layout.fillCrossAxis && orientation == FocusAreaOrientation.Vertical ->
            result.fillMaxWidth()
        else -> result
    }
    result = when {
        layout.height.isSpecified() -> result.height(layout.height)
        layout.fillCrossAxis && orientation == FocusAreaOrientation.Horizontal ->
            result.fillMaxHeight()
        else -> result
    }
    if (layout.minWidth.isSpecified() || layout.minHeight.isSpecified()) {
        result = result.defaultMinSize(
            minWidth = layout.minWidth.takeIf { it.isSpecified() }
                ?: androidx.compose.ui.unit.Dp.Unspecified,
            minHeight = layout.minHeight.takeIf { it.isSpecified() }
                ?: androidx.compose.ui.unit.Dp.Unspecified
        )
    }
    return result
}

private fun androidx.compose.ui.unit.Dp.isSpecified(): Boolean = !value.isNaN()

private fun androidx.compose.ui.unit.Density.roundOrNull(
    value: androidx.compose.ui.unit.Dp
): Int? = if (value.isSpecified()) (value.value * density).roundToInt() else null

private fun androidx.compose.ui.unit.Density.roundOrZero(
    value: androidx.compose.ui.unit.Dp
): Int = if (value.isSpecified()) (value.value * density).roundToInt() else 0
