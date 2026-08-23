package com.android.car.settings.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.uicomponents.scrollbar.BScrollbarStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val AutomotiveScrollbarStyle =
    BScrollbarStyle(
        thickness = 12.dp,
        padding = 8.dp,
        minThumbLength = 64.dp,
        cornerRadius = 6.dp,
    )

/**
 * A full automotive touch target reserved outside scrollable content. The visible thumb is
 * centered inside this gutter, so it never obscures a row or competes with the row's hit target.
 */
val AutomotiveScrollbarGutterWidth = 48.dp

/**
 * Computed metrics for drawing and hit-testing the automotive scrollbar.
 */
data class ScrollbarMetrics(
    val trackLength: Float,
    val thumbLength: Float,
    val thumbOffset: Float,
    val progress: Float,
)

/** Cross-axis placement for a scrollbar that owns a dedicated trailing gutter. */
data class ScrollbarGutterGeometry(
    val gutterStart: Float,
    val gutterEnd: Float,
    val trackCrossOffset: Float,
    val trackThickness: Float,
)

fun computeScrollbarGutterGeometry(
    crossAxisSize: Float,
    thicknessPx: Float,
    gutterWidthPx: Float,
): ScrollbarGutterGeometry? {
    if (crossAxisSize <= 0f || thicknessPx <= 0f || gutterWidthPx <= 0f) return null

    val resolvedGutterWidth = gutterWidthPx.coerceAtMost(crossAxisSize)
    val resolvedThickness = thicknessPx.coerceAtMost(resolvedGutterWidth)
    val gutterStart = crossAxisSize - resolvedGutterWidth
    return ScrollbarGutterGeometry(
        gutterStart = gutterStart,
        gutterEnd = crossAxisSize,
        trackCrossOffset = gutterStart + (resolvedGutterWidth - resolvedThickness) / 2f,
        trackThickness = resolvedThickness,
    )
}

/**
 * Pure function to calculate scrollbar metrics for a [LazyListState].
 */
fun computeLazyListScrollbarMetrics(
    viewportSize: Float,
    totalItemsCount: Int,
    visibleItemSizes: List<Int>,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    paddingPx: Float = 2f,
    minThumbLengthPx: Float = 64f,
): ScrollbarMetrics? {
    if (totalItemsCount <= 0 || visibleItemSizes.isEmpty()) return null
    if (!canScrollBackward && !canScrollForward) return null

    val trackLength = (viewportSize - 2 * paddingPx).coerceAtLeast(0f)
    if (trackLength <= 0f) return null

    val avgItemSize = visibleItemSizes.sum().toFloat() / visibleItemSizes.size
    val estimatedTotalSize = avgItemSize * totalItemsCount
    val rawThumbLength =
        if (estimatedTotalSize > 0f) {
            (viewportSize / estimatedTotalSize) * trackLength
        } else {
            trackLength
        }
    val thumbLength = rawThumbLength.coerceIn(minThumbLengthPx.coerceAtMost(trackLength), trackLength)

    val progress =
        when {
            !canScrollBackward -> 0f
            !canScrollForward -> 1f
            else -> {
                val maxScrollPx = (estimatedTotalSize - viewportSize).coerceAtLeast(1f)
                val currentScrollPx = firstVisibleItemIndex * avgItemSize + firstVisibleItemScrollOffset
                (currentScrollPx / maxScrollPx).coerceIn(0f, 1f)
            }
        }
    val thumbOffset = paddingPx + progress * (trackLength - thumbLength)
    return ScrollbarMetrics(
        trackLength = trackLength,
        thumbLength = thumbLength,
        thumbOffset = thumbOffset,
        progress = progress,
    )
}

/**
 * Pure function to calculate scrollbar metrics for a [ScrollState].
 */
fun computeScrollStateScrollbarMetrics(
    viewportSize: Float,
    scrollValue: Int,
    maxScrollValue: Int,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    paddingPx: Float = 2f,
    minThumbLengthPx: Float = 64f,
): ScrollbarMetrics? {
    if (maxScrollValue <= 0) return null

    val trackLength = (viewportSize - 2 * paddingPx).coerceAtLeast(0f)
    if (trackLength <= 0f) return null

    val totalContentSize = viewportSize + maxScrollValue
    val rawThumbLength = (viewportSize / totalContentSize) * trackLength
    val thumbLength = rawThumbLength.coerceIn(minThumbLengthPx.coerceAtMost(trackLength), trackLength)

    val progress =
        when {
            !canScrollBackward || scrollValue <= 0 -> 0f
            !canScrollForward || scrollValue >= maxScrollValue -> 1f
            else -> (scrollValue.toFloat() / maxScrollValue.toFloat()).coerceIn(0f, 1f)
        }
    val thumbOffset = paddingPx + progress * (trackLength - thumbLength)
    return ScrollbarMetrics(
        trackLength = trackLength,
        thumbLength = thumbLength,
        thumbOffset = thumbOffset,
        progress = progress,
    )
}

private data class TrackBounds(
    val mainStart: Float,
    val mainEnd: Float,
    val crossStart: Float,
    val crossEnd: Float,
    val thumbLength: Float,
    val trackLength: Float,
) {
    fun isHit(
        offset: Offset,
        orientation: Orientation,
    ): Boolean {
        val (mainPos, crossPos) =
            if (orientation == Orientation.Vertical) {
                offset.y to offset.x
            } else {
                offset.x to offset.y
            }
        return mainPos in mainStart..mainEnd && crossPos in crossStart..crossEnd
    }

    fun progressFromTouch(touchPos: Float): Float {
        val availableTrack = (trackLength - thumbLength).coerceAtLeast(1f)
        return ((touchPos - mainStart - thumbLength / 2f) / availableTrack).coerceIn(0f, 1f)
    }
}

/**
 * Shared Automotive scrollbar modifier for regular [ScrollState] containers.
 */
fun Modifier.vehicleBScrollbar(
    scrollState: ScrollState,
    orientation: Orientation = Orientation.Vertical,
    style: BScrollbarStyle = AutomotiveScrollbarStyle,
    gutterWidth: Dp = AutomotiveScrollbarGutterWidth,
    touchToSeekEnabled: Boolean = true,
    showTooltip: Boolean = true,
    autoHideEnabled: Boolean = false,
): Modifier =
    composed {
        val density = LocalDensity.current
        val coroutineScope = rememberCoroutineScope()
        val alphaAnimatable = remember { Animatable(if (autoHideEnabled) 0f else 1f) }
        var isInteracting by remember { mutableStateOf(false) }

        val isScrollInProgress = scrollState.isScrollInProgress
        val canScrollBackward = scrollState.canScrollBackward
        val canScrollForward = scrollState.canScrollForward
        val canScroll = canScrollBackward || canScrollForward || scrollState.maxValue > 0

        LaunchedEffect(isScrollInProgress, isInteracting, canScroll) {
            if (!canScroll) {
                alphaAnimatable.snapTo(0f)
                return@LaunchedEffect
            }
            if (isScrollInProgress || isInteracting) {
                alphaAnimatable.animateTo(1f, animationSpec = tween(150))
            } else if (autoHideEnabled) {
                delay(1500)
                alphaAnimatable.animateTo(0f, animationSpec = tween(500))
            } else {
                alphaAnimatable.animateTo(1f, animationSpec = tween(150))
            }
        }

        val primaryColor = MaterialTheme.colorScheme.primary
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
        val trackColor = onSurfaceColor.copy(alpha = 0.08f)
        val thumbColor =
            if (isInteracting) {
                primaryColor.copy(alpha = 0.85f)
            } else {
                onSurfaceColor.copy(alpha = 0.45f)
            }

        val thicknessPx = with(density) { style.thickness.toPx() }
        val paddingPx = with(density) { style.padding.toPx() }
        val minThumbLengthPx = with(density) { style.minThumbLength.toPx() }
        val cornerRadiusPx = with(density) { style.cornerRadius.toPx() }
        val gutterWidthPx = with(density) { gutterWidth.toPx() }

        var latestTrackBounds by remember { mutableStateOf<TrackBounds?>(null) }

        this
            .then(
                if (touchToSeekEnabled && canScroll) {
                    Modifier
                        .pointerInput(orientation, scrollState, thicknessPx, paddingPx, gutterWidthPx) {
                            detectTapGestures { offset ->
                                val bounds = latestTrackBounds ?: return@detectTapGestures
                                if (!bounds.isHit(offset, orientation)) return@detectTapGestures
                                val touchPos = if (orientation == Orientation.Vertical) offset.y else offset.x
                                val targetProgress = bounds.progressFromTouch(touchPos)
                                val targetScroll = (targetProgress * scrollState.maxValue).roundToInt()
                                coroutineScope.launch {
                                    scrollState.scrollTo(targetScroll)
                                }
                            }
                        }
                        .pointerInput(orientation, scrollState, thicknessPx, paddingPx, gutterWidthPx) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val bounds = latestTrackBounds ?: return@detectDragGestures
                                    if (bounds.isHit(offset, orientation)) {
                                        isInteracting = true
                                        val touchPos = if (orientation == Orientation.Vertical) offset.y else offset.x
                                        val targetProgress = bounds.progressFromTouch(touchPos)
                                        val targetScroll = (targetProgress * scrollState.maxValue).roundToInt()
                                        coroutineScope.launch {
                                            scrollState.scrollTo(targetScroll)
                                        }
                                    }
                                },
                                onDrag = { change, _ ->
                                    val bounds = latestTrackBounds ?: return@detectDragGestures
                                    if (isInteracting) {
                                        change.consume()
                                        val touchPos =
                                            if (orientation == Orientation.Vertical) {
                                                change.position.y
                                            } else {
                                                change.position.x
                                            }
                                        val targetProgress = bounds.progressFromTouch(touchPos)
                                        val targetScroll = (targetProgress * scrollState.maxValue).roundToInt()
                                        coroutineScope.launch {
                                            scrollState.scrollTo(targetScroll)
                                        }
                                    }
                                },
                                onDragEnd = { isInteracting = false },
                                onDragCancel = { isInteracting = false },
                            )
                        }
                } else {
                    Modifier
                },
            )
            .drawWithContent {
                drawContent()

                val alpha = alphaAnimatable.value
                if (alpha <= 0.01f || !canScroll) return@drawWithContent

                val isVertical = orientation == Orientation.Vertical
                val viewportSize = if (isVertical) size.height else size.width
                val crossAxisSize = if (isVertical) size.width else size.height

                val metrics =
                    computeScrollStateScrollbarMetrics(
                        viewportSize = viewportSize,
                        scrollValue = scrollState.value,
                        maxScrollValue = scrollState.maxValue,
                        canScrollBackward = scrollState.canScrollBackward,
                        canScrollForward = scrollState.canScrollForward,
                        paddingPx = paddingPx,
                        minThumbLengthPx = minThumbLengthPx,
                    ) ?: return@drawWithContent

                val gutterGeometry =
                    computeScrollbarGutterGeometry(
                        crossAxisSize = crossAxisSize,
                        thicknessPx = thicknessPx,
                        gutterWidthPx = gutterWidthPx,
                    ) ?: return@drawWithContent

                latestTrackBounds =
                    TrackBounds(
                        mainStart = paddingPx,
                        mainEnd = viewportSize - paddingPx,
                        crossStart = gutterGeometry.gutterStart,
                        crossEnd = gutterGeometry.gutterEnd,
                        thumbLength = metrics.thumbLength,
                        trackLength = metrics.trackLength,
                    )

                // Draw track
                val trackTopLeft =
                    if (isVertical) {
                        Offset(x = gutterGeometry.trackCrossOffset, y = paddingPx)
                    } else {
                        Offset(x = paddingPx, y = gutterGeometry.trackCrossOffset)
                    }
                val trackSize =
                    if (isVertical) {
                        Size(width = gutterGeometry.trackThickness, height = metrics.trackLength)
                    } else {
                        Size(width = metrics.trackLength, height = gutterGeometry.trackThickness)
                    }
                drawRoundRect(
                    color = trackColor.copy(alpha = trackColor.alpha * alpha),
                    topLeft = trackTopLeft,
                    size = trackSize,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                )

                // Draw thumb
                val thumbTopLeft =
                    if (isVertical) {
                        Offset(x = gutterGeometry.trackCrossOffset, y = metrics.thumbOffset)
                    } else {
                        Offset(x = metrics.thumbOffset, y = gutterGeometry.trackCrossOffset)
                    }
                val thumbSize =
                    if (isVertical) {
                        Size(width = gutterGeometry.trackThickness, height = metrics.thumbLength)
                    } else {
                        Size(width = metrics.thumbLength, height = gutterGeometry.trackThickness)
                    }
                drawRoundRect(
                    color = thumbColor.copy(alpha = thumbColor.alpha * alpha),
                    topLeft = thumbTopLeft,
                    size = thumbSize,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                )
            }
            .then(
                if (!canScroll) {
                    Modifier
                } else if (orientation == Orientation.Vertical) {
                    Modifier.padding(end = gutterWidth)
                } else {
                    Modifier.padding(bottom = gutterWidth)
                },
            )
    }

/**
 * Shared Automotive scrollbar modifier for [LazyListState] containers.
 */
fun Modifier.vehicleBLazyScrollbar(
    state: LazyListState,
    orientation: Orientation = Orientation.Vertical,
    style: BScrollbarStyle = AutomotiveScrollbarStyle,
    gutterWidth: Dp = AutomotiveScrollbarGutterWidth,
    touchToSeekEnabled: Boolean = true,
    showTooltip: Boolean = true,
    autoHideEnabled: Boolean = false,
): Modifier =
    composed {
        val density = LocalDensity.current
        val coroutineScope = rememberCoroutineScope()
        val alphaAnimatable = remember { Animatable(if (autoHideEnabled) 0f else 1f) }
        var isInteracting by remember { mutableStateOf(false) }

        val isScrollInProgress = state.isScrollInProgress
        val canScrollBackward = state.canScrollBackward
        val canScrollForward = state.canScrollForward
        val canScroll = canScrollBackward || canScrollForward

        LaunchedEffect(isScrollInProgress, isInteracting, canScroll) {
            if (!canScroll) {
                alphaAnimatable.snapTo(0f)
                return@LaunchedEffect
            }
            if (isScrollInProgress || isInteracting) {
                alphaAnimatable.animateTo(1f, animationSpec = tween(150))
            } else if (autoHideEnabled) {
                delay(1500)
                alphaAnimatable.animateTo(0f, animationSpec = tween(500))
            } else {
                alphaAnimatable.animateTo(1f, animationSpec = tween(150))
            }
        }

        val primaryColor = MaterialTheme.colorScheme.primary
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
        val trackColor = onSurfaceColor.copy(alpha = 0.08f)
        val thumbColor =
            if (isInteracting) {
                primaryColor.copy(alpha = 0.85f)
            } else {
                onSurfaceColor.copy(alpha = 0.45f)
            }

        val thicknessPx = with(density) { style.thickness.toPx() }
        val paddingPx = with(density) { style.padding.toPx() }
        val minThumbLengthPx = with(density) { style.minThumbLength.toPx() }
        val cornerRadiusPx = with(density) { style.cornerRadius.toPx() }
        val gutterWidthPx = with(density) { gutterWidth.toPx() }

        var latestTrackBounds by remember { mutableStateOf<TrackBounds?>(null) }

        this
            .then(
                if (touchToSeekEnabled && canScroll) {
                    Modifier
                        .pointerInput(orientation, state, thicknessPx, paddingPx, gutterWidthPx) {
                            detectTapGestures { offset ->
                                val bounds = latestTrackBounds ?: return@detectTapGestures
                                if (!bounds.isHit(offset, orientation)) return@detectTapGestures
                                val touchPos = if (orientation == Orientation.Vertical) offset.y else offset.x
                                val targetProgress = bounds.progressFromTouch(touchPos)
                                val totalItems = state.layoutInfo.totalItemsCount
                                if (totalItems > 0) {
                                    val targetIndex = (targetProgress * (totalItems - 1)).roundToInt()
                                    coroutineScope.launch {
                                        state.scrollToItem(targetIndex)
                                    }
                                }
                            }
                        }
                        .pointerInput(orientation, state, thicknessPx, paddingPx, gutterWidthPx) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val bounds = latestTrackBounds ?: return@detectDragGestures
                                    if (bounds.isHit(offset, orientation)) {
                                        isInteracting = true
                                        val touchPos = if (orientation == Orientation.Vertical) offset.y else offset.x
                                        val targetProgress = bounds.progressFromTouch(touchPos)
                                        val totalItems = state.layoutInfo.totalItemsCount
                                        if (totalItems > 0) {
                                            val targetIndex = (targetProgress * (totalItems - 1)).roundToInt()
                                            coroutineScope.launch {
                                                state.scrollToItem(targetIndex)
                                            }
                                        }
                                    }
                                },
                                onDrag = { change, _ ->
                                    val bounds = latestTrackBounds ?: return@detectDragGestures
                                    if (isInteracting) {
                                        change.consume()
                                        val touchPos =
                                            if (orientation == Orientation.Vertical) {
                                                change.position.y
                                            } else {
                                                change.position.x
                                            }
                                        val targetProgress = bounds.progressFromTouch(touchPos)
                                        val totalItems = state.layoutInfo.totalItemsCount
                                        if (totalItems > 0) {
                                            val targetIndex = (targetProgress * (totalItems - 1)).roundToInt()
                                            coroutineScope.launch {
                                                state.scrollToItem(targetIndex)
                                            }
                                        }
                                    }
                                },
                                onDragEnd = { isInteracting = false },
                                onDragCancel = { isInteracting = false },
                            )
                        }
                } else {
                    Modifier
                },
            )
            .drawWithContent {
                drawContent()

                val alpha = alphaAnimatable.value
                if (alpha <= 0.01f || !canScroll) return@drawWithContent

                val layoutInfo = state.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val visibleItems = layoutInfo.visibleItemsInfo
                if (totalItems <= 0 || visibleItems.isEmpty()) return@drawWithContent

                val isVertical = orientation == Orientation.Vertical
                val viewportSize = if (isVertical) size.height else size.width
                val crossAxisSize = if (isVertical) size.width else size.height

                val metrics =
                    computeLazyListScrollbarMetrics(
                        viewportSize = viewportSize,
                        totalItemsCount = totalItems,
                        visibleItemSizes = visibleItems.map { it.size },
                        firstVisibleItemIndex = state.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset,
                        canScrollBackward = state.canScrollBackward,
                        canScrollForward = state.canScrollForward,
                        paddingPx = paddingPx,
                        minThumbLengthPx = minThumbLengthPx,
                    ) ?: return@drawWithContent

                val gutterGeometry =
                    computeScrollbarGutterGeometry(
                        crossAxisSize = crossAxisSize,
                        thicknessPx = thicknessPx,
                        gutterWidthPx = gutterWidthPx,
                    ) ?: return@drawWithContent

                latestTrackBounds =
                    TrackBounds(
                        mainStart = paddingPx,
                        mainEnd = viewportSize - paddingPx,
                        crossStart = gutterGeometry.gutterStart,
                        crossEnd = gutterGeometry.gutterEnd,
                        thumbLength = metrics.thumbLength,
                        trackLength = metrics.trackLength,
                    )

                // Draw track
                val trackTopLeft =
                    if (isVertical) {
                        Offset(x = gutterGeometry.trackCrossOffset, y = paddingPx)
                    } else {
                        Offset(x = paddingPx, y = gutterGeometry.trackCrossOffset)
                    }
                val trackSize =
                    if (isVertical) {
                        Size(width = gutterGeometry.trackThickness, height = metrics.trackLength)
                    } else {
                        Size(width = metrics.trackLength, height = gutterGeometry.trackThickness)
                    }
                drawRoundRect(
                    color = trackColor.copy(alpha = trackColor.alpha * alpha),
                    topLeft = trackTopLeft,
                    size = trackSize,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                )

                // Draw thumb
                val thumbTopLeft =
                    if (isVertical) {
                        Offset(x = gutterGeometry.trackCrossOffset, y = metrics.thumbOffset)
                    } else {
                        Offset(x = metrics.thumbOffset, y = gutterGeometry.trackCrossOffset)
                    }
                val thumbSize =
                    if (isVertical) {
                        Size(width = gutterGeometry.trackThickness, height = metrics.thumbLength)
                    } else {
                        Size(width = metrics.thumbLength, height = gutterGeometry.trackThickness)
                    }
                drawRoundRect(
                    color = thumbColor.copy(alpha = thumbColor.alpha * alpha),
                    topLeft = thumbTopLeft,
                    size = thumbSize,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                )
            }
            .then(
                if (!canScroll) {
                    Modifier
                } else if (orientation == Orientation.Vertical) {
                    Modifier.padding(end = gutterWidth)
                } else {
                    Modifier.padding(bottom = gutterWidth)
                },
            )
    }

/**
 * Lazy-column façade that always carries the same B-Material scrollbar in a dedicated gutter. The compact API
 * intentionally mirrors every call site in the app; callers that need a stable position can pass
 * their own [LazyListState].
 */
@Composable
fun BMaterialLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.vehicleBLazyScrollbar(state),
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}

