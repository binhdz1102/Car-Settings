package com.android.car.settings.core.ui

import androidx.compose.runtime.Immutable
import com.android.car.settings.core.vehicle.VehiclePropertyAreaType

/** A presentation-only option used by [VehicleEnumRow]. */
@Immutable
data class VehicleEnumOption(
    val key: String,
    val label: String,
    val contentDescription: String = label,
    val enabled: Boolean = true,
)

/** A presentation-only vehicle area used by [VehicleZoneSelector]. */
@Immutable
data class VehicleZoneOption(
    val areaId: Int,
    val label: String,
    val contentDescription: String = label,
    val enabled: Boolean = true,
    val propertyFamily: String = "",
    val areaType: VehiclePropertyAreaType = VehiclePropertyAreaType.UNKNOWN,
) {
    val key: VehicleZoneKey
        get() = VehicleZoneKey(areaType, propertyFamily, areaId)
}

/** An additional, caller-owned section displayed by [VehicleInfoBottomSheet]. */
@Immutable
data class VehicleInfoSection(
    val title: String,
    val body: String,
)

/**
 * A presentation-only marker placed over [VehicleTopView]. Fractions use the image's
 * left-to-right and top-to-bottom coordinate space.
 */
@Immutable
data class VehicleTopViewZone(
    val areaId: Int,
    val label: String,
    val shortLabel: String,
    val contentDescription: String = label,
    val horizontalFraction: Float,
    val verticalFraction: Float,
    val enabled: Boolean = true,
) {
    init {
        require(horizontalFraction.isFinite() && horizontalFraction in 0f..1f) {
            "horizontalFraction must be finite and between 0 and 1"
        }
        require(verticalFraction.isFinite() && verticalFraction in 0f..1f) {
            "verticalFraction must be finite and between 0 and 1"
        }
    }
}

/** Visual emphasis only; it deliberately has no dependency on vehicle-domain status types. */
enum class VehicleStatusTone {
    Neutral,
    Positive,
    Warning,
    Error,
    Unavailable,
}

internal fun vehicleSliderFraction(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
): Float {
    val length = valueRange.endInclusive - valueRange.start
    if (!length.isFinite() || length <= 0f || !value.isFinite()) return 0f
    return ((value - valueRange.start) / length).coerceIn(0f, 1f)
}

/**
 * Resolves the value rendered by a slider while a write is travelling through CarService/VHAL.
 *
 * Vehicle callbacks are authoritative once they acknowledge the submitted target, but an older
 * callback must never repaint the thumb between the user's drag and that acknowledgement.  The
 * small pure reducer keeps touch and rotary sliders on the same no-snap-back contract and makes
 * the race-sensitive behaviour unit-testable without a Compose clock.
 */
internal data class VehicleSliderSyncResult(
    val value: Float,
    val clearSubmittedValue: Boolean,
)

internal fun resolveVehicleSliderValue(
    externalValue: Float,
    currentValue: Float,
    interactionActive: Boolean,
    submittedValue: Float?,
    pending: Boolean,
    hasError: Boolean,
): VehicleSliderSyncResult =
    when {
        interactionActive ->
            VehicleSliderSyncResult(value = currentValue, clearSubmittedValue = false)
        hasError -> VehicleSliderSyncResult(value = externalValue, clearSubmittedValue = true)
        pending && submittedValue != null ->
            VehicleSliderSyncResult(value = submittedValue, clearSubmittedValue = false)
        submittedValue != null && kotlin.math.abs(externalValue - submittedValue) <= 0.001f ->
            VehicleSliderSyncResult(value = externalValue, clearSubmittedValue = true)
        submittedValue != null ->
            VehicleSliderSyncResult(value = submittedValue, clearSubmittedValue = false)
        else -> VehicleSliderSyncResult(value = externalValue, clearSubmittedValue = false)
    }

/**
 * Returns one deterministic detent step for a rotary slider.
 *
 * Keeping this calculation outside the composable prevents a detent frame from allocating a
 * range calculation and gives touch/rotary implementations one shared contract.  A declared
 * number of intermediate steps wins; otherwise the range is bounded to at most twenty useful
 * detents and never produces a zero/NaN step.
 */
internal fun vehicleSliderStep(
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
): Float {
    val span = valueRange.endInclusive - valueRange.start
    if (!span.isFinite() || span <= 0f) return 1f
    return when {
        steps > 0 -> (span / (steps.toFloat() + 1f)).coerceAtLeast(Float.MIN_VALUE)
        span <= 20f -> 1f
        else -> (span / 20f).coerceAtLeast(1f)
    }
}

/** Coalescing window used by the rotary slider writer to avoid one VHAL request per detent. */
internal const val VEHICLE_ROTARY_WRITE_COALESCE_MILLIS = 80L

/** Applies a rotary burst without allowing invalid values to enter the rendered slider state. */
internal fun nextVehicleSliderValue(
    currentValue: Float,
    detents: Float,
    step: Float,
    valueRange: ClosedFloatingPointRange<Float>,
): Float {
    if (!currentValue.isFinite() || !step.isFinite()) return valueRange.start
    return (currentValue + detents * step)
        .takeIf(Float::isFinite)
        ?.coerceIn(valueRange.start, valueRange.endInclusive)
        ?: valueRange.start
}

/**
 * Selects the next enabled enum option without depending on Compose state or a renderer.
 * Unknown/disabled current values safely fall back to the first enabled option.
 */
internal fun nextVehicleEnumOption(
    options: List<VehicleEnumOption>,
    selectedKey: String?,
): VehicleEnumOption? {
    val enabled = options.filter(VehicleEnumOption::enabled)
    if (enabled.isEmpty()) return null
    val currentIndex = enabled.indexOfFirst { it.key == selectedKey }
    return enabled[(currentIndex + 1).mod(enabled.size)]
}
