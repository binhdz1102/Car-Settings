package com.android.car.settings.feature.seatcontrol.presentation

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.car.settings.core.ui.VehicleControlUiModel
import com.android.car.settings.core.ui.VehicleIllustrationImage
import com.android.car.settings.core.ui.VehicleObservationStatus
import com.android.car.settings.core.ui.VehiclePreviewAnchor
import com.android.car.settings.core.ui.VehicleVisualPolicy
import com.android.car.settings.core.ui.offsetIn
import com.android.car.settings.feature.seatcontrol.R

/**
 * A concise live diagram rather than an animated image. Every transform below derives from the
 * read/observed `*_POS` properties; missing properties retain a neutral position.
 */
@Composable
internal fun SeatControlVisualization(
    controls: List<VehicleControlUiModel>,
    selectedControl: VehicleControlUiModel?,
    visualPolicy: VehicleVisualPolicy = VehicleVisualPolicy(true, true, true),
    modifier: Modifier = Modifier,
) {
    val areaId = selectedControl?.areaId ?: controls.firstOrNull()?.areaId ?: 0
    val motion =
        remember(controls, areaId) {
            seatVisualMotion(controls.filter { it.areaId == areaId || it.areaId == 0 })
        }
    val foreAft by animateFloatAsState(motion.foreAft, seatMotionSpec(visualPolicy), label = "seat-fore-aft")
    val height by animateFloatAsState(motion.height, seatMotionSpec(visualPolicy), label = "seat-height")
    val depth by animateFloatAsState(motion.depth, seatMotionSpec(visualPolicy), label = "seat-depth")
    val tilt by animateFloatAsState(motion.tilt, seatMotionSpec(visualPolicy), label = "seat-tilt")
    val backrest by animateFloatAsState(motion.backrest, seatMotionSpec(visualPolicy), label = "seat-backrest")
    val headrestHeight by animateFloatAsState(motion.headrestHeight, seatMotionSpec(visualPolicy), label = "seat-headrest-height")
    val headrestAngle by animateFloatAsState(motion.headrestAngle, seatMotionSpec(visualPolicy), label = "seat-headrest-angle")
    val lumbar by animateFloatAsState(motion.lumbar, seatMotionSpec(visualPolicy), label = "seat-lumbar")
    val selectedKey = selectedControl?.key.orEmpty()
    val accent = MaterialTheme.colorScheme.primary
    val visualizationDescription = stringResource(R.string.seat_visualization_content_description)

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
                illustrationRes = R.drawable.vehicle_preview_seat_steering,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = size.minDimension * .018f)
                val seatShift = (foreAft - .5f) * size.width * .08f
                val seatLift = (.5f - height) * size.height * .08f
                val seat = VehiclePreviewAnchor(.70f, .57f).offsetIn(size) + Offset(seatShift, seatLift)
                val head =
                    VehiclePreviewAnchor(.71f, .17f).offsetIn(size) +
                        Offset(seatShift, (.5f - headrestHeight) * size.height * .10f)
                val steering = VehiclePreviewAnchor(.34f, .38f).offsetIn(size)
                val railStart = VehiclePreviewAnchor(.48f, .88f).offsetIn(size)
                val railEnd = VehiclePreviewAnchor(.78f, .88f).offsetIn(size)

                drawLine(accent.copy(alpha = .38f), railStart, railEnd, stroke.width)
                drawCircle(accent.copy(alpha = .9f), size.minDimension * .022f, seat)

                when {
                    "HEADREST" in selectedKey ->
                        drawCircle(accent.copy(alpha = .9f), size.minDimension * (.07f + headrestAngle * .025f), head, style = stroke)
                    "STEERING" in selectedKey ->
                        drawCircle(accent.copy(alpha = .9f), size.minDimension * .095f, steering, style = stroke)
                    "BACKREST" in selectedKey || "LUMBAR" in selectedKey -> {
                        val width = size.width * (.13f + lumbar * .025f)
                        val topLeft = Offset(seat.x - width * .42f, seat.y - size.height * (.29f + backrest * .03f))
                        drawRoundRect(
                            accent.copy(alpha = .88f),
                            topLeft,
                            Size(width, size.height * .28f),
                            CornerRadius(size.minDimension * .035f),
                            style = stroke,
                        )
                    }
                    else -> {
                        val width = size.width * (.18f + depth * .04f)
                        val topLeft = Offset(seat.x - width * .5f, seat.y - size.height * .02f)
                        drawRoundRect(
                            accent.copy(alpha = .88f),
                            topLeft,
                            Size(width, size.height * (.12f + tilt * .018f)),
                            CornerRadius(size.minDimension * .035f),
                            style = stroke,
                        )
                    }
                }
            }
        }
    }
}

private fun seatMotionSpec(policy: VehicleVisualPolicy) =
    tween<Float>(durationMillis = if (policy.allowPreviewTransition) 420 else 0, easing = FastOutSlowInEasing)

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
        return normalizedSeatPosition(
            control.observedSnapshot.numericValue.takeIf {
                control.observedSnapshot.status == VehicleObservationStatus.CONFIRMED
            },
            control.range,
        )
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
