package com.android.car.settings.core.ui

import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId

/** Navigation policy for one native FocusArea participating in a logical pane-local ring. */
internal data class SettingsFocusAreaPolicy(
    val wrapAround: Boolean,
    val previousFocusArea: FocusAreaId?,
    val nextFocusArea: FocusAreaId?,
)

/**
 * Builds one closed rotary ring without introducing links to another pane.
 *
 * A single native area wraps locally. Multiple native areas form a bidirectional cycle in the
 * supplied visual order, allowing an app bar and one or more content areas to behave as one pane.
 */
internal fun settingsFocusAreaRing(areaIds: List<FocusAreaId>): Map<FocusAreaId, SettingsFocusAreaPolicy> {
    require(areaIds.isNotEmpty()) { "A settings focus ring requires at least one area" }
    require(areaIds.distinct().size == areaIds.size) { "A settings focus ring cannot contain duplicate areas" }
    if (areaIds.size == 1) {
        return mapOf(
            areaIds.single() to
                SettingsFocusAreaPolicy(
                    wrapAround = true,
                    previousFocusArea = null,
                    nextFocusArea = null,
                ),
        )
    }
    return buildMap {
        areaIds.forEachIndexed { index, areaId ->
            put(
                areaId,
                SettingsFocusAreaPolicy(
                    wrapAround = false,
                    previousFocusArea = areaIds[(index - 1 + areaIds.size) % areaIds.size],
                    nextFocusArea = areaIds[(index + 1) % areaIds.size],
                ),
            )
        }
    }
}
