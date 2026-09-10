package com.android.car.settings.feature.vehiclelighting.presentation

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.android.car.settings.core.ui.VehicleControlUiModel
import com.android.car.settings.core.ui.VehicleIllustrationImage
import com.android.car.settings.core.ui.VehicleObservationStatus
import com.android.car.settings.core.ui.VehiclePreviewAnchor
import com.android.car.settings.core.ui.VehicleVisualPolicy
import com.android.car.settings.core.ui.offsetIn
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
    visualPolicy: VehicleVisualPolicy = VehicleVisualPolicy(true, true, true),
    modifier: Modifier = Modifier,
) {
    val selectedKey = selectedControl?.key
    val selectedAreaId = selectedControl?.areaId
    val selectedControlState =
        remember(controls, selectedKey, selectedAreaId) {
            controls
                .firstOrNull { it.key == selectedKey && it.areaId == selectedAreaId }
        ?.let {
            if (it.observedSnapshot.status == VehicleObservationStatus.CONFIRMED) {
                it.observedSnapshot.numericValue to it.observedSnapshot.booleanValue
            } else {
                null to null
            }
        }
        }
    val glow by
        animateFloatAsState(
            targetValue =
                lightingGlowTarget(
                    numericValue = selectedControlState?.first,
                    booleanValue = selectedControlState?.second,
                ),
            animationSpec = tween(durationMillis = if (visualPolicy.allowPreviewTransition) 240 else 0),
            label = "lighting-observed-glow",
        )
    val activeColor = MaterialTheme.colorScheme.tertiary
    val selectedFamilyKey = selectedControl?.key.orEmpty().uppercase()
    val visualizationDescription =
        stringResource(R.string.vehicle_lighting_visualization_content_description)

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
                illustrationRes = R.drawable.vehicle_preview_lighting,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val exterior =
                    selectedFamilyKey.isBlank() ||
                        listOf("HEAD", "FOG", "HAZARD", "TURN", "BEAM").any(selectedFamilyKey::contains)
                val interior =
                    listOf("CABIN", "READING", "FOOTWELL", "DOME", "AMBIENT").any(selectedFamilyKey::contains)
                if (exterior) {
                    drawLight(VehiclePreviewAnchor(.18f, .50f).offsetIn(size), glow, activeColor)
                    drawLight(VehiclePreviewAnchor(.47f, .49f).offsetIn(size), glow, activeColor)
                }
                if (interior || !exterior) {
                    val cabin = VehiclePreviewAnchor(.59f, .31f).offsetIn(size)
                    drawCircle(
                        color = activeColor.copy(alpha = glow * .2f),
                        radius = size.minDimension * .18f,
                        center = cabin,
                    )
                    drawCircle(
                        color = activeColor.copy(alpha = glow),
                        radius = size.minDimension * .025f,
                        center = cabin,
                    )
                }
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
