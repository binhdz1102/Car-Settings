/*
 * Compatible implementation of the AOSP Car UI focus-area contract.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.car.ui

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.ViewParent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemView
import com.android.car.ui.utils.RotaryConstants

/**
 * A real Android [LinearLayout] recognized as `com.android.car.ui.FocusArea` by RotaryService.
 *
 * The implementation intentionally has no resource/theme dependency so the Compose bridge can be
 * published as a standalone library. It implements the accessibility and default-focus behavior
 * required by AAOS; OEM focus visuals remain owned by the app/theme.
 */
public open class FocusArea @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    private var defaultFocusView: View? = null

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
    }

    override fun getAccessibilityClassName(): CharSequence =
        RotaryConstants.FOCUS_AREA_CLASS_NAME

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_FOCUS) {
            return requestConfiguredDescendantFocus()
        }
        return super.performAccessibilityAction(action, arguments)
    }

    override fun onRequestFocusInDescendants(
        direction: Int,
        previouslyFocusedRect: Rect?
    ): Boolean = requestConfiguredDescendantFocus() ||
        super.onRequestFocusInDescendants(direction, previouslyFocusedRect)

    override fun restoreDefaultFocus(): Boolean =
        requestConfiguredDescendantFocus() || super.restoreDefaultFocus()

    /** Equivalent to Car UI Library's programmatic `app:defaultFocus`. */
    public open fun setDefaultFocus(view: View?) {
        require(view == null || isDescendant(view)) {
            "The default focus View must be a descendant of this FocusArea"
        }
        defaultFocusView = view
    }

    public open fun getDefaultFocusView(): View? = defaultFocusView

    protected fun requestConfiguredDescendantFocus(): Boolean {
        val target = defaultFocusView?.takeIf { it.canTakeRotaryFocus() }
            ?: descendantsDepthFirst().firstOrNull { it.canTakeRotaryFocus() }
            ?: return false
        return target.performAccessibilityAction(AccessibilityNodeInfo.ACTION_FOCUS, null) ||
            (target as? FocusItemView)?.requestRotaryFocus() == true ||
            target.requestFocus()
    }

    private fun View.canTakeRotaryFocus(): Boolean =
        isFocusable && isEnabled && visibility == VISIBLE && width > 0 && height > 0

    private fun isDescendant(candidate: View): Boolean {
        var ancestor: ViewParent? = candidate.parent
        while (ancestor != null) {
            if (ancestor === this) return true
            ancestor = ancestor.parent
        }
        return false
    }

    private fun descendantsDepthFirst(): Sequence<View> = sequence {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            yield(child)
            if (child is android.view.ViewGroup) {
                yieldAll(child.descendantsDepthFirst())
            }
        }
    }

    private fun android.view.ViewGroup.descendantsDepthFirst(): Sequence<View> = sequence {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            yield(child)
            if (child is android.view.ViewGroup) yieldAll(child.descendantsDepthFirst())
        }
    }
}
