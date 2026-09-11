package com.android.car.settings.core.ui

import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.b231001.bmaterial.ccp.rotaryfocus.FocusAreaLayout
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
    visualPolicy: VehicleVisualPolicy,
    guideVisualization: (@Composable (VehicleControlUiModel, Float) -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    val infoScrollState = rememberScrollState()
    val areaId = FocusAreaId("vehicle-info-dialog-${rotarySafeKey(control.key)}-${control.areaId}")
    val closeId = FocusItemId("${areaId.value}-close")
    val guideActionId = FocusItemId("${areaId.value}-guide-action")
    val guideAvailable =
        visualPolicy.allowGuide &&
            guideVisualization != null &&
            control.visualBinding.guideSceneId != null
    var playback by remember(control.key, control.areaId) { mutableStateOf(VehicleGuidePlaybackState()) }
    val playbackProgress = remember(control.key, control.areaId) { Animatable(0f) }
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

    LaunchedEffect(playback.phase, visualPolicy.allowGuidePlayback, guideAvailable) {
        if (!guideAvailable || !visualPolicy.allowGuidePlayback) {
            playback = VehicleGuidePlaybackState()
            playbackProgress.snapTo(0f)
            return@LaunchedEffect
        }
        if (playback.phase == VehicleGuidePhase.PLAYING) {
            playbackProgress.snapTo(0f)
            playbackProgress.animateTo(1f, tween(VEHICLE_GUIDE_DURATION_MILLIS))
            playback =
                reduceVehicleGuidePlayback(
                    playback,
                    VehicleGuideIntent.Complete,
                    visualPolicy,
                )
        } else {
            playbackProgress.snapTo(playback.progress)
        }
    }

    @Composable
    fun DialogBody() {
        // Consume Back inside the dialog's own dispatcher. Relying only on the platform dialog
        // onDismiss callback can leak the same hardware event to the parent activity on AAOS API
        // 37, which would close the vehicle destination after dismissing the popup.
        BackHandler(enabled = true, onBack = dismiss)

        @Composable
        fun StaticArtwork() {
            if (control.illustrationRes != null) {
                VehicleIllustrationImage(
                    illustrationRes = control.illustrationRes,
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
                        .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .automotiveScrollbar(
                                scrollState = infoScrollState,
                                style = AutomotiveDialogScrollbarStyle,
                                gutterWidth = AutomotiveDialogScrollbarGutterWidth,
                            )
                            .verticalScroll(infoScrollState)
                            .padding(horizontal = 24.dp),
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
                            if (
                                guideAvailable &&
                                (
                                    playback.phase == VehicleGuidePhase.PLAYING ||
                                        playback.phase == VehicleGuidePhase.COMPLETE
                                )
                            ) {
                                requireNotNull(guideVisualization)(control, playbackProgress.value)
                            } else {
                                StaticArtwork()
                            }
                        },
                    )

                    Text(
                        text = stringResource(R.string.vehicle_control_info_illustration_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        body = vehicleObservedStatusLabel(control),
                    )
                }

                FocusArea(
                    id = areaId,
                    layout =
                        FocusAreaLayout(
                            itemSpacing = SettingsTokens.DialogActionGap,
                            fillMainAxis = false,
                        ),
                    firstFocusAt = if (guideAvailable) guideActionId else closeId,
                    focusOrder =
                        buildList {
                            if (guideAvailable) add(guideActionId)
                            add(closeId)
                        },
                    contentPadding = PaddingValues(vertical = SettingsTokens.FocusRingClearance),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    if (guideAvailable) {
                        FocusItem(
                            id = guideActionId,
                            onClick = {
                                val intent =
                                    if (playback.phase == VehicleGuidePhase.PLAYING) {
                                        VehicleGuideIntent.Stop
                                    } else if (playback.phase == VehicleGuidePhase.COMPLETE) {
                                        VehicleGuideIntent.Replay
                                    } else {
                                        VehicleGuideIntent.Play
                                    }
                                playback = reduceVehicleGuidePlayback(playback, intent, visualPolicy)
                            },
                            touchBehavior = FocusItemTouchBehavior.ComposeContent,
                            layout = FocusItemLayout(minHeight = SettingsTokens.DialogActionMinHeight),
                            semantics =
                                FocusItemSemantics(
                                    label =
                                        stringResource(
                                            when (playback.phase) {
                                                VehicleGuidePhase.PLAYING -> R.string.vehicle_control_info_stop
                                                VehicleGuidePhase.COMPLETE -> R.string.vehicle_control_info_replay
                                                else -> R.string.vehicle_control_info_play
                                            },
                                        ),
                                    role = FocusItemRole.Button,
                                ),
                        ) { _ ->
                            AutomotiveButton(
                                label =
                                    stringResource(
                                        when (playback.phase) {
                                            VehicleGuidePhase.PLAYING -> R.string.vehicle_control_info_stop
                                            VehicleGuidePhase.COMPLETE -> R.string.vehicle_control_info_replay
                                            else -> R.string.vehicle_control_info_play
                                        },
                                    ),
                                onClick = {
                                    val intent =
                                        if (playback.phase == VehicleGuidePhase.PLAYING) {
                                            VehicleGuideIntent.Stop
                                        } else if (playback.phase == VehicleGuidePhase.COMPLETE) {
                                            VehicleGuideIntent.Replay
                                        } else {
                                            VehicleGuideIntent.Play
                                        }
                                    playback = reduceVehicleGuidePlayback(playback, intent, visualPolicy)
                                },
                                size = BButtonSize.Lg,
                                modifier = Modifier.fillMaxWidth().height(SettingsTokens.DialogActionMinHeight),
                            )
                        }
                    }
                    FocusItem(
                        id = closeId,
                        onClick = dismiss,
                        touchBehavior = FocusItemTouchBehavior.ComposeContent,
                        layout =
                            FocusItemLayout(
                                minHeight = SettingsTokens.DialogActionMinHeight,
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
                            modifier = Modifier.fillMaxWidth().height(SettingsTokens.DialogActionMinHeight),
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
            initialFocus = RotaryFocusTarget(areaId, if (guideAvailable) guideActionId else closeId),
            window =
                RotaryDialogWindow(
                    width = ViewGroup.LayoutParams.WRAP_CONTENT,
                    height = ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            content = { DialogBody() },
        )
    }
}

private const val VEHICLE_GUIDE_DURATION_MILLIS = 4_000

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
