/*
 * Compatible implementation of the AOSP Car UI focus-parking contract.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.car.ui

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import com.android.car.ui.utils.RotaryConstants
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusLogger

/** Invisible, real View used to safely park focus in each Android window. */
public open class FocusParkingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {
    private var focusRestorer: (() -> View?)? = null
    private var focusRestorationDeferred: (() -> Boolean)? = null
    private var popupDismissHandler: (() -> Unit)? = null
    private var shouldRestoreFocus: Boolean = true

    init {
        isFocusable = true
        isFocusableInTouchMode = false
        isEnabled = true
        isClickable = false
        alpha = 0f
        defaultFocusHighlightEnabled = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(1, 1)
    }

    override fun getAccessibilityClassName(): CharSequence =
        RotaryConstants.FOCUS_PARKING_VIEW_CLASS_NAME

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        RotaryFocusLogger.debug {
            "Window focus changed; parkingView=$id hasWindowFocus=$hasWindowFocus"
        }
        if (!hasWindowFocus) {
            parkFocus()
        } else if (shouldRestoreFocus && isFocused) {
            restoreFocus()
        }
        super.onWindowFocusChanged(hasWindowFocus)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean =
        when (action) {
            RotaryConstants.ACTION_RESTORE_DEFAULT_FOCUS -> restoreFocus()

            RotaryConstants.ACTION_DISMISS_POPUP_WINDOW -> {
                popupDismissHandler?.invoke()
                popupDismissHandler != null
            }

            RotaryConstants.ACTION_HIDE_IME -> {
                val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
                val hidden = inputMethodManager?.hideSoftInputFromWindow(windowToken, 0) == true
                RotaryFocusLogger.debug {
                    "IME hide requested from parkingView=$id success=$hidden"
                }
                hidden
            }

            AccessibilityNodeInfo.ACTION_FOCUS -> parkFocus()
            else -> super.performAccessibilityAction(action, arguments)
        }

    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        if (shouldRestoreFocus && hasWindowFocus() && restoreFocus()) return true
        return super.requestFocus(direction, previouslyFocusedRect)
    }

    public fun parkFocus(): Boolean {
        val result = super.requestFocus(FOCUS_DOWN, null)
        RotaryFocusLogger.debug { "Focus parked; parkingView=$id success=$result" }
        return result
    }

    public fun setShouldRestoreFocus(shouldRestoreFocus: Boolean) {
        this.shouldRestoreFocus = shouldRestoreFocus
    }

    public fun setFocusRestorer(restorer: (() -> View?)?) {
        focusRestorer = restorer
    }

    /** Keeps focus parked while a requested target is still attaching or being materialized. */
    public fun setFocusRestorationDeferred(deferred: (() -> Boolean)?) {
        focusRestorationDeferred = deferred
    }

    public fun setOnDismissPopupWindow(handler: (() -> Unit)?) {
        popupDismissHandler = handler
    }

    private fun restoreFocus(): Boolean {
        if (focusRestorationDeferred?.invoke() == true) {
            val parked = super.requestFocus(FOCUS_DOWN, null)
            RotaryFocusLogger.debug {
                "Default focus restoration deferred; parkingView=$id parked=$parked"
            }
            return parked
        }
        val target = focusRestorer?.invoke()
        val restored = target != null && target !== this &&
            (
                target.performAccessibilityAction(AccessibilityNodeInfo.ACTION_FOCUS, null) ||
                    target.requestFocus()
                )
        if (restored) {
            RotaryFocusLogger.debug { "Focus restored from parkingView=$id to view=${target?.id}" }
            return true
        }
        val rootFocusables = arrayListOf<View>().also {
            if (rootView !== this) rootView.addFocusables(it, FOCUS_FORWARD, FOCUSABLES_ALL)
        }
        val fallback = rootFocusables.firstOrNull {
            it !== this && it !is FocusParkingView && it.isShown && it.isEnabled && it.isFocusable
        }
        val rootRestored = fallback?.requestFocus() == true
        val parkedAsFallback = !rootRestored && super.requestFocus(FOCUS_DOWN, null)
        RotaryFocusLogger.debug {
            "Default focus restoration; parkingView=$id success=$rootRestored target=${target?.id}"
        }
        return rootRestored || parkedAsFallback
    }
}
