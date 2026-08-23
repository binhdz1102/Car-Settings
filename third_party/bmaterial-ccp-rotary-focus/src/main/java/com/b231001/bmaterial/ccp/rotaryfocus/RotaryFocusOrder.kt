package com.b231001.bmaterial.ccp.rotaryfocus

internal enum class RotaryTraversalDirection {
    Forward,
    Backward
}

/** Returns declaration-order candidates without ever returning [currentIndex]. */
internal fun rotaryTraversalIndices(
    currentIndex: Int,
    itemCount: Int,
    direction: RotaryTraversalDirection,
    wrapAround: Boolean
): List<Int> {
    if (itemCount <= 1 || currentIndex !in 0 until itemCount) return emptyList()
    return when (direction) {
        RotaryTraversalDirection.Forward -> buildList {
            addAll((currentIndex + 1) until itemCount)
            if (wrapAround) addAll(0 until currentIndex)
        }

        RotaryTraversalDirection.Backward -> buildList {
            addAll((currentIndex - 1) downTo 0)
            if (wrapAround) addAll((itemCount - 1) downTo (currentIndex + 1))
        }
    }
}

/** Keeps the explicit logical order, then appends live items not explicitly listed. */
internal fun mergeRotaryFocusOrder(
    explicitOrder: List<FocusItemId>,
    liveItems: Collection<FocusItemId>
): List<FocusItemId> = buildList {
    val seen = hashSetOf<FocusItemId>()
    explicitOrder.forEach { if (seen.add(it)) add(it) }
    liveItems.forEach { if (seen.add(it)) add(it) }
}
