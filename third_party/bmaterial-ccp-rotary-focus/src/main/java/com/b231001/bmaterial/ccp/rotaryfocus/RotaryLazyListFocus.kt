package com.b231001.bmaterial.ccp.rotaryfocus

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Creates the reveal bridge required when a [FocusArea] `focusOrder` contains lazy items which
 * have not been composed yet.
 *
 * [itemIds] must use the same stable order and keys as the matching `LazyColumn`/`LazyRow` items.
 * [firstItemIndex] accounts for non-focusable lazy headers declared before those items. The bridge
 * uses [LazyFocusScrollBehavior.Animated] by default, then the controller focuses the real
 * [FocusItemView] after AndroidView registration and layout.
 */
@Composable
public fun rememberLazyListFocusHandler(
    state: LazyListState,
    itemIds: List<FocusItemId>,
    firstItemIndex: Int = 0,
    scrollOffset: Int = 0
): FocusItemRevealHandler = rememberLazyListFocusHandler(
    state = state,
    itemIds = itemIds,
    scrollBehavior = LazyFocusScrollBehavior.Animated,
    firstItemIndex = firstItemIndex,
    scrollOffset = scrollOffset
)

/** Behavior-configurable overload of [rememberLazyListFocusHandler]. */
@Composable
public fun rememberLazyListFocusHandler(
    state: LazyListState,
    itemIds: List<FocusItemId>,
    scrollBehavior: LazyFocusScrollBehavior,
    firstItemIndex: Int = 0,
    scrollOffset: Int = 0
): FocusItemRevealHandler {
    require(firstItemIndex >= 0) { "firstItemIndex must be >= 0" }
    require(itemIds.size == itemIds.distinct().size) {
        "itemIds passed to rememberLazyListFocusHandler must be unique"
    }
    val scope = rememberCoroutineScope()
    val indexById = remember(itemIds, firstItemIndex) {
        itemIds.mapIndexed { index, id -> id to (index + firstItemIndex) }.toMap()
    }
    return rememberLazyListFocusHandler(
        state = state,
        itemIndexById = indexById,
        scrollOffset = scrollOffset,
        scrollBehavior = scrollBehavior,
        scope = scope
    )
}

/**
 * Map-based lazy reveal bridge for lists containing headers, separators, advertisements, or any
 * other non-focusable rows between FocusItems. Values are absolute lazy-layout indices.
 */
@Composable
public fun rememberLazyListFocusHandler(
    state: LazyListState,
    itemIndexById: Map<FocusItemId, Int>,
    scrollOffset: Int = 0
): FocusItemRevealHandler = rememberLazyListFocusHandler(
    state = state,
    itemIndexById = itemIndexById,
    scrollBehavior = LazyFocusScrollBehavior.Animated,
    scrollOffset = scrollOffset
)

/** Behavior-configurable map overload of [rememberLazyListFocusHandler]. */
@Composable
public fun rememberLazyListFocusHandler(
    state: LazyListState,
    itemIndexById: Map<FocusItemId, Int>,
    scrollBehavior: LazyFocusScrollBehavior,
    scrollOffset: Int = 0
): FocusItemRevealHandler {
    require(itemIndexById.values.all { it >= 0 }) {
        "itemIndexById passed to rememberLazyListFocusHandler must contain non-negative indices"
    }
    val scope = rememberCoroutineScope()
    val stableIndexById = remember(itemIndexById) { itemIndexById.toMap() }
    return rememberLazyListFocusHandler(
        state = state,
        itemIndexById = stableIndexById,
        scrollOffset = scrollOffset,
        scrollBehavior = scrollBehavior,
        scope = scope
    )
}

/** Controls how an offscreen lazy FocusItem is brought into the composed viewport. */
public enum class LazyFocusScrollBehavior {
    /** Smoothly scrolls toward the target before real View focus is completed. */
    Animated,

    /** Jumps directly to the target; useful for non-visual state restoration. */
    Immediate
}

@Composable
private fun rememberLazyListFocusHandler(
    state: LazyListState,
    itemIndexById: Map<FocusItemId, Int>,
    scrollOffset: Int,
    scrollBehavior: LazyFocusScrollBehavior,
    scope: kotlinx.coroutines.CoroutineScope
): FocusItemRevealHandler {
    val jobHolder = remember { RevealJobHolder() }
    DisposableEffect(jobHolder) {
        onDispose { jobHolder.job?.cancel() }
    }
    return remember(state, itemIndexById, scrollOffset, scrollBehavior, scope, jobHolder) {
        LazyListRevealHandler(
            state = state,
            itemIndexById = itemIndexById,
            scrollOffset = scrollOffset,
            scrollBehavior = scrollBehavior,
            scope = scope,
            jobHolder = jobHolder
        )
    }
}

private class RevealJobHolder {
    var job: Job? = null
}

private class LazyListRevealHandler(
    private val state: LazyListState,
    private val itemIndexById: Map<FocusItemId, Int>,
    private val scrollOffset: Int,
    private val scrollBehavior: LazyFocusScrollBehavior,
    private val scope: CoroutineScope,
    private val jobHolder: RevealJobHolder
) : FocusItemRevealHandler {
    override fun requestReveal(itemId: FocusItemId): Boolean =
        requestReveal(itemId, onFailure = {})

    override fun requestReveal(
        itemId: FocusItemId,
        onFailure: (Throwable) -> Unit
    ): Boolean {
        val index = itemIndexById[itemId] ?: return false
        val itemCount = state.layoutInfo.totalItemsCount
        if (itemCount > 0 && index >= itemCount) {
            RotaryFocusLogger.warning {
                "Lazy focus materialization rejected; item=$itemId index=$index " +
                    "itemCount=$itemCount"
            }
            return false
        }
        jobHolder.job?.cancel()
        jobHolder.job = scope.launch {
            RotaryFocusLogger.debug {
                "Lazy focus materialization started; item=$itemId index=$index " +
                    "offset=$scrollOffset behavior=$scrollBehavior"
            }
            try {
                when (scrollBehavior) {
                    LazyFocusScrollBehavior.Animated ->
                        state.animateScrollToItem(index, scrollOffset)
                    LazyFocusScrollBehavior.Immediate ->
                        state.scrollToItem(index, scrollOffset)
                }
                RotaryFocusLogger.debug {
                    "Lazy focus materialization completed; item=$itemId index=$index " +
                        "behavior=$scrollBehavior"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                RotaryFocusLogger.error(
                    { "Lazy focus materialization failed; item=$itemId index=$index" },
                    error
                )
                onFailure(error)
            }
        }
        return true
    }
}
