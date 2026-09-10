package com.android.car.settings.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.android.car.settings.core.vehicle.VehiclePropertyAreaType

enum class VehicleEditorUiKind {
    SWITCH,
    SLIDER,
    ENUM,
    STATUS,
}

/** Visual treatment for a numeric control; the VHAL range remains the source of truth. */
enum class VehicleSliderUiKind {
    THERMAL,
    LEVEL,
    POSITION,
    OFFSET,
}

@Immutable
data class VehicleSliderUiSpec(
    val kind: VehicleSliderUiKind = VehicleSliderUiKind.LEVEL,
    val startLabel: String? = null,
    val endLabel: String? = null,
    val showTicks: Boolean = false,
    val showValueLabel: Boolean = true,
    val centerMarker: Float? = null,
)

/** Declares whether a preview is backed by observed vehicle data or is an explanatory demo. */
enum class VehicleVisualizationSource {
    LIVE_PROPERTY,
    ILLUSTRATION,
}

/** Describes what a visual is allowed to claim about the vehicle. */
enum class VehicleVisualMeaning {
    OBSERVED_STATE,
    CONFIRMED_SETTING,
    CONTEXT_ONLY,
    INSTRUCTIONAL,
}

/** Quality of the property value used by the preview; optimistic editor values are excluded. */
enum class VehicleObservationStatus {
    CONFIRMED,
    UNKNOWN,
    UNAVAILABLE,
    ERROR,
}

@Immutable
data class VehicleObservedSnapshot(
    val booleanValue: Boolean? = null,
    val numericValue: Float? = null,
    val enumValue: Int? = null,
    val status: VehicleObservationStatus = VehicleObservationStatus.UNKNOWN,
    val timestampNanos: Long? = null,
)

@Immutable
data class VehicleVisualBinding(
    val previewSceneId: String? = null,
    val guideSceneId: String? = null,
    val meaning: VehicleVisualMeaning = VehicleVisualMeaning.CONTEXT_ONLY,
    val companionKeys: List<String> = emptyList(),
)

@Immutable
data class VehicleZoneKey(
    val areaType: VehiclePropertyAreaType,
    val propertyFamily: String,
    val areaId: Int,
)

@Immutable
data class VehicleControlInstanceKey(
    val propertyId: Int,
    val zone: VehicleZoneKey,
)

@Immutable
data class VehicleVisualizationModel(
    @param:DrawableRes val artworkRes: Int?,
    val source: VehicleVisualizationSource,
    val zone: VehicleZoneKey,
    val available: Boolean,
    val booleanValue: Boolean? = null,
    val normalizedValue: Float? = null,
    val enumValue: Int? = null,
)

@Immutable
data class VehicleControlUiModel(
    val key: String,
    val propertyId: Int,
    val areaId: Int,
    val areaType: VehiclePropertyAreaType = VehiclePropertyAreaType.UNKNOWN,
    val section: String,
    val categoryKey: String = section,
    val title: String,
    val summary: String,
    val info: String,
    val limitations: String,
    val dependencies: String,
    @param:DrawableRes val illustrationRes: Int? = null,
    val editor: VehicleEditorUiKind,
    val readable: Boolean,
    val writable: Boolean,
    val supported: Boolean = true,
    val available: Boolean,
    val pending: Boolean,
    val booleanValue: Boolean? = null,
    val numericValue: Float? = null,
    val usesFloatSlider: Boolean = false,
    val valueLabel: String = "",
    val range: ClosedFloatingPointRange<Float> = 0f..1f,
    val steps: Int = 0,
    val sliderUiSpec: VehicleSliderUiSpec = VehicleSliderUiSpec(),
    val selectedEnumKey: String? = null,
    val enumOptions: List<VehicleEnumOption> = emptyList(),
    val errorMessage: String? = null,
    val requiresUnrestrictedUx: Boolean = false,
    val observedSnapshot: VehicleObservedSnapshot = VehicleObservedSnapshot(),
    val visualBinding: VehicleVisualBinding = VehicleVisualBinding(),
) {
    val zoneKey: VehicleZoneKey
        get() = VehicleZoneKey(areaType, categoryKey, areaId)

    val instanceKey: VehicleControlInstanceKey
        get() = VehicleControlInstanceKey(propertyId, zoneKey)
}
