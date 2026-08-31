package com.android.car.settings.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.ccp.rotaryfocus.FocusArea
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRole
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemSemantics
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryDialogWindow
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusDialog
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusTarget

/** Native-window card dialog with a dedicated FocusArea and opener focus restoration. */
@Composable
fun SettingsCardDialog(
    dialogKey: String,
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    confirmLabel: String = "OK",
) {
    if (isRotaryFallbackMode()) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                AutomotiveDialogActionVisual {
                    TextButton(onClick = onDismissRequest) {
                        Text(confirmLabel, style = MaterialTheme.typography.titleMedium)
                    }
                }
            },
        )
        return
    }

    val areaId = FocusAreaId("settings-dialog-$dialogKey")
    val backId = FocusItemId("settings-dialog-back-$dialogKey")
    val density = LocalDensity.current
    RotaryFocusDialog(
        onDismissRequest = onDismissRequest,
        dialogKey = "settings-card-dialog-$dialogKey",
        initialFocus = RotaryFocusTarget(areaId, backId),
        window = RotaryDialogWindow(width = with(density) { 760.dp.roundToPx() }, cancelOnBackPress = true, cancelOnClickOutside = true),
    ) {
        SettingsCardSurface(
            modifier = Modifier.widthIn(min = 560.dp, max = 760.dp),
        ) {
            Column(modifier = Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FocusArea(
                    id = areaId,
                    modifier = Modifier.fillMaxWidth(),
                    firstFocusAt = backId,
                    focusOrder = listOf(backId),
                    wrapAround = true,
                ) {
                    FocusItem(
                        id = backId,
                        modifier = Modifier.width(SettingsTokens.DialogActionMinWidth),
                        onClick = onDismissRequest,
                        semantics = FocusItemSemantics(label = confirmLabel, role = FocusItemRole.Button),
                        layout =
                            FocusItemLayout(
                                fillCrossAxis = false,
                                minWidth = SettingsTokens.DialogActionMinWidth,
                                minHeight = SettingsTokens.DialogActionMinHeight,
                            ),
                    ) { _ ->
                        SettingsCardSurface(
                            modifier = Modifier.fillMaxWidth().height(SettingsTokens.DialogActionMinHeight),
                        ) {
                            Row(
                                Modifier.padding(
                                    horizontal = SettingsTokens.CardHorizontalPadding,
                                    vertical = SettingsTokens.CardSingleLineVerticalPadding,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = null,
                                    tint = settingsPrimaryColor(),
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    confirmLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
