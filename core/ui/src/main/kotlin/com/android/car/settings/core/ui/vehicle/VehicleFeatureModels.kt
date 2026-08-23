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

/** Declares whether a preview is backed by observed vehicle data or is an explanatory demo. */
enum class VehicleVisualizationSource {
    LIVE_PROPERTY,
    ILLUSTRATION,
}

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
    val selectedEnumKey: String? = null,
    val enumOptions: List<VehicleEnumOption> = emptyList(),
    val errorMessage: String? = null,
    val requiresUnrestrictedUx: Boolean = false,
) {
    val zoneKey: VehicleZoneKey
        get() = VehicleZoneKey(areaType, categoryKey, areaId)

    val instanceKey: VehicleControlInstanceKey
        get() = VehicleControlInstanceKey(propertyId, zoneKey)
}
