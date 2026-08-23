/*
 * Protocol based on the Android Open Source Project Car UI Library.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.car.ui.utils

import android.content.Context
import android.os.Build
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusLogger

/** Sends the AAOS accessibility protocol used to enter or exit direct-manipulation mode. */
public object DirectManipulationHelper {
    @Suppress("DEPRECATION")
    public fun enableDirectManipulationMode(view: View, enable: Boolean): Boolean {
        val manager = view.context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? AccessibilityManager
        if (manager?.isEnabled != true) {
            RotaryFocusLogger.warning {
                "DM event not sent: accessibility is disabled; " +
                    "view=${view.debugName()} enable=$enable"
            }
            return false
        }

        val event = AccessibilityEvent.obtain().apply {
            className = RotaryConstants.DIRECT_MANIPULATION_CLASS_NAME
            setSource(view)
            eventType = if (enable) {
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
            } else {
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
            }
        }
        manager.sendAccessibilityEvent(event)
        RotaryFocusLogger.debug {
            "DM accessibility event sent; view=${view.debugName()} enable=$enable"
        }
        return true
    }

    public fun isDirectManipulation(event: AccessibilityEvent): Boolean =
        event.className?.toString() == RotaryConstants.DIRECT_MANIPULATION_CLASS_NAME

    public fun supportsDirectManipulation(node: AccessibilityNodeInfo): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            (
                node.contentDescription?.toString() ==
                    RotaryConstants.DIRECT_MANIPULATION_CLASS_NAME ||
                    node.stateDescription?.toString() ==
                    RotaryConstants.DIRECT_MANIPULATION_CLASS_NAME
                )

    /** Enables the simple RotaryService-managed DM mechanism on Android 11 and newer. */
    public fun setSupportsRotateDirectly(view: View, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val marker = if (enabled) {
                RotaryConstants.DIRECT_MANIPULATION_CLASS_NAME
            } else {
                null
            }
            // Current RotaryService reads contentDescription; early Android 11 Car builds read
            // stateDescription. Simple DM reserves both fields. Advanced DM does not.
            view.contentDescription = marker
            view.stateDescription = marker
        }
        RotaryFocusLogger.debug {
            "Simple DM support updated; view=${view.debugName()} enabled=$enabled"
        }
    }

    private fun View.debugName(): String =
        "${javaClass.simpleName}(id=$id, focused=$isFocused, attached=$isAttachedToWindow)"
}
