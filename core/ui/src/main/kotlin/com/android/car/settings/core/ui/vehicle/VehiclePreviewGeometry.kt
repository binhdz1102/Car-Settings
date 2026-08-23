package com.android.car.settings.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/** A point in the fixed 16:9 preview coordinate space. */
@Immutable
data class VehiclePreviewAnchor(
    val x: Float,
    val y: Float,
) {
    init {
        require(x.isFinite() && x in 0f..1f) { "x must be finite and between 0 and 1" }
        require(y.isFinite() && y in 0f..1f) { "y must be finite and between 0 and 1" }
    }
}

/** Converts a normalized anchor into pixels without allowing an overlay outside its viewport. */
fun VehiclePreviewAnchor.offsetIn(size: Size): Offset =
    Offset(
        x = size.width.coerceAtLeast(0f) * x.coerceIn(0f, 1f),
        y = size.height.coerceAtLeast(0f) * y.coerceIn(0f, 1f),
    )

/** Normalizes an observed value and clamps malformed vendor ranges to a safe fallback. */
fun normalizedVehiclePreviewValue(
    value: Float?,
    range: ClosedFloatingPointRange<Float>,
    fallback: Float = .5f,
): Float {
    if (value == null || !value.isFinite()) return fallback.coerceIn(0f, 1f)
    val span = range.endInclusive - range.start
    if (!span.isFinite() || span <= 0f) return fallback.coerceIn(0f, 1f)
    return ((value - range.start) / span).coerceIn(0f, 1f)
}
