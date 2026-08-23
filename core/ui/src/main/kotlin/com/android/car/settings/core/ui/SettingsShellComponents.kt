package com.android.car.settings.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRole
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemSemantics
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemTouchBehavior
import com.b231001.bmaterial.ccp.rotaryfocus.LocalIsInTouchMode
import com.b231001.bmaterial.uicomponents.button.BIconButton
import com.b231001.bmaterial.uicomponents.button.BIconButtonSize
import com.b231001.bmaterial.uicomponents.button.BIconButtonStyle

@Composable
internal fun SettingsHeaderBar(
    title: String,
    @DrawableRes titleIconRes: Int? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    backId: FocusItemId? = null,
    titleTag: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null && backLabel != null && backId != null) {
            RotaryAppBarButton(id = backId, label = backLabel, onClick = onBack)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                titleIconRes?.let { iconRes ->
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        // Vector fills are black; without a tint the header icon disappears on
                        // dark surfaces.
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = title,
                    modifier =
                        Modifier
                            .then(
                                if (titleIconRes == null) Modifier else Modifier.padding(start = 12.dp),
                            ).then(
                                titleTag?.let { Modifier.testTag(it) }
                                    ?: Modifier,
                            ),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
private fun RotaryAppBarButton(
    id: FocusItemId,
    label: String,
    onClick: () -> Unit,
) {
    FocusItem(
        id = id,
        layout = FocusItemLayout(fillCrossAxis = false, minWidth = 76.dp, minHeight = 76.dp),
        onClick = onClick,
        touchBehavior = FocusItemTouchBehavior.ComposeContent,
        semantics = FocusItemSemantics(label = label, role = FocusItemRole.Button),
    ) { state ->
        BIconButton(
            onClick = onClick,
            style = BIconButtonStyle.Text,
            size = BIconButtonSize.Lg,
            modifier = Modifier.size(76.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = label,
                tint =
                    if (state.isFocused && !LocalIsInTouchMode.current) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
    }
}

/** Rotary-aware B-Material action for app bars (refresh, overflow, and similar commands). */
@Composable
fun SettingsAppBarAction(
    id: String,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    FocusItem(
        id = FocusItemId("settings-action-${stableRotaryKey(id)}"),
        isEnabled = enabled,
        layout = FocusItemLayout(width = 76.dp, height = 76.dp, fillCrossAxis = false),
        onClick = onClick,
        touchBehavior = FocusItemTouchBehavior.ComposeContent,
        semantics = FocusItemSemantics(label = contentDescription, role = FocusItemRole.Button),
    ) { state ->
        BIconButton(
            onClick = onClick,
            enabled = enabled,
            style = BIconButtonStyle.Text,
            size = BIconButtonSize.Lg,
            modifier = Modifier.size(76.dp).rotaryFocusBorder(state.isFocused),
            content = icon,
        )
    }
}

private fun stableRotaryKey(value: String): String = value.hashCode().toUInt().toString(16)
