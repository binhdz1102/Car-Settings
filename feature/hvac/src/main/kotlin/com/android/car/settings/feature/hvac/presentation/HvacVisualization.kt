package com.android.car.settings.feature.hvac.presentation

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.android.car.settings.core.ui.drawAirflowStreamline
import com.android.car.settings.core.ui.drawAmbientBeacon
import com.android.car.settings.core.ui.normalizedVehiclePreviewValue
import com.android.car.settings.core.ui.offsetIn
import com.android.car.settings.feature.hvac.R

@Composable
internal fun HvacVisualization(
    controls: List<VehicleControlUiModel>,
    selectedControl: VehicleControlUiModel?,
    visualPolicy: VehicleVisualPolicy = VehicleVisualPolicy(true, true, true),
    modifier: Modifier = Modifier,
) {
    val observed =
        controls.firstOrNull {
            it.key == selectedControl?.key && it.areaId == selectedControl.areaId
        } ?: selectedControl
    val key = observed?.key.orEmpty().uppercase()
    val snapshot = observed?.observedSnapshot
    val normalized =
        normalizedVehiclePreviewValue(
            snapshot?.numericValue,
            observed?.range ?: (0f..1f),
        )
    val enabledTarget =
        if (snapshot?.status == VehicleObservationStatus.CONFIRMED) {
            if (snapshot.booleanValue != false) 1f else .25f
        } else {
            .65f
        }
    val intensity by
        animateFloatAsState(
            targetValue =
                if (snapshot?.status == VehicleObservationStatus.CONFIRMED && snapshot.numericValue != null) {
                    normalized.coerceAtLeast(.18f)
                } else {
                    enabledTarget
                },
            animationSpec =
                tween(
                    if (visualPolicy.allowPreviewTransition) {
                        if ("POSITION" in key) 400 else 240
                    } else {
                        0
                    },
                ),
            label = "climate-observed-intensity",
        )
    val cool = Color(0xFF24C7F4)
    val warm = Color(0xFFFF9E42)
    val accent = if ("HEAT" in key || "DEFROST" in key) warm else cool
    val description = stringResource(R.string.hvac_visualization_content_description)

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box {
            VehicleIllustrationImage(
                illustrationRes = R.drawable.vehicle_preview_climate,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(Modifier.fillMaxSize()) {
                when {
                    "DEFROST" in key -> {
                        val stroke = Stroke(size.minDimension * .010f, cap = StrokeCap.Round)
                        val center = VehiclePreviewAnchor(.37f, .22f).offsetIn(size)
                        drawArc(
                            color = accent.copy(alpha = intensity * .75f),
                            startAngle = 195f,
                            sweepAngle = 145f,
                            useCenter = false,
                            topLeft =
                                Offset(
                                    center.x - size.width * .16f,
                                    center.y - size.height * .10f,
                                ),
                            size = Size(size.width * .32f, size.height * .20f),
                            style = stroke,
                        )
                        drawAmbientBeacon(
                            center = center,
                            radius = size.minDimension * .014f,
                            color = accent,
                            pulse = intensity,
                        )
                    }
                    "RECIRCULATION" in key -> {
                        val cabin = VehiclePreviewAnchor(.58f, .48f).offsetIn(size)
                        drawArc(
                            color = accent.copy(alpha = intensity * .65f),
                            startAngle = 30f,
                            sweepAngle = 280f,
                            useCenter = false,
                            topLeft = Offset(cabin.x - size.minDimension * .12f, cabin.y - size.minDimension * .12f),
                            size = Size(size.minDimension * .24f, size.minDimension * .24f),
                            style = Stroke(size.minDimension * .010f, cap = StrokeCap.Round),
                        )
                        drawAmbientBeacon(
                            center = cabin,
                            radius = size.minDimension * .014f,
                            color = accent,
                            pulse = intensity,
                        )
                    }
                    "TEMPERATURE" in key || "HEAT" in key -> {
                        val cabin = VehiclePreviewAnchor(.58f, .48f).offsetIn(size)
                        drawCircle(accent.copy(alpha = intensity * .15f), size.minDimension * .22f, cabin)
                        drawAmbientBeacon(
                            center = cabin,
                            radius = size.minDimension * .018f,
                            color = accent,
                            pulse = intensity,
                        )
                    }
                    else -> {
                        // 1. Driver Dashboard Vent Streamline
                        val driverVent = VehiclePreviewAnchor(.228f, .362f).offsetIn(size)
                        drawAmbientBeacon(
                            center = driverVent,
                            radius = size.minDimension * .011f,
                            color = accent,
                            pulse = intensity,
                        )
                        drawAirflowStreamline(
                            start = driverVent,
                            control1 = Offset(driverVent.x + size.width * .06f, driverVent.y + size.height * .08f),
                            control2 = Offset(driverVent.x + size.width * .12f, driverVent.y + size.height * .16f),
                            end = Offset(driverVent.x + size.width * .16f, driverVent.y + size.height * .22f),
                            color = accent,
                            intensity = intensity,
                            strokeWidth = size.minDimension * .008f,
                        )

                        // 2. Center Console Vent Streamlines
                        val centerVent = VehiclePreviewAnchor(.369f, .280f).offsetIn(size)
                        drawAmbientBeacon(
                            center = centerVent,
                            radius = size.minDimension * .011f,
                            color = accent,
                            pulse = intensity,
                        )
                        drawAirflowStreamline(
                            start = centerVent,
                            control1 = Offset(centerVent.x + size.width * .05f, centerVent.y + size.height * .06f),
                            control2 = Offset(centerVent.x + size.width * .09f, centerVent.y + size.height * .12f),
                            end = Offset(centerVent.x + size.width * .12f, centerVent.y + size.height * .16f),
                            color = accent,
                            intensity = intensity,
                            strokeWidth = size.minDimension * .008f,
                        )

                        // 3. Passenger Vent Streamline
                        val passVent = VehiclePreviewAnchor(.439f, .162f).offsetIn(size)
                        drawAmbientBeacon(
                            center = passVent,
                            radius = size.minDimension * .011f,
                            color = accent,
                            pulse = intensity,
                        )
                        drawAirflowStreamline(
                            start = passVent,
                            control1 = Offset(passVent.x + size.width * .04f, passVent.y + size.height * .06f),
                            control2 = Offset(passVent.x + size.width * .07f, passVent.y + size.height * .12f),
                            end = Offset(passVent.x + size.width * .10f, passVent.y + size.height * .18f),
                            color = accent,
                            intensity = intensity,
                            strokeWidth = size.minDimension * .008f,
                        )

                        // 4. Rear Console Vent Streamline
                        val rearVent = VehiclePreviewAnchor(.516f, .492f).offsetIn(size)
                        drawAmbientBeacon(
                            center = rearVent,
                            radius = size.minDimension * .011f,
                            color = accent,
                            pulse = intensity,
                        )
                        drawAirflowStreamline(
                            start = rearVent,
                            control1 = Offset(rearVent.x + size.width * .05f, rearVent.y + size.height * .04f),
                            control2 = Offset(rearVent.x + size.width * .10f, rearVent.y + size.height * .08f),
                            end = Offset(rearVent.x + size.width * .14f, rearVent.y + size.height * .12f),
                            color = accent,
                            intensity = intensity,
                            strokeWidth = size.minDimension * .008f,
                        )
                    }
                }
            }
        }
    }
}

/** One-shot instructional airflow scene; it is never used as live HVAC telemetry. */
@Composable
internal fun HvacGuideVisualization(
    selectedControl: VehicleControlUiModel,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val key = selectedControl.key.uppercase()
    val t = progress.coerceIn(0f, 1f)
    val cool = Color(0xFF24C7F4)
    val warm = Color(0xFFFF9E42)
    val accent = if ("DEFROST" in key || "HEAT" in key) warm else cool
    Surface(
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box {
            VehicleIllustrationImage(
                illustrationRes = R.drawable.vehicle_preview_climate,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(size.minDimension * .016f, cap = StrokeCap.Round)
                when {
                    "RECIRCULATION" in key -> {
                        drawArc(
                            color = accent.copy(alpha = t),
                            startAngle = 20f,
                            sweepAngle = 300f * t,
                            useCenter = false,
                            topLeft = VehiclePreviewAnchor(.38f, .28f).offsetIn(size),
                            size =
                                androidx.compose.ui.geometry
                                    .Size(size.width * .25f, size.height * .30f),
                            style = stroke,
                        )
                    }
                    "DEFROST" in key -> {
                        val start = VehiclePreviewAnchor(.40f, .50f).offsetIn(size)
                        val end = VehiclePreviewAnchor(.40f, .18f).offsetIn(size)
                        drawLine(accent.copy(alpha = t), start, start + (end - start) * t, stroke.width)
                        drawLine(
                            accent.copy(alpha = t),
                            start +
                                androidx.compose.ui.geometry
                                    .Offset(size.width * .08f, 0f),
                            end + androidx.compose.ui.geometry
                                .Offset(size.width * .08f, 0f) * t,
                            stroke.width,
                        )
                    }
                    else -> {
                        val start = VehiclePreviewAnchor(.28f, .55f).offsetIn(size)
                        val end = VehiclePreviewAnchor(.72f, .55f).offsetIn(size)
                        drawLine(accent.copy(alpha = t), start, start + (end - start) * t, stroke.width)
                        drawCircle(accent.copy(alpha = t), size.minDimension * .025f, start + (end - start) * t)
                    }
                }
            }
        }
    }
}
