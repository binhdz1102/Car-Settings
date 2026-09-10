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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.android.car.settings.core.ui.VehicleControlUiModel
import com.android.car.settings.core.ui.VehicleIllustrationImage
import com.android.car.settings.core.ui.VehicleObservationStatus
import com.android.car.settings.core.ui.VehiclePreviewAnchor
import com.android.car.settings.core.ui.VehicleVisualPolicy
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
    val mirrorAngle by animateFloatAsState(if (motion.mirrorFolded) 72f else 0f, tween(if (visualPolicy.allowPreviewTransition) 360 else 0), label = "mirror-fold")
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
                val stroke = Stroke(width = size.minDimension * .017f)
                val hinge = VehiclePreviewAnchor(.62f, .36f).offsetIn(size)
                val doorTopLeft = VehiclePreviewAnchor(.42f, .29f).offsetIn(size)
                val doorSize = Size(size.width * .205f, size.height * .37f)
                rotate(degrees = doorOpen * 18f, pivot = hinge) {
                    drawRoundRect(
                        color = accent.copy(alpha = .88f),
                        topLeft = doorTopLeft,
                        size = doorSize,
                        cornerRadius = CornerRadius(size.minDimension * .025f),
                        style = stroke,
                    )
                }

                val windowY = size.height * (.28f + windowOpen * .11f)
                drawLine(
                    color = accent.copy(alpha = .9f),
                    start = Offset(size.width * .44f, windowY),
                    end = Offset(size.width * .59f, windowY),
                    strokeWidth = stroke.width,
                )

                val mirror = VehiclePreviewAnchor(.72f, .36f).offsetIn(size)
                rotate(degrees = mirrorAngle * .22f, pivot = mirror) {
                    drawCircle(
                        color = accent.copy(alpha = .9f),
                        radius = size.minDimension * .045f,
                        center = mirror,
                        style = stroke,
                    )
                }
                drawCircle(
                    color = lockColor,
                    radius = size.minDimension * .027f,
                    center = VehiclePreviewAnchor(.48f, .43f).offsetIn(size),
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
