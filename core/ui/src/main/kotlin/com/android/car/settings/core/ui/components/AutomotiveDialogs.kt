package com.android.car.settings.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRole
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemSemantics
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemTouchBehavior
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryDialogWindow
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusDialog
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusTarget
import com.b231001.bmaterial.uicomponents.dialog.BAlertDialog
import com.b231001.bmaterial.uicomponents.dialog.BDialogDefaults

/**
 * B-Material alert dialog with a real CCP dialog host when one is available.
 *
 * Hostless Compose tests and OEM builds use BAlertDialog directly.  Automotive builds get a
 * separate RotaryFocusDialog window and explicit FocusItems for Cancel/Confirm so Back restores
 * the opener and Center never lands on a non-focusable Material dialog action.
 */
@Composable
fun AutomotiveAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    /** Stable identity for the separate rotary dialog window; keep it constant across recompositions. */
    dialogKey: String = "automotive-alert",
    dismissButton: (@Composable RowScope.() -> Unit)? = null,
    /** Action invoked by CCP Center on the confirm FocusItem. */
    confirmAction: (() -> Unit)? = null,
    /** Mirrors the enabled state of the visual confirm button for rotary/accessibility input. */
    confirmEnabled: Boolean = true,
    /** Action invoked by CCP Center on the dismiss FocusItem. */
    dismissAction: (() -> Unit)? = null,
    /** Mirrors the enabled state of the visual dismiss button for rotary/accessibility input. */
    dismissEnabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    if (isRotaryFallbackMode()) {
        BAlertDialog(
            onDismissRequest = onDismissRequest,
            // BAlertDialog owns its own action row.  Give each slot the same explicit bounds so
            // a TextButton (Cancel) cannot collapse beside a filled Button (Save/Clear).
            confirmButton = {
                val rowScope = this
                AutomotiveDialogActionVisual { confirmButton.invoke(rowScope) }
            },
            dismissButton =
                dismissButton?.let { button ->
                    {
                        val rowScope = this
                        AutomotiveDialogActionVisual { button.invoke(rowScope) }
                    }
                },
            icon = icon,
            title = title,
            text = text,
            modifier = modifier,
            properties = properties,
        )
        return
    }

    val key = dialogKey
    val areaId = FocusAreaId("automotive-dialog-area-$key")
    val dismissId = FocusItemId("automotive-dialog-dismiss-$key")
    val confirmId = FocusItemId("automotive-dialog-confirm-$key")
    val firstId = if (dismissButton != null) dismissId else confirmId
    RotaryFocusDialog(
        onDismissRequest = onDismissRequest,
        dialogKey = "automotive-dialog-$key",
        initialFocus = RotaryFocusTarget(areaId, firstId),
        window = RotaryDialogWindow(cancelOnBackPress = true, cancelOnClickOutside = true),
    ) {
        val colors = BDialogDefaults.colors()
        val metrics = BDialogDefaults.metrics()
        Surface(
            modifier = modifier.widthIn(min = metrics.minWidth, max = metrics.maxWidth),
            shape = metrics.shape,
            color = colors.container,
            contentColor = colors.content,
            tonalElevation = metrics.elevation,
            shadowElevation = metrics.elevation,
        ) {
            FocusArea(
                id = areaId,
                layout =
                    FocusAreaLayout(
                        itemSpacing = SettingsTokens.DialogActionGap,
                        fillMainAxis = false,
                    ),
                focusOrder =
                    buildList {
                        if (dismissButton != null) add(dismissId)
                        add(confirmId)
                    },
                content = {
                    Column(modifier = Modifier.padding(metrics.contentPadding)) {
                        if (icon != null) {
                            CompositionLocalProvider(LocalContentColor provides colors.icon) { icon() }
                            Spacer(Modifier.height(metrics.titleContentGap))
                        }
                        if (title != null) {
                            CompositionLocalProvider(LocalContentColor provides colors.title) {
                                ProvideTextStyle(MaterialTheme.typography.headlineMedium) { title() }
                            }
                        }
                        if (title != null && text != null) Spacer(Modifier.height(metrics.titleContentGap))
                        if (text != null) {
                            ProvideTextStyle(MaterialTheme.typography.bodyLarge) { text() }
                        }
                        Spacer(Modifier.height(metrics.contentActionsGap))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(metrics.actionGap, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val rowScope = this
                            if (dismissButton != null) {
                                AutomotiveDialogAction(
                                    id = dismissId,
                                    label = "Cancel",
                                    enabled = dismissEnabled,
                                    onClick = dismissAction ?: onDismissRequest,
                                ) {
                                    dismissButton.invoke(rowScope)
                                }
                            }
                            AutomotiveDialogAction(
                                id = confirmId,
                                label = "Confirm",
                                enabled = confirmEnabled,
                                onClick = confirmAction ?: onDismissRequest,
                            ) {
                                confirmButton.invoke(rowScope)
                            }
                        }
                    }
                },
            )
        }
    }
}

/**
 * Gives touch and rotary dialog actions one shared visual hit target.  propagateMinConstraints
 * is important here: it makes the existing Material Button/TextButton content honour the same
 * width and height without requiring every feature dialog to be rewritten at once.
 */
@Composable
internal fun AutomotiveDialogActionVisual(content: @Composable () -> Unit) {
    ProvideTextStyle(MaterialTheme.typography.titleMedium) {
        Box(
            modifier =
                Modifier
                    .widthIn(min = SettingsTokens.DialogActionMinWidth)
                    .heightIn(min = SettingsTokens.DialogActionMinHeight),
            contentAlignment = Alignment.Center,
            propagateMinConstraints = true,
            content = { content() },
        )
    }
}

@Composable
private fun AutomotiveDialogAction(
    id: FocusItemId,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    FocusItem(
        id = id,
        layout =
            FocusItemLayout(
                fillCrossAxis = false,
                minWidth = SettingsTokens.DialogActionMinWidth,
                minHeight = SettingsTokens.DialogActionMinHeight,
            ),
        touchBehavior = FocusItemTouchBehavior.ComposeContent,
        isEnabled = enabled,
        onClick = if (enabled) onClick else null,
        semantics = FocusItemSemantics(label = label, role = FocusItemRole.Button),
        content = { AutomotiveDialogActionVisual(content) },
    )
}
