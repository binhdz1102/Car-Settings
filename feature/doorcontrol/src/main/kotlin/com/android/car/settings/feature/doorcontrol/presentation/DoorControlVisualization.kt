package com.android.car.settings.feature.doorcontrol.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.android.car.settings.core.ui.VehicleControlUiModel
import com.android.car.settings.core.ui.VehicleIllustrationImage
import com.android.car.settings.core.ui.VehicleObservationStatus
import com.android.car.settings.core.ui.VehiclePreviewAnchor
import com.android.car.settings.core.ui.VehicleVisualPolicy
import com.android.car.settings.core.ui.drawAmbientBeacon
import com.android.car.settings.core.ui.drawAngleArcGauge
import com.android.car.settings.core.ui.drawDoorSeamGlow
import com.android.car.settings.core.ui.offsetIn
import com.android.car.settings.feature.doorcontrol.R

/**
 * Dedicated vehicle outline driven only by the position/state properties. `DOOR_MOVE` and
 * `WINDOW_MOVE` are commands and are intentionally never used to animate this diagram.
 */
@Composable
internal fun DoorControlVisualization(
    controls: List<VehicleControlUiModel>,
    selectedControl: VehicleControlUiModel?,
    visualPolicy: VehicleVisualPolicy = VehicleVisualPolicy(true, true, true),
    modifier: Modifier = Modifier,
) {
    val areaId = selectedControl?.areaId ?: controls.firstOrNull()?.areaId ?: 0
    val motion =
        remember(controls, areaId) {
            doorVisualMotion(controls.filter { it.areaId == areaId || it.areaId == 0 })
        }
    val doorOpen by animateFloatAsState(motion.doorOpen, doorMotionSpec(visualPolicy), label = "door-position")
    val windowOpen by animateFloatAsState(motion.windowOpen, doorMotionSpec(visualPolicy), label = "window-position")
    val mirrorAngle by animateFloatAsState(
        if (motion.mirrorFolded) 72f else 0f,
        tween(if (visualPolicy.allowPreviewTransition) 360 else 0),
        label = "mirror-fold",
    )
    val lockColor by
        animateColorAsState(
            if (motion.locked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
            tween(if (visualPolicy.allowPreviewTransition) 220 else 0),
            label = "door-lock",
        )
    val accent = MaterialTheme.colorScheme.primary
    val visualizationDescription = stringResource(R.string.door_visualization_content_description)

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .semantics {
                    contentDescription = visualizationDescription
                },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box {
            VehicleIllustrationImage(
                illustrationRes = R.drawable.vehicle_preview_doors,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val isRearArea = (areaId and 0x00000050) != 0
                val isTailgate = (areaId and 0x00100000) != 0

                // 1. Door Ajar / Seam Glow: Traces the authentic trailing latch shut-line
                if (doorOpen > 0.01f) {
                    val seamPath =
                        Path().apply {
                            if (isTailgate) {
                                moveTo(size.width * .065f, size.height * .320f)
                                lineTo(size.width * .075f, size.height * .520f)
                            } else if (isRearArea) {
                                moveTo(size.width * .170f, size.height * .230f)
                                lineTo(size.width * .165f, size.height * .680f)
                            } else {
                                // Front door B-pillar latch shut-line
                                moveTo(size.width * .354f, size.height * .205f)
                                lineTo(size.width * .358f, size.height * .690f)
                            }
                        }
                    drawDoorSeamGlow(
                        seamPath = seamPath,
                        color = accent,
                        openRatio = doorOpen,
                    )

                    // Ambient ground puddle / entry wash illumination under the open door
                    val puddleCenter =
                        if (isRearArea) {
                            Offset(size.width * .260f, size.height * .715f)
                        } else {
                            Offset(size.width * .470f, size.height * .720f)
                        }
                    drawCircle(
                        color = accent.copy(alpha = 0.20f * doorOpen),
                        radius = size.minDimension * 0.065f,
                        center = puddleCenter,
                    )
                }

                // 2. Side Mirror Fold Indicator at actual mirror position
                val mirror = VehiclePreviewAnchor(.495f, .379f).offsetIn(size)
                if (motion.mirrorFolded) {
                    drawAngleArcGauge(
                        center = mirror,
                        radius = size.minDimension * .035f,
                        startAngle = 180f,
                        sweepAngle = mirrorAngle,
                        currentProgress = 1f,
                        color = accent,
                    )
                } else {
                    drawAmbientBeacon(
                        center = mirror,
                        radius = size.minDimension * .012f,
                        color = accent.copy(alpha = .7f),
                    )
                }

                // 3. Door Handle Lock Beacon placed precisely on the selected door's handle
                val handleAnchor =
                    when {
                        isTailgate -> VehiclePreviewAnchor(.085f, .480f)
                        isRearArea -> VehiclePreviewAnchor(.197f, .413f)
                        else -> VehiclePreviewAnchor(.394f, .445f)
                    }
                drawAmbientBeacon(
                    center = handleAnchor.offsetIn(size),
                    radius = size.minDimension * .015f,
                    color = lockColor,
                    pulse = if (motion.locked) .75f else 1f,
                )
            }
        }
    }
}

private fun doorMotionSpec(policy: VehicleVisualPolicy) =
    tween<Float>(durationMillis = if (policy.allowPreviewTransition) 420 else 0, easing = FastOutSlowInEasing)

internal data class DoorVisualMotion(
    val doorOpen: Float = 0f,
    val windowOpen: Float = 0f,
    val mirrorFolded: Boolean = false,
    val locked: Boolean = false,
)

/** Pure transform targets sourced from actual DOOR/WINDOW position and state properties. */
internal fun doorVisualMotion(controls: List<VehicleControlUiModel>): DoorVisualMotion {
    val byKey =
        buildMap {
            controls.forEach { control ->
                if (!containsKey(control.key)) put(control.key, control)
            }
        }

    fun level(key: String): Float {
        val control = byKey[key]
        return when {
            control == null -> 0f
            control.observedSnapshot.status != VehicleObservationStatus.CONFIRMED ||
                control.observedSnapshot.numericValue == null -> 0f
            else -> {
                val value = requireNotNull(control.observedSnapshot.numericValue)
                val span = control.range.endInclusive - control.range.start
                if (span.isFinite() && span > 0f) {
                    ((value - control.range.start) / span).coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
        }
    }
    return DoorVisualMotion(
        doorOpen = level("DOOR_POSITION"),
        windowOpen = level("WINDOW_POSITION"),
        mirrorFolded =
            byKey["MIRROR_FOLD"]?.let {
                it.observedSnapshot.status == VehicleObservationStatus.CONFIRMED &&
                    it.observedSnapshot.booleanValue == true
            } == true,
        locked =
            byKey["DOOR_LOCK"]?.let {
                it.observedSnapshot.status == VehicleObservationStatus.CONFIRMED &&
                    it.observedSnapshot.booleanValue == true
            } == true,
    )
}

internal fun interpolateDoorMotion(
    from: DoorVisualMotion,
    to: DoorVisualMotion,
    fraction: Float,
): DoorVisualMotion {
    val t = fraction.coerceIn(0f, 1f)

    fun lerp(
        start: Float,
        end: Float,
    ) = start + (end - start) * t
    return DoorVisualMotion(
        doorOpen = lerp(from.doorOpen, to.doorOpen),
        windowOpen = lerp(from.windowOpen, to.windowOpen),
        // Discrete state follows the latest target once the transition reaches its endpoint.
        mirrorFolded = if (t < 1f) from.mirrorFolded else to.mirrorFolded,
        locked = if (t < 1f) from.locked else to.locked,
    )
}
