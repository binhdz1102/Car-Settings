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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.android.car.settings.core.ui.VehicleControlUiModel
import com.android.car.settings.core.ui.VehicleIllustrationImage
import com.android.car.settings.core.ui.VehiclePreviewAnchor
import com.android.car.settings.core.ui.normalizedVehiclePreviewValue
import com.android.car.settings.core.ui.offsetIn
import com.android.car.settings.feature.hvac.R

@Composable
internal fun HvacVisualization(
    controls: List<VehicleControlUiModel>,
    selectedControl: VehicleControlUiModel?,
    modifier: Modifier = Modifier,
) {
    val observed =
        controls.firstOrNull {
            it.key == selectedControl?.key && it.areaId == selectedControl.areaId
        } ?: selectedControl
    val key = observed?.key.orEmpty().uppercase()
    val normalized = normalizedVehiclePreviewValue(observed?.numericValue, observed?.range ?: (0f..1f))
    val enabledTarget = if (observed?.booleanValue == false) .18f else 1f
    val intensity by
        animateFloatAsState(
            targetValue = if (observed?.numericValue != null) normalized.coerceAtLeast(.18f) else enabledTarget,
            animationSpec = tween(if ("POSITION" in key) 400 else 240),
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
                val stroke = Stroke(size.minDimension * .016f, cap = StrokeCap.Round)
                val vents =
                    listOf(
                        VehiclePreviewAnchor(.19f, .42f),
                        VehiclePreviewAnchor(.38f, .37f),
                        VehiclePreviewAnchor(.82f, .35f),
                    )
                when {
                    "DEFROST" in key -> {
                        val center = VehiclePreviewAnchor(.39f, .24f).offsetIn(size)
                        drawArc(
                            color = accent.copy(alpha = intensity),
                            startAngle = 205f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = center - androidx.compose.ui.geometry.Offset(size.width * .15f, size.height * .12f),
                            size = androidx.compose.ui.geometry.Size(size.width * .30f, size.height * .24f),
                            style = stroke,
                        )
                    }
                    "RECIRCULATION" in key -> {
                        drawCircle(
                            color = accent.copy(alpha = intensity),
                            radius = size.minDimension * .15f,
                            center = VehiclePreviewAnchor(.60f, .51f).offsetIn(size),
                            style = stroke,
                        )
                    }
                    "TEMPERATURE" in key || "HEAT" in key -> {
                        val cabin = VehiclePreviewAnchor(.62f, .53f).offsetIn(size)
                        drawCircle(accent.copy(alpha = intensity * .20f), size.minDimension * .22f, cabin)
                        drawCircle(accent.copy(alpha = intensity), size.minDimension * .028f, cabin)
                    }
                    else -> vents.forEach { anchor ->
                        val center = anchor.offsetIn(size)
                        drawCircle(accent.copy(alpha = intensity), size.minDimension * .035f, center, style = stroke)
                        drawLine(
                            accent.copy(alpha = intensity * .85f),
                            center,
                            center + androidx.compose.ui.geometry.Offset(size.width * (.05f + intensity * .035f), 0f),
                            stroke.width,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}
