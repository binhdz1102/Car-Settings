package com.android.car.settings.feature.seatcontrol.presentation

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
import com.android.car.settings.feature.seatcontrol.R

/**
 * A concise live diagram rather than an animated image. Every transform below derives from the
 * read/observed `*_POS` properties; missing properties retain a neutral position.
 */
@Composable
internal fun SeatControlVisualization(
    controls: List<VehicleControlUiModel>,
    selectedControl: VehicleControlUiModel?,
    modifier: Modifier = Modifier,
) {
    val areaId = selectedControl?.areaId ?: controls.firstOrNull()?.areaId ?: 0
    val motion =
        remember(controls, areaId) {
            seatVisualMotion(controls.filter { it.areaId == areaId || it.areaId == 0 })
        }
    val foreAft by animateFloatAsState(motion.foreAft, seatMotionSpec(), label = "seat-fore-aft")
    val height by animateFloatAsState(motion.height, seatMotionSpec(), label = "seat-height")
    val depth by animateFloatAsState(motion.depth, seatMotionSpec(), label = "seat-depth")
    val tilt by animateFloatAsState(motion.tilt, seatMotionSpec(), label = "seat-tilt")
    val backrest by animateFloatAsState(motion.backrest, seatMotionSpec(), label = "seat-backrest")
    val headrestHeight by animateFloatAsState(motion.headrestHeight, seatMotionSpec(), label = "seat-headrest-height")
    val headrestAngle by animateFloatAsState(motion.headrestAngle, seatMotionSpec(), label = "seat-headrest-angle")
    val lumbar by animateFloatAsState(motion.lumbar, seatMotionSpec(), label = "seat-lumbar")
    val px = with(LocalDensity.current) { 68.dp.toPx() }
    val visualizationDescription = stringResource(R.string.seat_visualization_content_description)

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
                        .align(Alignment.Center)
                        .offset(y = 72.dp)
                        .size(width = 228.dp, height = 8.dp)
                        .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = (foreAft - .5f) * px
                            translationY = (.5f - height) * px * .48f
                            rotationZ = (tilt - .5f) * 18f
                            transformOrigin = TransformOrigin(.5f, .5f)
                        }.offset(y = 34.dp)
                        .size(width = (112 + (depth * 38).toInt()).dp, height = 48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = (foreAft - .5f) * px - px * .52f
                            translationY = (.5f - height) * px * .48f
                            rotationZ = -16f + (backrest - .5f) * 44f
                            transformOrigin = TransformOrigin(1f, 1f)
                        }.offset(x = (-58).dp, y = (-24).dp)
                        .size(width = 46.dp, height = 118.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = (foreAft - .5f) * px - px * .53f
                            translationY = (.5f - headrestHeight) * px * .72f
                            rotationZ = (headrestAngle - .5f) * 22f
                        }.offset(x = (-58).dp, y = (-109).dp)
                        .size(width = 48.dp, height = 34.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = (foreAft - .5f) * px + (lumbar - .5f) * px * .24f - px * .5f
                            translationY = (.5f - height) * px * .42f
                        }.offset(x = (-42).dp, y = 4.dp)
                        .size(width = 18.dp, height = 42.dp)
                        .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(12.dp)),
            )
        }
    }
}

private fun seatMotionSpec() = tween<Float>(durationMillis = 420, easing = FastOutSlowInEasing)

internal data class SeatVisualMotion(
    val foreAft: Float = .5f,
    val height: Float = .5f,
    val depth: Float = .5f,
    val tilt: Float = .5f,
    val backrest: Float = .5f,
    val headrestHeight: Float = .5f,
    val headrestAngle: Float = .5f,
    val lumbar: Float = .5f,
)

/** Pure, bounded targets for the state-driven seat diagram. */
internal fun seatVisualMotion(controls: List<VehicleControlUiModel>): SeatVisualMotion {
    val byKey =
        buildMap {
            controls.forEach { control ->
                if (!containsKey(control.key)) put(control.key, control)
            }
        }

    fun level(key: String): Float {
        val control = byKey[key] ?: return .5f
        return normalizedSeatPosition(control.numericValue, control.range)
    }
    return SeatVisualMotion(
        foreAft = level("FORE_AFT"),
        height = level("HEIGHT"),
        depth = level("DEPTH"),
        tilt = level("TILT"),
        backrest = level("BACKREST_PRIMARY"),
        headrestHeight = level("HEADREST_HEIGHT"),
        headrestAngle = level("HEADREST_ANGLE"),
        lumbar = level("LUMBAR_FORE_AFT"),
    )
}

internal fun normalizedSeatPosition(
    value: Float?,
    range: ClosedFloatingPointRange<Float>,
    fallback: Float = .5f,
): Float =
    when {
        value == null || !value.isFinite() -> fallback
        else -> {
            val span = range.endInclusive - range.start
            if (span.isFinite() && span > 0f) {
                ((value - range.start) / span).coerceIn(0f, 1f)
            } else {
                fallback
            }
        }
    }

internal fun interpolateSeatMotion(
    from: SeatVisualMotion,
    to: SeatVisualMotion,
    fraction: Float,
): SeatVisualMotion {
    val t = fraction.coerceIn(0f, 1f)

    fun lerp(
        start: Float,
        end: Float,
    ) = start + (end - start) * t
    return SeatVisualMotion(
        foreAft = lerp(from.foreAft, to.foreAft),
        height = lerp(from.height, to.height),
        depth = lerp(from.depth, to.depth),
        tilt = lerp(from.tilt, to.tilt),
        backrest = lerp(from.backrest, to.backrest),
        headrestHeight = lerp(from.headrestHeight, to.headrestHeight),
        headrestAngle = lerp(from.headrestAngle, to.headrestAngle),
        lumbar = lerp(from.lumbar, to.lumbar),
    )
}
