package com.android.car.settings.feature.vehiclelighting.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.car.settings.core.ui.VehicleControlUiModel
import com.android.car.settings.core.ui.VehicleIllustrationImage
import com.android.car.settings.feature.vehiclelighting.R

/**
 * Glow transitions are driven by the observed `*_SWITCH` property values. They communicate the
 * control state only, not a hardware lamp diagnostic, because this VHAL catalog has no lamp-state
 * property to subscribe to.
 */
@Composable
internal fun VehicleLightingVisualization(
    controls: List<VehicleControlUiModel>,
    selectedControl: VehicleControlUiModel?,
    modifier: Modifier = Modifier,
) {
    val selectedKey = selectedControl?.key
    val selectedAreaId = selectedControl?.areaId
    val selectedControlState =
        remember(controls, selectedKey, selectedAreaId) {
            controls
                .firstOrNull { it.key == selectedKey && it.areaId == selectedAreaId }
                ?.let { it.numericValue to it.booleanValue }
        }
    val glow by
        animateFloatAsState(
            targetValue =
                lightingGlowTarget(
                    numericValue = selectedControlState?.first,
                    booleanValue = selectedControlState?.second,
                ),
            animationSpec = tween(durationMillis = 360),
            label = "lighting-observed-glow",
        )
    val activeColor = MaterialTheme.colorScheme.tertiary
    val bodyColor = MaterialTheme.colorScheme.primaryContainer
    val visualizationDescription =
        stringResource(R.string.vehicle_lighting_visualization_content_description)

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
        Box {
            selectedControl?.illustrationRes?.let { illustrationRes ->
                VehicleIllustrationImage(
                    illustrationRes = illustrationRes,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = .18f },
                )
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bodyWidth = size.width * .60f
                val bodyHeight = size.height * .34f
                val left = (size.width - bodyWidth) / 2f
                val top = size.height * .36f
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(left, top),
                    size =
                        androidx.compose.ui.geometry
                            .Size(bodyWidth, bodyHeight),
                    cornerRadius =
                        androidx.compose.ui.geometry
                            .CornerRadius(bodyHeight * .42f),
                )
                drawLight(Offset(left + bodyWidth * .08f, top + bodyHeight * .40f), glow, activeColor)
                drawLight(Offset(left + bodyWidth * .92f, top + bodyHeight * .40f), glow, activeColor)
                // Interior glow mirrors the setting; it is not a hardware diagnostic.
                drawCircle(
                    color = activeColor.copy(alpha = glow * .22f),
                    radius = bodyHeight * .64f,
                    center = Offset(left + bodyWidth * .5f, top + bodyHeight * .48f),
                )
                drawCircle(
                    color = activeColor.copy(alpha = glow),
                    radius = bodyHeight * .10f,
                    center = Offset(left + bodyWidth * .5f, top + bodyHeight * .48f),
                )
            }
        }
    }
}

/**
 * Converts an observed switch value into a bounded illustrative glow target.
 *
 * Lighting definitions are Boolean on AAOS (`*_SWITCH`), while the generic vehicle UI model also
 * supports numeric values for future vendor properties. Prefer the Boolean when present so a
 * real ON callback cannot accidentally render as the neutral/off illustration.
 */
internal fun lightingGlowTarget(
    numericValue: Float?,
    booleanValue: Boolean? = null,
): Float =
    when {
        booleanValue != null -> if (booleanValue) 1f else .16f
        numericValue != null && numericValue.isFinite() && numericValue != 0f -> 1f
        else -> .16f
    }

private fun DrawScope.drawLight(
    center: Offset,
    glow: Float,
    color: Color,
) {
    drawCircle(color = color.copy(alpha = glow * .18f), radius = size.minDimension * .20f, center = center)
    drawCircle(color = color.copy(alpha = glow * .42f), radius = size.minDimension * .10f, center = center)
    drawCircle(color = color.copy(alpha = glow), radius = size.minDimension * .035f, center = center)
}
