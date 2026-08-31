package com.android.car.settings.core.ui

import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemId
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemLayout
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRole
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemSemantics
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemTouchBehavior
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryDialogWindow
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusDialog
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusTarget
import com.b231001.bmaterial.uicomponents.button.BButtonSize
import com.b231001.bmaterial.uicomponents.card.BCard
import com.b231001.bmaterial.uicomponents.card.BCardSize
import com.b231001.bmaterial.uicomponents.card.BCardStyle

/**
 * Image-led vehicle information shown in a bounded popup instead of replacing the current
 * vehicle-control destination. The native B-Material dialog owns its own rotary host and restores
 * the exact control that opened it when Center/Back dismisses the popup.
 */
@Composable
fun VehicleInfoGuideDialog(
    control: VehicleControlUiModel,
    onDismissRequest: () -> Unit,
) {
    val infoScrollState = rememberScrollState()
    val areaId = FocusAreaId("vehicle-info-dialog-${rotarySafeKey(control.key)}-${control.areaId}")
    val closeId = FocusItemId("${areaId.value}-close")
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(control.key, control.areaId) {
        VehicleDialogBackGuard.armForShownDialog()
        Log.i("MySystemVehicle", "vehicle-info-popup open key=${control.key} area=${control.areaId}")
    }

    val dismiss: () -> Unit = {
        // Dialog windows on some AAOS builds dispatch Back once to the Compose callback and
        // once more while the window is being removed. Make the callback idempotent so the
        // parent NavHost cannot observe a second Back and finish the activity.
        if (!dismissed) {
            dismissed = true
            VehicleDialogBackGuard.armForDismissedDialog()
            Log.i("MySystemVehicle", "vehicle-info-popup dismiss key=${control.key} area=${control.areaId}")
            onDismissRequest()
        }
    }

    @Composable
    fun DialogBody() {
        // Consume Back inside the dialog's own dispatcher. Relying only on the platform dialog
        // onDismiss callback can leak the same hardware event to the parent activity on AAOS API
        // 37, which would close the vehicle destination after dismissing the popup.
        BackHandler(enabled = true, onBack = dismiss)
        Surface(
            modifier = Modifier.widthIn(min = 620.dp, max = 980.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 720.dp)
                        .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 620.dp)
                            .automotiveScrollbar(infoScrollState)
                            .verticalScroll(infoScrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = control.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )

                    BCard(
                        modifier = Modifier.fillMaxWidth(),
                        style = BCardStyle.Elevated,
                        size = BCardSize.Lg,
                        content = {
                            AnimatedContent(
                                targetState = control.illustrationRes,
                                transitionSpec = {
                                    fadeIn(tween(VehicleMotionTokens.CONTENT_ENTER_DURATION_MILLIS)) togetherWith
                                        fadeOut(tween(VehicleMotionTokens.CONTENT_EXIT_DURATION_MILLIS))
                                },
                                label = "vehicle-info-artwork",
                            ) { illustrationRes ->
                                if (illustrationRes != null) {
                                    VehicleIllustrationImage(
                                        illustrationRes = illustrationRes,
                                        contentDescription = control.info,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = MaterialTheme.shapes.extraLarge,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.DirectionsCar,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(96.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )

                    GuideDialogSection(
                        title = control.title,
                        body = control.info,
                    )
                    GuideDialogSection(
                        title = stringResource(R.string.vehicle_control_info_limitations_heading),
                        body = control.limitations,
                    )
                    GuideDialogSection(
                        title = stringResource(R.string.vehicle_control_info_dependencies_heading),
                        body = control.dependencies,
                    )
                    GuideDialogSection(
                        title = stringResource(R.string.vehicle_control_current_status_heading),
                        body = vehicleGuideStatus(control),
                    )
                }

                FocusArea(
                    id = areaId,
                    firstFocusAt = closeId,
                    focusOrder = listOf(closeId),
                    contentPadding = PaddingValues(vertical = SettingsTokens.FocusRingClearance),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FocusItem(
                        id = closeId,
                        onClick = dismiss,
                        touchBehavior = FocusItemTouchBehavior.ComposeContent,
                        layout =
                            FocusItemLayout(
                                minHeight = 60.dp,
                            ),
                        semantics =
                            FocusItemSemantics(
                                label = stringResource(R.string.vehicle_control_info_close_content_description),
                                role = FocusItemRole.Button,
                            ),
                    ) { _ ->
                        AutomotiveButton(
                            label = stringResource(R.string.vehicle_control_info_close),
                            onClick = dismiss,
                            size = BButtonSize.Lg,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                        )
                    }
                }
            }
        }
    }

    if (isRotaryFallbackMode()) {
        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            DialogBody()
        }
    } else {
        RotaryFocusDialog(
            onDismissRequest = dismiss,
            dialogKey = "vehicle-info-${rotarySafeKey(control.key)}-${control.areaId}",
            initialFocus = RotaryFocusTarget(areaId, closeId),
            window =
                RotaryDialogWindow(
                    width = ViewGroup.LayoutParams.WRAP_CONTENT,
                    height = ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            content = { DialogBody() },
        )
    }
}

@Composable
private fun GuideDialogSection(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun rotarySafeKey(value: String): String = value.replace(Regex("[^A-Za-z0-9_-]"), "_")

@Composable
private fun vehicleGuideStatus(control: VehicleControlUiModel): String =
    when {
        !control.supported -> stringResource(R.string.vehicle_control_unavailable)
        control.errorMessage != null -> control.errorMessage
        !control.available -> stringResource(R.string.vehicle_control_unavailable)
        control.valueLabel.isNotBlank() -> control.valueLabel
        control.booleanValue == true -> stringResource(R.string.vehicle_control_on)
        control.booleanValue == false -> stringResource(R.string.vehicle_control_off)
        else -> control.summary
    }
