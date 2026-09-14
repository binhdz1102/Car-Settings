package com.android.car.settings.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs

/**
 * Shared Digital Twin HUD drawing utilities for vehicle preview screens.
 *
 * Replaces crude 2D bounding boxes and wireframe primitives with refined, automotive-grade
 * visual effects: soft focal beacons, perspective ground-plane radar, organic airflow streamlines,
 * and technical telemetry rulers.
 */
fun DrawScope.drawAmbientBeacon(
    center: Offset,
    radius: Float,
    color: Color,
    pulse: Float = 1f,
) {
    val clampedPulse = pulse.coerceIn(0f, 1f)
    if (clampedPulse <= 0f) return

    // Wide ambient halo
    drawCircle(
        color = color.copy(alpha = 0.12f * clampedPulse),
        radius = radius * 2.8f,
        center = center,
    )
    // Focused middle glow
    drawCircle(
        color = color.copy(alpha = 0.28f * clampedPulse),
        radius = radius * 1.6f,
        center = center,
    )
    // Core beacon
    drawCircle(
        color = color.copy(alpha = 0.85f * clampedPulse),
        radius = radius,
        center = center,
    )
    // Inner crisp highlight dot
    drawCircle(
        color = Color.White.copy(alpha = 0.95f * clampedPulse),
        radius = radius * 0.38f,
        center = center,
    )
}

/**
 * Draws an organic airflow streamline curving gracefully from a vent into the cabin.
 */
fun DrawScope.drawAirflowStreamline(
    start: Offset,
    control1: Offset,
    control2: Offset,
    end: Offset,
    color: Color,
    intensity: Float,
    strokeWidth: Float,
) {
    val alpha = intensity.coerceIn(0f, 1f)
    if (alpha <= 0.02f) return

    val path =
        Path().apply {
            moveTo(start.x, start.y)
            cubicTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)
        }

    val gradientBrush =
        Brush.linearGradient(
            colors =
                listOf(
                    color.copy(alpha = 0.85f * alpha),
                    color.copy(alpha = 0.45f * alpha),
                    color.copy(alpha = 0.05f * alpha),
                ),
            start = start,
            end = end,
        )

    // Outer soft wind trail
    drawPath(
        path = path,
        brush = gradientBrush,
        style =
            Stroke(
                width = strokeWidth * 2.4f,
                cap = StrokeCap.Round,
            ),
    )
    // Core crisp streamline
    drawPath(
        path = path,
        brush = gradientBrush,
        style =
            Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
            ),
    )
}

/**
 * Draws a perspective ground-plane radar/sonar detection cone conforming to road perspective.
 */
fun DrawScope.drawPerspectiveGroundRadar(
    origin: Offset,
    leftFar: Offset,
    rightFar: Offset,
    color: Color,
    pulse: Float,
    rings: Int = 3,
) {
    val clampedPulse = pulse.coerceIn(0f, 1f)
    if (clampedPulse <= 0.02f) return

    val groundPolygon =
        Path().apply {
            moveTo(origin.x, origin.y)
            lineTo(leftFar.x, leftFar.y)
            lineTo(rightFar.x, rightFar.y)
            close()
        }

    val farMid = Offset((leftFar.x + rightFar.x) * 0.5f, (leftFar.y + rightFar.y) * 0.5f)
    val fillBrush =
        Brush.linearGradient(
            colors =
                listOf(
                    color.copy(alpha = 0.26f * clampedPulse),
                    color.copy(alpha = 0.12f * clampedPulse),
                    color.copy(alpha = 0.02f * clampedPulse),
                ),
            start = origin,
            end = farMid,
        )

    // Ground plane wash
    drawPath(path = groundPolygon, brush = fillBrush, style = Fill)

    // Perspective sonar sweep lines expanding outward
    val stroke = Stroke(width = size.minDimension * 0.008f, cap = StrokeCap.Round)
    for (i in 1..rings) {
        val fraction = i.toFloat() / (rings + 1)
        val leftInterp = Offset(origin.x + (leftFar.x - origin.x) * fraction, origin.y + (leftFar.y - origin.y) * fraction)
        val rightInterp = Offset(origin.x + (rightFar.x - origin.x) * fraction, origin.y + (rightFar.y - origin.y) * fraction)
        val arcMid =
            Offset(
                (leftInterp.x + rightInterp.x) * 0.5f + (farMid.x - origin.x) * 0.08f,
                (leftInterp.y + rightInterp.y) * 0.5f + (farMid.y - origin.y) * 0.08f,
            )

        val arcPath =
            Path().apply {
                moveTo(leftInterp.x, leftInterp.y)
                quadraticTo(arcMid.x, arcMid.y, rightInterp.x, rightInterp.y)
            }

        drawPath(
            path = arcPath,
            color = color.copy(alpha = (0.55f - fraction * 0.25f) * clampedPulse),
            style = stroke,
        )
    }
}

/**
 * Draws a fine technical telemetry ruler along the seat sliding rail with travel arrows and cursor.
 */
fun DrawScope.drawTechnicalTrackRuler(
    start: Offset,
    end: Offset,
    normalizedPosition: Float,
    color: Color,
    intensity: Float = 1f,
    tickCount: Int = 8,
) {
    val alpha = intensity.coerceIn(0f, 1f)
    val strokeWidth = size.minDimension * 0.007f

    // Base rail line
    drawLine(
        color = color.copy(alpha = 0.35f * alpha),
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )

    // Tick marks along rail
    val dx = end.x - start.x
    val dy = end.y - start.y
    val normalX = -dy * 0.035f
    val normalY = dx * 0.035f

    for (i in 0 until tickCount) {
        val fraction = i.toFloat() / (tickCount - 1)
        val tickCenter = Offset(start.x + dx * fraction, start.y + dy * fraction)
        drawLine(
            color = color.copy(alpha = 0.30f * alpha),
            start = Offset(tickCenter.x - normalX * 0.5f, tickCenter.y - normalY * 0.5f),
            end = Offset(tickCenter.x + normalX * 0.5f, tickCenter.y + normalY * 0.5f),
            strokeWidth = strokeWidth * 0.8f,
            cap = StrokeCap.Round,
        )
    }

    // Active position indicator cursor
    val cursorClamped = normalizedPosition.coerceIn(0f, 1f)
    val cursor = Offset(start.x + dx * cursorClamped, start.y + dy * cursorClamped)

    // Glowing halo at active position
    drawCircle(
        color = color.copy(alpha = 0.25f * alpha),
        radius = size.minDimension * 0.025f,
        center = cursor,
    )
    drawCircle(
        color = color.copy(alpha = 0.90f * alpha),
        radius = size.minDimension * 0.012f,
        center = cursor,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.95f * alpha),
        radius = size.minDimension * 0.005f,
        center = cursor,
    )

    // Subtle travel arrows at ends
    val arrowLen = size.minDimension * 0.018f
    drawLine(
        color = color.copy(alpha = 0.45f * alpha),
        start = start,
        end = Offset(start.x + arrowLen, start.y - normalY * 0.3f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color.copy(alpha = 0.45f * alpha),
        start = end,
        end = Offset(end.x - arrowLen, end.y + normalY * 0.3f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
}

/**
 * Draws a technical degree arc gauge at an adjustment pivot (e.g. seat backrest hinge).
 */
fun DrawScope.drawAngleArcGauge(
    center: Offset,
    radius: Float,
    startAngle: Float,
    sweepAngle: Float,
    currentProgress: Float,
    color: Color,
    intensity: Float = 1f,
) {
    val alpha = intensity.coerceIn(0f, 1f)
    val strokeWidth = size.minDimension * 0.008f

    // Background track arc
    drawArc(
        color = color.copy(alpha = 0.22f * alpha),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )

    // Active sweep arc
    val activeSweep = (sweepAngle * currentProgress.coerceIn(0f, 1f))
    if (abs(activeSweep) > 0.5f) {
        drawArc(
            color = color.copy(alpha = 0.85f * alpha),
            startAngle = startAngle,
            sweepAngle = activeSweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = strokeWidth * 1.6f, cap = StrokeCap.Round),
        )
    }

    // Pivot center dot
    drawCircle(
        color = color.copy(alpha = 0.65f * alpha),
        radius = size.minDimension * 0.012f,
        center = center,
    )
}

/**
 * Draws an illuminated door seam silhouette glow along the door shut-line when unlatched or ajar.
 */
fun DrawScope.drawDoorSeamGlow(
    seamPath: Path,
    color: Color,
    openRatio: Float,
) {
    val clampedRatio = openRatio.coerceIn(0f, 1f)
    if (clampedRatio <= 0.01f) return

    val baseWidth = size.minDimension * 0.010f

    // Ambient diffuse glow along seam
    drawPath(
        path = seamPath,
        color = color.copy(alpha = 0.22f * clampedRatio),
        style = Stroke(width = baseWidth * 3.2f, cap = StrokeCap.Round),
    )
    // Intermediate bright glow
    drawPath(
        path = seamPath,
        color = color.copy(alpha = 0.55f * clampedRatio),
        style = Stroke(width = baseWidth * 1.6f, cap = StrokeCap.Round),
    )
    // Core highlight seam
    drawPath(
        path = seamPath,
        color = Color.White.copy(alpha = 0.85f * clampedRatio),
        style = Stroke(width = baseWidth * 0.6f, cap = StrokeCap.Round),
    )
}
