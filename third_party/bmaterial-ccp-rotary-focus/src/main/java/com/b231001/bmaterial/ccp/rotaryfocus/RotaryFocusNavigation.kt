package com.b231001.bmaterial.ccp.rotaryfocus

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.StyleRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

internal val LocalRotaryFocusDestinationKey: ProvidableCompositionLocal<String?> =
    staticCompositionLocalOf { null }

/**
 * Saves and restores the last real View focus for one navigation destination.
 * Put this around the destination's FocusAreas, not around each individual item.
 */
@Composable
public fun RotaryFocusDestination(
    destinationKey: String,
    fallback: RotaryFocusTarget? = null,
    parkOnDispose: Boolean = true,
    controller: RotaryFocusController = requireRotaryFocusController(),
    content: @Composable () -> Unit
) {
    val isInTouchMode = LocalIsInTouchMode.current
    val currentTouchMode = rememberUpdatedState(isInTouchMode)
    LaunchedEffect(controller, destinationKey, fallback, isInTouchMode) {
        if (isInTouchMode) {
            controller.deferRestoreFocus(destinationKey, fallback)
        } else {
            controller.restoreFocus(destinationKey, fallback)
        }
    }
    DisposableEffect(controller, destinationKey) {
        onDispose {
            controller.saveFocus(destinationKey)
            if (parkOnDispose && !currentTouchMode.value) {
                controller.parkFocusOwnedBy(destinationKey)
            }
        }
    }
    CompositionLocalProvider(LocalRotaryFocusDestinationKey provides destinationKey) {
        content()
    }
}

/** Native Android dialog window configuration used by [RotaryFocusDialog]. */
public data class RotaryDialogWindow(
    val width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    val height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    val gravity: Int = Gravity.CENTER,
    val cancelOnBackPress: Boolean = true,
    val cancelOnClickOutside: Boolean = true
)

/**
 * Dialog backed by an Android [Dialog] and its own [RotaryFocusHostView].
 *
 * This intentionally does not use Compose's Dialog wrapper: every window must have a real
 * FocusParkingView first in the View tree, and focus must be restored to the parent window when
 * the dialog closes.
 */
@Composable
public fun RotaryFocusDialog(
    onDismissRequest: () -> Unit,
    controller: RotaryFocusController = requireRotaryFocusController(),
    dialogKey: String,
    initialFocus: RotaryFocusTarget? = null,
    window: RotaryDialogWindow = RotaryDialogWindow(),
    @StyleRes themeResId: Int = 0,
    content: @Composable () -> Unit
) {
    require(dialogKey.isNotBlank()) { "dialogKey must not be blank" }
    val context = LocalContext.current
    val parentView = LocalView.current
    val parentContext = rememberCompositionContext()
    val currentOnDismiss = rememberUpdatedState(onDismissRequest)
    val currentContent = rememberUpdatedState(content)
    val parentWasInTouchMode = LocalIsInTouchMode.current
    val returnTarget = remember(dialogKey) { controller.currentFocusTarget }
    val hostId = remember(dialogKey) { "dialog:$dialogKey" }
    val dialogAndHost = remember(context, controller, dialogKey, themeResId, parentView) {
        createRotaryDialog(
            context = context,
            controller = controller,
            hostId = hostId,
            themeResId = themeResId,
            parentView = parentView
        )
    }
    val dialog = dialogAndHost.first
    val host = dialogAndHost.second
    val restoreOnce = remember(dialog) { RestoreOnce() }

    host.setParentCompositionContext(parentContext)
    host.setContent {
        // A dialog is a separate View window, not part of the parent's navigation destination.
        // Prevent its FocusItems from overwriting the screen's saved last-focus target.
        CompositionLocalProvider(
            LocalRotaryFocusDestinationKey provides null,
            LocalRotaryFocusArea provides null
        ) {
            currentContent.value.invoke()
        }
    }
    dialog.setCancelable(window.cancelOnBackPress || window.cancelOnClickOutside)
    dialog.setCanceledOnTouchOutside(window.cancelOnClickOutside)
    val restoreParentFocus: () -> Unit = {
        if (
            restoreOnce.markRestored() &&
            !host.observedIsInTouchMode &&
            returnTarget != null
        ) {
            controller.requestFocus(returnTarget)
        }
    }
    dialog.setOnDismissListener {
        restoreParentFocus()
        currentOnDismiss.value.invoke()
    }
    dialog.setOnKeyListener { _, keyCode, _ ->
        keyCode == KeyEvent.KEYCODE_BACK &&
            !controller.isDirectManipulationActive &&
            !window.cancelOnBackPress
    }
    host.setOnDismissPopupWindow {
        if (dialog.isShowing) dialog.dismiss() else currentOnDismiss.value.invoke()
    }

    DisposableEffect(dialog, window, returnTarget) {
        dialog.show()
        dialog.window?.applyWindowLayout(window)
        if (initialFocus != null) {
            host.post { controller.requestFocus(initialFocus) }
        }
        RotaryFocusLogger.debug {
            "Rotary dialog shown; key=$dialogKey host=$hostId returnTarget=$returnTarget " +
                "initialFocus=$initialFocus parentTouchMode=$parentWasInTouchMode"
        }
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.setOnKeyListener(null)
            host.setOnDismissPopupWindow(null)
            if (dialog.isShowing) dialog.dismiss()
            restoreParentFocus()
            RotaryFocusLogger.debug {
                "Rotary dialog disposed; key=$dialogKey returnTarget=$returnTarget"
            }
        }
    }
}

private class RestoreOnce {
    private var restored: Boolean = false

    fun markRestored(): Boolean {
        if (restored) return false
        restored = true
        return true
    }
}

@Composable
private fun requireRotaryFocusController(): RotaryFocusController =
    LocalRotaryFocusController.current
        ?: error("A RotaryFocusController requires RotaryFocusHost or setRotaryContent")

private fun createRotaryDialog(
    context: Context,
    controller: RotaryFocusController,
    hostId: String,
    @StyleRes themeResId: Int,
    parentView: android.view.View
): Pair<Dialog, RotaryFocusHostView> {
    val dialog = if (themeResId == 0) Dialog(context) else Dialog(context, themeResId)
    val host = RotaryFocusHostView(context, controller, hostId)
    dialog.setContentView(host)
    host.setViewTreeLifecycleOwner(parentView.findViewTreeLifecycleOwner())
    host.setViewTreeViewModelStoreOwner(parentView.findViewTreeViewModelStoreOwner())
    host.setViewTreeSavedStateRegistryOwner(parentView.findViewTreeSavedStateRegistryOwner())
    return dialog to host
}

private fun Window.applyWindowLayout(layout: RotaryDialogWindow) {
    setBackgroundDrawableResource(android.R.color.transparent)
    setLayout(layout.width, layout.height)
    setGravity(layout.gravity)
}
