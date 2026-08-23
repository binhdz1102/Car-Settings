/*
 * Copyright 2026 B-Material contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.car.ui.utils

/** Accessibility protocol constants understood by the AAOS RotaryService. */
public object RotaryConstants {
    public const val FOCUS_AREA_CLASS_NAME: String = "com.android.car.ui.FocusArea"
    public const val FOCUS_PARKING_VIEW_CLASS_NAME: String = "com.android.car.ui.FocusParkingView"
    public const val DIRECT_MANIPULATION_CLASS_NAME: String =
        "com.android.car.ui.utils.DIRECT_MANIPULATION"

    public const val ACTION_NUDGE_SHORTCUT: Int = 0x01000000
    public const val ACTION_NUDGE_TO_ANOTHER_FOCUS_AREA: Int = 0x02000000
    public const val ACTION_RESTORE_DEFAULT_FOCUS: Int = 0x04000000
    public const val ACTION_HIDE_IME: Int = 0x08000000
    public const val ACTION_DISMISS_POPUP_WINDOW: Int = 0x10000000

    public const val ROTARY_HORIZONTALLY_SCROLLABLE: String =
        "com.android.car.ui.utils.HORIZONTALLY_SCROLLABLE"
    public const val ROTARY_VERTICALLY_SCROLLABLE: String =
        "com.android.car.ui.utils.VERTICALLY_SCROLLABLE"
    public const val LEGACY_ROTARY_HORIZONTALLY_SCROLLABLE: String =
        "android.rotary.HORIZONTALLY_SCROLLABLE"
    public const val LEGACY_ROTARY_VERTICALLY_SCROLLABLE: String =
        "android.rotary.VERTICALLY_SCROLLABLE"
}
