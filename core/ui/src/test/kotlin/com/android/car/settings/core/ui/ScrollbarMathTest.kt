package com.android.car.settings.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollbarMathTest {

    @Test
    fun scrollbarGutter_centersTrackOutsideContentBounds() {
        val geometry = computeScrollbarGutterGeometry(
            crossAxisSize = 500f,
            thicknessPx = 12f,
            gutterWidthPx = 48f,
        )

        assertNotNull(geometry)
        assertEquals(452f, geometry!!.gutterStart, 0.001f)
        assertEquals(500f, geometry.gutterEnd, 0.001f)
        assertEquals(470f, geometry.trackCrossOffset, 0.001f)
        assertEquals(12f, geometry.trackThickness, 0.001f)
        assertTrue(geometry.trackCrossOffset >= geometry.gutterStart)
        assertTrue(geometry.trackCrossOffset + geometry.trackThickness <= geometry.gutterEnd)
    }

    @Test
    fun scrollbarGutter_clampsToSmallContainer() {
        val geometry = computeScrollbarGutterGeometry(
            crossAxisSize = 24f,
            thicknessPx = 32f,
            gutterWidthPx = 48f,
        )

        assertNotNull(geometry)
        assertEquals(0f, geometry!!.gutterStart, 0.001f)
        assertEquals(0f, geometry.trackCrossOffset, 0.001f)
        assertEquals(24f, geometry.trackThickness, 0.001f)
    }

    @Test
    fun lazyListScrollbar_atTop_hasProgressZeroAndThumbAtTop() {
        val metrics = computeLazyListScrollbarMetrics(
            viewportSize = 800f,
            totalItemsCount = 14,
            visibleItemSizes = List(7) { 100 },
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
            canScrollBackward = false,
            canScrollForward = true,
            paddingPx = 2f,
            minThumbLengthPx = 64f,
        )
        assertNotNull(metrics)
        assertEquals(0f, metrics!!.progress, 0.001f)
        assertEquals(2f, metrics.thumbOffset, 0.001f)
    }

    @Test
    fun lazyListScrollbar_atBottom_hasProgressOneAndThumbAtBottom() {
        val viewportSize = 800f
        val paddingPx = 2f
        val metrics = computeLazyListScrollbarMetrics(
            viewportSize = viewportSize,
            totalItemsCount = 14,
            visibleItemSizes = List(7) { 100 },
            firstVisibleItemIndex = 7,
            firstVisibleItemScrollOffset = 0,
            canScrollBackward = true,
            canScrollForward = false,
            paddingPx = paddingPx,
            minThumbLengthPx = 64f,
        )
        assertNotNull(metrics)
        assertEquals(1f, metrics!!.progress, 0.001f)
        // thumb bottom edge must match viewportSize - paddingPx
        val thumbBottom = metrics.thumbOffset + metrics.thumbLength
        val expectedBottom = viewportSize - paddingPx
        assertEquals(expectedBottom, thumbBottom, 0.001f)
    }

    @Test
    fun lazyListScrollbar_nonScrollable_returnsNull() {
        val metrics = computeLazyListScrollbarMetrics(
            viewportSize = 800f,
            totalItemsCount = 3,
            visibleItemSizes = List(3) { 100 },
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
            canScrollBackward = false,
            canScrollForward = false,
            paddingPx = 2f,
            minThumbLengthPx = 64f,
        )
        assertNull(metrics)
    }

    @Test
    fun scrollStateScrollbar_atTop_hasProgressZero() {
        val metrics = computeScrollStateScrollbarMetrics(
            viewportSize = 800f,
            scrollValue = 0,
            maxScrollValue = 600,
            canScrollBackward = false,
            canScrollForward = true,
            paddingPx = 2f,
            minThumbLengthPx = 64f,
        )
        assertNotNull(metrics)
        assertEquals(0f, metrics!!.progress, 0.001f)
        assertEquals(2f, metrics.thumbOffset, 0.001f)
    }

    @Test
    fun scrollStateScrollbar_atBottom_hasProgressOneAndThumbAtBottom() {
        val viewportSize = 800f
        val paddingPx = 2f
        val metrics = computeScrollStateScrollbarMetrics(
            viewportSize = viewportSize,
            scrollValue = 600,
            maxScrollValue = 600,
            canScrollBackward = true,
            canScrollForward = false,
            paddingPx = paddingPx,
            minThumbLengthPx = 64f,
        )
        assertNotNull(metrics)
        assertEquals(1f, metrics!!.progress, 0.001f)
        val thumbBottom = metrics.thumbOffset + metrics.thumbLength
        val expectedBottom = viewportSize - paddingPx
        assertEquals(expectedBottom, thumbBottom, 0.001f)
    }

    @Test
    fun scrollStateScrollbar_nonScrollable_returnsNull() {
        val metrics = computeScrollStateScrollbarMetrics(
            viewportSize = 800f,
            scrollValue = 0,
            maxScrollValue = 0,
            canScrollBackward = false,
            canScrollForward = false,
            paddingPx = 2f,
            minThumbLengthPx = 64f,
        )
        assertNull(metrics)
    }
}
