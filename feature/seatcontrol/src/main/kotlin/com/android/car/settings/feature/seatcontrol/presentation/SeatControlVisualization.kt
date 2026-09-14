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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.android.car.settings.core.ui.drawTechnicalTrackRuler
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
                val stroke = Stroke(width = size.minDimension * .012f)
                val seatShift = (foreAft - .5f) * size.width * .06f
                val seatLift = (.5f - height) * size.height * .06f
                val seatHinge = VehiclePreviewAnchor(.675f, .730f).offsetIn(size) + Offset(seatShift, seatLift)
                val head =
                    VehiclePreviewAnchor(.690f, .130f).offsetIn(size) +
                        Offset(seatShift, (.5f - headrestHeight) * size.height * .08f)
                val steering = VehiclePreviewAnchor(.310f, .370f).offsetIn(size)
                val railStart = VehiclePreviewAnchor(.440f, .870f).offsetIn(size)
                val railEnd = VehiclePreviewAnchor(.680f, .845f).offsetIn(size)

                // 1. Mechanical Seat Track Ruler along slider rail
                drawTechnicalTrackRuler(
                    start = railStart,
                    end = railEnd,
                    normalizedPosition = foreAft,
                    color = accent,
                    intensity = if ("FORE_AFT" in selectedKey || selectedKey.isBlank()) 1f else .45f,
                )

                // 2. Feature-specific telemetry overlay
                when {
                    "HEADREST" in selectedKey -> {
                        drawAmbientBeacon(
                            center = head,
                            radius = size.minDimension * (.014f + headrestAngle * .006f),
                            color = accent,
                        )
                        drawAngleArcGauge(
                            center = head,
                            radius = size.minDimension * .045f,
                            startAngle = 260f,
                            sweepAngle = 40f,
                            currentProgress = headrestAngle,
                            color = accent,
                        )
                    }
                    "STEERING" in selectedKey -> {
                        drawCircle(
                            color = accent.copy(alpha = .30f),
                            radius = size.minDimension * .095f,
                            center = steering,
                            style = stroke,
                        )
                        drawAmbientBeacon(
                            center = steering,
                            radius = size.minDimension * .012f,
                            color = accent,
                        )
                    }
                    "BACKREST" in selectedKey -> {
                        drawAngleArcGauge(
                            center = seatHinge,
                            radius = size.minDimension * .075f,
                            startAngle = 235f,
                            sweepAngle = 45f,
                            currentProgress = backrest,
                            color = accent,
                        )
                    }
                    "LUMBAR" in selectedKey -> {
                        val lumbarCenter = VehiclePreviewAnchor(.615f, .540f).offsetIn(size) + Offset(seatShift, seatLift)
                        drawAmbientBeacon(
                            center = lumbarCenter,
                            radius = size.minDimension * (.012f + lumbar * .010f),
                            color = accent,
                        )
                    }
                    else -> {
                        // Cushion adjustment / Height / Tilt
                        drawAmbientBeacon(
                            center = seatHinge,
                            radius = size.minDimension * .014f,
                            color = accent,
                            pulse = .75f,
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
