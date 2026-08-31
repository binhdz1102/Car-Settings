package com.android.car.settings.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRenderState
import com.b231001.bmaterial.ccp.rotaryfocus.LocalIsInTouchMode

private val RotaryFocusBorderWidth = 3.dp

/** The app-wide visual contract for ordinary CCP focus. */
internal enum class RotaryFocusPresentation {
    None,
    BorderOnly,
}

internal fun rotaryFocusPresentation(isFocused: Boolean): RotaryFocusPresentation =
    if (isFocused) RotaryFocusPresentation.BorderOnly else RotaryFocusPresentation.None

/**
 * Renders a consistent B-Material/CCP focus ring around the content owned by a [FocusItem].
 *
 * The rotary library deliberately leaves focus visuals to the application. Keeping the ring at
 * this boundary means real Android View focus and the hostless fallback share one visual contract,
 * while touch mode remains free of a persistent ring.
 */
@Composable
internal fun RotaryFocusContent(
    state: FocusItemRenderState,
    content: @Composable () -> Unit,
) {
    val presentation = rotaryFocusPresentation(state.isFocused)
    Box(
        modifier =
            Modifier.rotaryFocusBorder(
                isFocused = presentation == RotaryFocusPresentation.BorderOnly,
                shape = MaterialTheme.shapes.large,
            ),
    ) {
        content()
    }
}

/** Adds the shared blue focus ring to a regular Compose focusable surface. */
@Composable
fun Modifier.rotaryFocusBorder(
    isFocused: Boolean,
    shape: Shape = MaterialTheme.shapes.medium,
): Modifier {
    val visibleFocus = isFocused && !LocalIsInTouchMode.current
    val targetColor =
        if (visibleFocus) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        }
    val borderColor by
        animateColorAsState(
            targetValue = targetColor,
            animationSpec = tween(VehicleMotionTokens.FOCUS_DURATION_MILLIS),
            label = "rotary-focus-ring",
        )
    // Draw after child content so opaque B-Material surfaces cannot cover the ring. Keep an
    // intentional clearance inside the component as well as half the stroke. The focus ring is
    // an indicator, not a layout boundary: drawing it on the outermost pixels made adjacent
    // cards/dialog surfaces look joined even when their FocusItem bounds had a valid gap.
    return drawWithContent {
        drawContent()
        if (borderColor.alpha > 0f) {
            val strokeWidth = RotaryFocusBorderWidth.toPx()
            val inset = strokeWidth / 2f + SettingsTokens.FocusRingClearance.toPx()
            val outlineSize =
                Size(
                    width = (size.width - (inset * 2f)).coerceAtLeast(0f),
                    height = (size.height - (inset * 2f)).coerceAtLeast(0f),
                )
            val outline = shape.createOutline(outlineSize, layoutDirection, this)
            translate(left = inset, top = inset) {
                drawShapeOutline(outline, borderColor, Stroke(width = strokeWidth))
            }
        }
    }
}

private fun DrawScope.drawShapeOutline(
    outline: Outline,
    color: Color,
    stroke: Stroke,
) {
    when (outline) {
        is Outline.Rectangle ->
            drawRect(
                color = color,
                topLeft = outline.rect.topLeft,
                size = outline.rect.size,
                style = stroke,
            )
        is Outline.Rounded ->
            drawPath(
                path = Path().apply { addRoundRect(outline.roundRect) },
                color = color,
                style = stroke,
            )
        is Outline.Generic -> drawPath(outline.path, color, style = stroke)
    }
}
