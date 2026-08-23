package com.b231001.bmaterial.ccp.rotaryfocus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RotaryFocusOrderTest {
    @Test
    fun forwardBoundaryDoesNotProduceARecursiveFallbackCandidate() {
        val candidates = rotaryTraversalIndices(
            currentIndex = 2,
            itemCount = 3,
            direction = RotaryTraversalDirection.Forward,
            wrapAround = false
        )

        assertEquals(emptyList(), candidates)
    }

    @Test
    fun backwardBoundaryDoesNotProduceARecursiveFallbackCandidate() {
        val candidates = rotaryTraversalIndices(
            currentIndex = 0,
            itemCount = 3,
            direction = RotaryTraversalDirection.Backward,
            wrapAround = false
        )

        assertEquals(emptyList(), candidates)
    }

    @Test
    fun wrapAroundTraversesLastToFirstAndFirstToLast() {
        val forward = rotaryTraversalIndices(
            currentIndex = 2,
            itemCount = 3,
            direction = RotaryTraversalDirection.Forward,
            wrapAround = true
        )
        val backward = rotaryTraversalIndices(
            currentIndex = 0,
            itemCount = 3,
            direction = RotaryTraversalDirection.Backward,
            wrapAround = true
        )

        assertEquals(listOf(0, 1), forward)
        assertEquals(listOf(2, 1), backward)
    }

    @Test
    fun disabledCandidatesCanBeSkippedInTraversalOrder() {
        val focusable = listOf(true, false, true, false)
        val target = rotaryTraversalIndices(
            currentIndex = 0,
            itemCount = focusable.size,
            direction = RotaryTraversalDirection.Forward,
            wrapAround = false
        ).firstOrNull { focusable[it] }

        assertEquals(2, target)
    }

    @Test
    fun oneItemAreaHoldsFocusEvenWhenWrapping() {
        val target = rotaryTraversalIndices(
            currentIndex = 0,
            itemCount = 1,
            direction = RotaryTraversalDirection.Forward,
            wrapAround = true
        ).firstOrNull()

        assertNull(target)
    }

    @Test
    fun explicitOrderPrecedesPhysicalRegistrationOrder() {
        val one = FocusItemId("one")
        val two = FocusItemId("two")
        val three = FocusItemId("three")

        val order = mergeRotaryFocusOrder(
            explicitOrder = listOf(one, three, two),
            liveItems = listOf(one, two, three)
        )

        assertEquals(listOf(one, three, two), order)
    }

    @Test
    fun liveItemsMissingFromExplicitOrderAreAppendedOnce() {
        val one = FocusItemId("one")
        val two = FocusItemId("two")
        val dynamic = FocusItemId("dynamic")

        val order = mergeRotaryFocusOrder(
            explicitOrder = listOf(one, two),
            liveItems = listOf(dynamic, one)
        )

        assertEquals(listOf(one, two, dynamic), order)
    }
}
