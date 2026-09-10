package com.android.car.settings.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Immutable
data class VehicleLayerSpec(
    val id: String,
    @param:DrawableRes val drawableRes: Int,
    val anchor: VehiclePreviewAnchor,
    val pivot: VehiclePreviewAnchor? = null,
)

@Immutable
data class VehicleSceneSpec(
    val id: String,
    val layers: List<VehicleLayerSpec>,
) {
    init {
        require(id.isNotBlank()) { "Vehicle scene ID must not be blank" }
        require(layers.map(VehicleLayerSpec::id).distinct().size == layers.size) {
            "Vehicle scene layers must have unique IDs: $id"
        }
    }
}

@Immutable
data class VehicleLayerTransform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val rotationDegrees: Float = 0f,
    val scale: Float = 1f,
    val alpha: Float = 1f,
)

/** Renders layer artwork in one shared Fit viewport; transforms are illustrative only. */
@Composable
fun VehicleScene(
    scene: VehicleSceneSpec,
    transforms: Map<String, VehicleLayerTransform> = emptyMap(),
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        scene.layers.forEach { layer ->
            val transform = transforms[layer.id] ?: VehicleLayerTransform()
            Image(
                painter = painterResource(layer.drawableRes),
                contentDescription = if (layer == scene.layers.firstOrNull()) contentDescription else null,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = transform.translationX
                            translationY = transform.translationY
                            rotationZ = transform.rotationDegrees
                            scaleX = transform.scale
                            scaleY = transform.scale
                            alpha = transform.alpha.coerceIn(0f, 1f)
                        },
            )
        }
    }
}
