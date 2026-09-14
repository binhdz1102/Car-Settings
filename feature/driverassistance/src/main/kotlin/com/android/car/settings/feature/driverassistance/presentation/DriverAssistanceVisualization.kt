package com.android.car.settings.feature.driverassistance.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.android.car.settings.core.ui.VehicleControlUiModel
import com.android.car.settings.core.ui.VehicleIllustrationImage
import com.android.car.settings.core.ui.VehicleVisualPolicy
import com.android.car.settings.core.ui.drawAmbientBeacon
import com.android.car.settings.core.ui.drawPerspectiveGroundRadar
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
    val pulse =
        if (visualPolicy.allowPreviewTransition) {
            val transition = rememberInfiniteTransition(label = "adasPulse")
            val pulseAnimation =
                transition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "pulseAlpha",
                )
            pulseAnimation.value
        } else {
            0.75f
        }
    val primary = MaterialTheme.colorScheme.primary
    val warning = Color(0xFFFF9800)
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
    val warning = Color(0xFFFF9800)
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
    // Road lane markings matching vehicle isometric perspective
    val leftStart = Offset(size.width * .44f, size.height * .18f)
    val leftEnd = Offset(size.width * .10f, size.height * .68f)
    val rightStart = Offset(size.width * .92f, size.height * .37f)
    val rightEnd = Offset(size.width * .46f, size.height * .95f)

    // Left lane boundary: soft guidance glow and line
    drawLine(
        color = primary.copy(alpha = 0.20f * pulse),
        start = leftStart,
        end = leftEnd,
        strokeWidth = size.minDimension * .022f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = primary.copy(alpha = 0.70f * pulse),
        start = leftStart,
        end = leftEnd,
        strokeWidth = size.minDimension * .007f,
        cap = StrokeCap.Round,
    )

    // Right lane boundary: warning alert highlight conforming to ground plane
    drawLine(
        color = warning.copy(alpha = 0.30f * pulse),
        start = rightStart,
        end = rightEnd,
        strokeWidth = size.minDimension * .026f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = warning.copy(alpha = 0.85f * pulse),
        start = rightStart,
        end = rightEnd,
        strokeWidth = size.minDimension * .009f,
        cap = StrokeCap.Round,
    )

    // Departure warning beacon on road boundary near right wheel
    drawAmbientBeacon(
        center = Offset(size.width * .58f, size.height * .79f),
        radius = size.minDimension * .018f,
        color = warning,
        pulse = pulse,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlindSpotIllustration(
    center: Offset,
    warning: Color,
    pulse: Float,
) {
    // Blind spot radar cone originating from rear right sensor out into adjacent lane
    val sensorOrigin = Offset(size.width * .75f, size.height * .42f)
    val radarLeftFar = Offset(size.width * .90f, size.height * .46f)
    val radarRightFar = Offset(size.width * .80f, size.height * .68f)

    drawPerspectiveGroundRadar(
        origin = sensorOrigin,
        leftFar = radarLeftFar,
        rightFar = radarRightFar,
        color = warning,
        pulse = pulse,
        rings = 3,
    )

    // Mirror indicator warning beacon (passenger side mirror)
    val mirrorCenter = Offset(size.width * .61f, size.height * .40f)
    drawAmbientBeacon(
        center = mirrorCenter,
        radius = size.minDimension * .015f,
        color = warning,
        pulse = pulse,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrossTrafficIllustration(
    center: Offset,
    warning: Color,
    pulse: Float,
) {
    // Rear cross traffic path across reversing zone
    val pathStart = Offset(size.width * .56f, size.height * .11f)
    val pathEnd = Offset(size.width * .95f, size.height * .37f)

    // Cross-traffic travel guideline
    drawLine(
        color = warning.copy(alpha = 0.25f * pulse),
        start = pathStart,
        end = pathEnd,
        strokeWidth = size.minDimension * .020f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = warning.copy(alpha = 0.75f * pulse),
        start = pathStart,
        end = pathEnd,
        strokeWidth = size.minDimension * .007f,
        cap = StrokeCap.Round,
    )

    // Rear detection radar sweep
    val rearCenter = Offset(size.width * .76f, size.height * .21f)
    drawPerspectiveGroundRadar(
        origin = rearCenter,
        leftFar = Offset(size.width * .64f, size.height * .14f),
        rightFar = Offset(size.width * .92f, size.height * .32f),
        color = warning,
        pulse = pulse,
        rings = 2,
    )

    // Approaching object alert beacon
    val approachTarget = Offset(size.width * .87f, size.height * .31f)
    drawAmbientBeacon(
        center = approachTarget,
        radius = size.minDimension * .022f,
        color = warning,
        pulse = pulse,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCollisionIllustration(
    center: Offset,
    warning: Color,
    pulse: Float,
) {
    // Forward collision warning radar cone projected onto road ahead of front bumper
    val grilleOrigin = Offset(size.width * .34f, size.height * .71f)
    val radarLeftFar = Offset(size.width * .06f, size.height * .84f)
    val radarRightFar = Offset(size.width * .36f, size.height * .98f)

    drawPerspectiveGroundRadar(
        origin = grilleOrigin,
        leftFar = radarLeftFar,
        rightFar = radarRightFar,
        color = warning,
        pulse = pulse,
        rings = 3,
    )

    // Imminent collision warning beacon in vehicle path
    val target = Offset(size.width * .20f, size.height * .87f)
    drawAmbientBeacon(
        center = target,
        radius = size.minDimension * .024f,
        color = warning,
        pulse = pulse,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAccIllustration(
    center: Offset,
    primary: Color,
    pulse: Float,
) {
    // Adaptive cruise distance tracking lane corridor on ground plane
    val leftTrackStart = Offset(size.width * .28f, size.height * .68f)
    val leftTrackEnd = Offset(size.width * .13f, size.height * .85f)
    val rightTrackStart = Offset(size.width * .43f, size.height * .76f)
    val rightTrackEnd = Offset(size.width * .27f, size.height * .94f)

    // Dynamic corridor guidelines
    drawLine(
        color = primary.copy(alpha = 0.45f * pulse),
        start = leftTrackStart,
        end = leftTrackEnd,
        strokeWidth = size.minDimension * .006f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = primary.copy(alpha = 0.45f * pulse),
        start = rightTrackStart,
        end = rightTrackEnd,
        strokeWidth = size.minDimension * .006f,
        cap = StrokeCap.Round,
    )

    // Distance tracking gap bars (3 segments conforming to perspective)
    for (step in 1..3) {
        val fraction = step / 4f
        val barStart =
            Offset(
                leftTrackStart.x + (leftTrackEnd.x - leftTrackStart.x) * fraction,
                leftTrackStart.y + (leftTrackEnd.y - leftTrackStart.y) * fraction,
            )
        val barEnd =
            Offset(
                rightTrackStart.x + (rightTrackEnd.x - rightTrackStart.x) * fraction,
                rightTrackStart.y + (rightTrackEnd.y - rightTrackStart.y) * fraction,
            )
        drawLine(
            color = primary.copy(alpha = (0.35f + fraction * 0.45f) * pulse),
            start = barStart,
            end = barEnd,
            strokeWidth = size.minDimension * .008f,
            cap = StrokeCap.Round,
        )
    }

    // Lead vehicle beacon ahead on the road
    val target = Offset(size.width * .18f, size.height * .88f)
    drawAmbientBeacon(
        center = target,
        radius = size.minDimension * .020f,
        color = primary,
        pulse = pulse,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParkingIllustration(
    center: Offset,
    primary: Color,
    pulse: Float,
) {
    // Front parking ultrasonic radar sweep
    val frontOrigin = Offset(size.width * .34f, size.height * .71f)
    drawPerspectiveGroundRadar(
        origin = frontOrigin,
        leftFar = Offset(size.width * .18f, size.height * .81f),
        rightFar = Offset(size.width * .43f, size.height * .88f),
        color = primary,
        pulse = pulse,
        rings = 3,
    )

    // Rear parking ultrasonic radar sweep
    val rearOrigin = Offset(size.width * .76f, size.height * .21f)
    drawPerspectiveGroundRadar(
        origin = rearOrigin,
        leftFar = Offset(size.width * .68f, size.height * .15f),
        rightFar = Offset(size.width * .86f, size.height * .26f),
        color = primary,
        pulse = pulse,
        rings = 3,
    )

    // Corner proximity sensor beacons
    drawAmbientBeacon(
        center = Offset(size.width * .27f, size.height * .66f),
        radius = size.minDimension * .013f,
        color = primary,
        pulse = pulse,
    )
    drawAmbientBeacon(
        center = Offset(size.width * .43f, size.height * .77f),
        radius = size.minDimension * .013f,
        color = primary,
        pulse = pulse,
    )
    drawAmbientBeacon(
        center = Offset(size.width * .78f, size.height * .27f),
        radius = size.minDimension * .013f,
        color = primary,
        pulse = pulse,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGuidanceIllustration(
    center: Offset,
    primary: Color,
    pulse: Float,
) {
    // Perimeter safety shield anchors conforming to vehicle footprint
    drawAmbientBeacon(
        center = Offset(size.width * .34f, size.height * .71f),
        radius = size.minDimension * .018f,
        color = primary,
        pulse = pulse,
    )
    drawAmbientBeacon(
        center = Offset(size.width * .76f, size.height * .21f),
        radius = size.minDimension * .018f,
        color = primary,
        pulse = pulse,
    )
}
