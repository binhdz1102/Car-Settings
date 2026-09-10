package com.android.car.settings.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

internal fun vehicleRouteCompositionKey(categoryKey: String?): String = categoryKey ?: "overview"

/**
 * Single-tree route transition for vehicle settings.
 *
 * The outgoing destination is removed before the incoming FocusAreas are registered, so this is
 * deliberately a graphics-layer transition rather than AnimatedContent (which composes both
 * targets briefly and can make real B-Material FocusAreas overlap). Layout bounds remain stable;
 * only alpha/translation are animated.
 */
@Composable
internal fun AutomotiveRouteMotion(
    routeKey: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val compositionKey = vehicleRouteCompositionKey(routeKey)
    val progress = remember { Animatable(1f) }
    // A graphicsLayer kept attached after the transition settles breaks pointer hit-testing
    // for the interop AndroidView children (FocusItem/FocusArea hosts) rendered inside this
    // box, leaving the whole vehicle screen deaf to touch. Attach the layer only while the
    // transition is actually running.
    var animating by remember(routeKey) { mutableStateOf(true) }
    LaunchedEffect(routeKey) {
        if (progress.value != 1f) progress.snapTo(1f)
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(VehicleMotionTokens.CONTENT_ENTER_DURATION_MILLIS),
        )
        animating = false
    }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .then(
                    if (animating) {
                        Modifier.graphicsLayer {
                            alpha = progress.value
                            translationX = (1f - progress.value) * 24f
                        }
                    } else {
                        Modifier
                    },
                ),
    ) {
        key(compositionKey) {
            content()
        }
    }
}
