package com.android.car.settings.core.ui

import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusTarget

private const val HEX_RADIX = 16

/** Logical lazy-list focus order plus the physical LazyColumn index used to reveal each item. */
internal data class SettingsLazyFocusSpec(
    val focusOrder: List<FocusItemId>,
    val itemIndexById: Map<FocusItemId, Int>,
)

/** Prevents an initial placeholder row from winning focus before repository data is available. */
internal fun settingsFirstContentItemToFocus(
    isContentFocusReady: Boolean,
    declaredItemId: FocusItemId?,
    discoveredItemId: FocusItemId?,
): FocusItemId? = if (isContentFocusReady) declaredItemId ?: discoveredItemId else null

/**
 * The shell owns initial focus for root categories, beginning at Search. Child destinations restore
 * their first content item unless an explicit one-shot handoff is pending.
 */
internal fun settingsDestinationFallback(
    isRoot: Boolean,
    contentFallback: RotaryFocusTarget?,
    backFallback: RotaryFocusTarget?,
    hasExplicitFocusHandoff: Boolean = false,
): RotaryFocusTarget? =
    if (isRoot || hasExplicitFocusHandoff) {
        null
    } else {
        contentFallback ?: backFallback
    }

/** Stable FocusItem identity shared by row registration and lazy-list reveal requests. */
internal fun settingsFocusItemId(
    destinationKey: String,
    focusId: String,
): FocusItemId {
    require(destinationKey.isNotBlank()) { "destinationKey must not be blank" }
    require(focusId.isNotBlank()) { "focusId must not be blank" }
    return FocusItemId("settings-item-${stableRotaryKey("$destinationKey/$focusId")}")
}

/**
 * Converts feature-level row keys into the scoped IDs understood by B-Material's lazy focus
 * handler. The caller supplies real LazyColumn indices so headers and empty states are skipped.
 */
internal fun settingsLazyFocusSpec(
    destinationKey: String,
    itemIndexByFocusId: Map<String, Int>,
): SettingsLazyFocusSpec {
    val scopedIndexes = linkedMapOf<FocusItemId, Int>()
    itemIndexByFocusId.forEach { (focusId, itemIndex) ->
        require(itemIndex >= 0) { "Lazy focus index must be non-negative for '$focusId'" }
        val itemId = settingsFocusItemId(destinationKey, focusId)
        check(scopedIndexes.put(itemId, itemIndex) == null) {
            "Duplicate scoped lazy focus id '$itemId'"
        }
    }
    return SettingsLazyFocusSpec(
        focusOrder = scopedIndexes.keys.toList(),
        itemIndexById = scopedIndexes,
    )
}

internal fun stableRotaryKey(value: String): String = value.hashCode().toUInt().toString(HEX_RADIX)
