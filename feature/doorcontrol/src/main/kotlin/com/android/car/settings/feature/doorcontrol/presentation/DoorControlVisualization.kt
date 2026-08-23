package com.android.car.settings.feature.doorcontrol.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.car.settings.core.ui.VehicleControlUiModel
import com.android.car.settings.core.ui.VehicleIllustrationImage
import com.android.car.settings.feature.doorcontrol.R

/**
 * Dedicated vehicle outline driven only by the position/state properties. `DOOR_MOVE` and
 * `WINDOW_MOVE` are commands and are intentionally never used to animate this diagram.
 */
@Composable
internal fun DoorControlVisualization(
    controls: List<VehicleControlUiModel>,
    selectedControl: VehicleControlUiModel?,
    modifier: Modifier = Modifier,
) {
    val areaId = selectedControl?.areaId ?: controls.firstOrNull()?.areaId ?: 0
    val motion =
        remember(controls, areaId) {
            doorVisualMotion(controls.filter { it.areaId == areaId || it.areaId == 0 })
        }
    val doorOpen by animateFloatAsState(motion.doorOpen, doorMotionSpec(), label = "door-position")
    val windowOpen by animateFloatAsState(motion.windowOpen, doorMotionSpec(), label = "window-position")
    val mirrorAngle by animateFloatAsState(if (motion.mirrorFolded) 72f else 0f, tween(360), label = "mirror-fold")
    val lockColor by
        animateColorAsState(
            if (motion.locked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
            tween(220),
            label = "door-lock",
        )
    val px = with(LocalDensity.current) { 74.dp.toPx() }
    val visualizationDescription = stringResource(R.string.door_visualization_content_description)

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .height(244.dp)
                .semantics {
                    contentDescription = visualizationDescription
                },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(contentAlignment = Alignment.Center) {
            selectedControl?.illustrationRes?.let { illustrationRes ->
                VehicleIllustrationImage(
                    illustrationRes = illustrationRes,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = .16f },
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(width = 196.dp, height = 126.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(42.dp)),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = 55.dp)
                        .graphicsLayer {
                            rotationZ = -doorOpen * 62f
                            transformOrigin = TransformOrigin(1f, .5f)
                        }.size(width = 72.dp, height = 88.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = 63.dp)
                        .graphicsLayer { translationY = windowOpen * px * .52f }
                        .size(width = 52.dp, height = 34.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = (-31).dp, y = (-48).dp)
                        .graphicsLayer {
                            rotationZ = mirrorAngle
                            transformOrigin = TransformOrigin(0f, .5f)
                        }.size(width = 46.dp, height = 18.dp)
                        .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp)),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .offset(x = (-17).dp)
                        .size(width = 34.dp, height = 22.dp)
                        .background(lockColor, RoundedCornerShape(8.dp)),
            )
        }
    }
}

private fun doorMotionSpec() = tween<Float>(durationMillis = 420, easing = FastOutSlowInEasing)

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
            control.numericValue == null -> 0f
            else -> {
                val value = requireNotNull(control.numericValue)
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
        mirrorFolded = byKey["MIRROR_FOLD"]?.booleanValue == true,
        locked = byKey["DOOR_LOCK"]?.booleanValue == true,
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
