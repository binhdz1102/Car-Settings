package com.android.car.settings.feature.driverassistance.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.android.car.settings.core.ui.VehicleControlUiModel
import com.android.car.settings.core.ui.VehicleIllustrationImage
import com.android.car.settings.core.ui.VehicleVisualPolicy
import com.android.car.settings.feature.driverassistance.R
import com.android.car.settings.feature.driverassistance.domain.DriverAssistanceId

/**
 * Instructional motion for ADAS guidance only. This VHAL catalog exposes configuration/enabled
 * properties, not live detections or warning events, therefore no animation is presented as live
 * sensor state.
 */
@Composable
internal fun DriverAssistanceVisualization(
    selectedControl: VehicleControlUiModel?,
    visualPolicy: VehicleVisualPolicy = VehicleVisualPolicy(true, true, true),
    modifier: Modifier = Modifier,
) {
    val title = selectedControl?.title.orEmpty()
    val key = selectedControl?.key.orEmpty()
    // The category preview is context-only. Instructional motion is rendered by the Info guide.
    val pulse = .55f
    val primary = MaterialTheme.colorScheme.primary
    val warning = MaterialTheme.colorScheme.tertiary
    val visualizationDescription =
        stringResource(R.string.driver_assistance_visualization_content_description, title)
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
                illustrationRes = R.drawable.vehicle_preview_driver_assistance,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width * .52f, size.height * .59f)
                when (driverAssistanceIllustrationFamily(key)) {
                    DriverAssistanceIllustrationFamily.LANE ->
                        drawLaneIllustration(center, primary, warning, pulse)
                    DriverAssistanceIllustrationFamily.BLIND_SPOT ->
                        drawBlindSpotIllustration(center, warning, pulse)
                    DriverAssistanceIllustrationFamily.CROSS_TRAFFIC ->
                        drawCrossTrafficIllustration(center, warning, pulse)
                    DriverAssistanceIllustrationFamily.COLLISION ->
                        drawCollisionIllustration(center, warning, pulse)
                    DriverAssistanceIllustrationFamily.ADAPTIVE_CRUISE ->
                        drawAccIllustration(center, primary, pulse)
                    DriverAssistanceIllustrationFamily.PARKING ->
                        drawParkingIllustration(center, primary, pulse)
                    DriverAssistanceIllustrationFamily.GENERIC ->
                        drawGuidanceIllustration(center, primary, pulse)
                }
            }
        }
    }
}

/** One-shot instructional scene rendered from playback progress; it never reads or writes VHAL. */
@Composable
internal fun DriverAssistanceGuideVisualization(
    selectedControl: VehicleControlUiModel,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val warning = MaterialTheme.colorScheme.tertiary
    val pulse = progress.coerceIn(0f, 1f)
    Surface(
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box {
            VehicleIllustrationImage(
                illustrationRes = R.drawable.vehicle_preview_driver_assistance,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width * .52f, size.height * .59f)
                when (driverAssistanceIllustrationFamily(selectedControl.key)) {
                    DriverAssistanceIllustrationFamily.LANE ->
                        drawLaneIllustration(center, primary, warning, pulse)
                    DriverAssistanceIllustrationFamily.BLIND_SPOT ->
                        drawBlindSpotIllustration(center, warning, pulse)
                    DriverAssistanceIllustrationFamily.CROSS_TRAFFIC ->
                        drawCrossTrafficIllustration(center, warning, pulse)
                    DriverAssistanceIllustrationFamily.COLLISION ->
                        drawCollisionIllustration(center, warning, pulse)
                    DriverAssistanceIllustrationFamily.ADAPTIVE_CRUISE ->
                        drawAccIllustration(center, primary, pulse)
                    DriverAssistanceIllustrationFamily.PARKING ->
                        drawParkingIllustration(center, primary, pulse)
                    DriverAssistanceIllustrationFamily.GENERIC ->
                        drawGuidanceIllustration(center, primary, pulse)
                }
            }
        }
    }
}

internal enum class DriverAssistanceIllustrationFamily {
    LANE,
    BLIND_SPOT,
    CROSS_TRAFFIC,
    COLLISION,
    ADAPTIVE_CRUISE,
    PARKING,
    GENERIC,
}

/** Maps configuration controls to a clearly labeled instructional overlay family. */
internal fun driverAssistanceIllustrationFamily(key: String): DriverAssistanceIllustrationFamily {
    val normalized = key.substringAfterLast(':').uppercase()
    if (normalized == "ADAPTIVE_CRUISE_ENABLED") {
        return DriverAssistanceIllustrationFamily.ADAPTIVE_CRUISE
    }
    if (normalized == "PARKING_VOLUME") {
        return DriverAssistanceIllustrationFamily.PARKING
    }
    val id =
        runCatching {
            DriverAssistanceId.valueOf(normalized)
        }.getOrNull()
    return when (id) {
        DriverAssistanceId.BLIND_SPOT_WARNING -> DriverAssistanceIllustrationFamily.BLIND_SPOT
        DriverAssistanceId.CROSS_TRAFFIC_MONITORING -> DriverAssistanceIllustrationFamily.CROSS_TRAFFIC
        DriverAssistanceId.LANE_DEPARTURE_WARNING,
        DriverAssistanceId.LANE_KEEP_ASSIST,
        DriverAssistanceId.LANE_CENTERING_ASSIST,
        DriverAssistanceId.EMERGENCY_LANE_KEEP_ASSIST,
        DriverAssistanceId.LANE_WARNING_SENSITIVITY,
        DriverAssistanceId.LANE_ALERT_MODE,
        -> DriverAssistanceIllustrationFamily.LANE
        DriverAssistanceId.AUTOMATIC_EMERGENCY_BRAKING,
        DriverAssistanceId.FORWARD_COLLISION_WARNING,
        DriverAssistanceId.LOW_SPEED_COLLISION_WARNING,
        DriverAssistanceId.LOW_SPEED_AUTOMATIC_EMERGENCY_BRAKING,
        DriverAssistanceId.COLLISION_WARNING_TIMING,
        -> DriverAssistanceIllustrationFamily.COLLISION
        DriverAssistanceId.CRUISE_CONTROL,
        DriverAssistanceId.ACC_ACCELERATION_PROFILE,
        -> DriverAssistanceIllustrationFamily.ADAPTIVE_CRUISE
        DriverAssistanceId.FRONT_PARKING_ASSISTANCE,
        DriverAssistanceId.REAR_PARKING_ASSISTANCE,
        DriverAssistanceId.PARKING_WARNING_VOLUME,
        DriverAssistanceId.SAFE_EXIT_ASSIST,
        -> DriverAssistanceIllustrationFamily.PARKING
        else -> DriverAssistanceIllustrationFamily.GENERIC
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLaneIllustration(
    center: Offset,
    primary: Color,
    warning: Color,
    pulse: Float,
) {
    val stroke = Stroke(width = size.minDimension * .018f, cap = StrokeCap.Round)
    drawLine(
        primary.copy(alpha = pulse),
        Offset(size.width * .23f, size.height * .1f),
        Offset(center.x - size.width * .08f, size.height * .94f),
        strokeWidth = stroke.width,
    )
    drawLine(
        primary.copy(alpha = pulse),
        Offset(size.width * .77f, size.height * .1f),
        Offset(center.x + size.width * .08f, size.height * .94f),
        strokeWidth = stroke.width,
    )
    drawCircle(
        warning.copy(alpha = pulse),
        size.minDimension * .035f,
        Offset(center.x + size.width * .13f, center.y - size.height * .12f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlindSpotIllustration(
    center: Offset,
    warning: Color,
    pulse: Float,
) {
    val target = Offset(center.x + size.width * .18f, center.y)
    drawCircle(warning.copy(alpha = pulse * .2f), size.minDimension * .19f, target)
    drawCircle(warning.copy(alpha = pulse), size.minDimension * .05f, target)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrossTrafficIllustration(
    center: Offset,
    warning: Color,
    pulse: Float,
) {
    val roadY = center.y + size.height * .18f
    drawLine(
        warning.copy(alpha = pulse * .72f),
        Offset(size.width * .12f, roadY),
        Offset(size.width * .88f, roadY),
        strokeWidth = size.minDimension * .022f,
        cap = StrokeCap.Round,
    )
    drawCircle(warning.copy(alpha = pulse), size.minDimension * .045f, Offset(center.x, roadY))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCollisionIllustration(
    center: Offset,
    warning: Color,
    pulse: Float,
) {
    val target = Offset(center.x, center.y - size.height * .26f)
    drawCircle(warning.copy(alpha = .85f), size.minDimension * .07f, target)
    drawCircle(
        warning.copy(alpha = pulse * .55f),
        size.minDimension * (.10f + pulse * .08f),
        target,
        style =
            Stroke(
                size.minDimension * .014f,
            ),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAccIllustration(
    center: Offset,
    primary: Color,
    pulse: Float,
) {
    val target = Offset(center.x, center.y - size.height * (.17f + pulse * .09f))
    drawCircle(primary.copy(alpha = .92f), size.minDimension * .055f, target)
    drawLine(primary.copy(alpha = pulse), center, target, strokeWidth = size.minDimension * .018f, cap = StrokeCap.Round)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParkingIllustration(
    center: Offset,
    primary: Color,
    pulse: Float,
) {
    repeat(3) { index ->
        drawCircle(
            primary.copy(alpha = pulse * (.34f - index * .07f)),
            size.minDimension * (.18f + index * .07f),
            center,
            style = Stroke(size.minDimension * .012f),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGuidanceIllustration(
    center: Offset,
    primary: Color,
    pulse: Float,
) {
    drawCircle(primary.copy(alpha = pulse * .22f), size.minDimension * .30f, center, style = Stroke(size.minDimension * .02f))
}
