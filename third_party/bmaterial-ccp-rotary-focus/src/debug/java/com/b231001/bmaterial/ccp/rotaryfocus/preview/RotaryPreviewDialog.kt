package com.b231001.bmaterial.ccp.rotaryfocus.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.ccp.rotaryfocus.FocusArea
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusDialog

@Composable
internal fun PreviewRotaryDialog(owner: String, onDismiss: () -> Unit) {
    val confirm = FocusItemId("dialog-confirm")
    val cancel = FocusItemId("dialog-cancel")

    RotaryFocusDialog(
        onDismissRequest = onDismiss,
        dialogKey = "preview-$owner",
        initialFocus = PreviewTargets.dialogDefault(owner)
    ) {
        Surface(
            modifier = Modifier.width(560.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("Native rotary dialog", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Confirm receives default real View focus. Closing with Center or Back " +
                        "restores the exact parent item that opened this window.",
                    style = MaterialTheme.typography.bodyMedium
                )
                FocusArea(
                    id = PreviewTargets.dialogArea(owner),
                    modifier = Modifier.fillMaxWidth(),
                    layout = FocusAreaLayout(itemSpacing = 12.dp),
                    firstFocusAt = confirm,
                    focusOrder = listOf(confirm, cancel),
                    wrapAround = true
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PreviewFocusButton(
                            id = confirm,
                            modifier = Modifier.weight(1f),
                            label = "Confirm",
                            supportingText = "Default focus",
                            onClick = onDismiss
                        )
                        PreviewFocusButton(
                            id = cancel,
                            modifier = Modifier.weight(1f),
                            label = "Cancel",
                            supportingText = "Return to opener",
                            onClick = onDismiss
                        )
                    }
                }
            }
        }
    }
}
