@file:Suppress("MatchingDeclarationName")

package com.android.car.settings.core.ui

import androidx.annotation.DrawableRes
import com.android.car.settings.core.vehicle.VehicleFeatureAreaState
import com.android.car.settings.core.vehicle.VehicleFeatureControlState
import com.android.car.settings.core.vehicle.VehicleFeatureState
import com.android.car.settings.core.vehicle.VehiclePropertyAccess
import com.android.car.settings.core.vehicle.VehiclePropertyArea
import com.android.car.settings.core.vehicle.VehiclePropertyError
import com.android.car.settings.core.vehicle.VehiclePropertyStatus
import kotlin.math.roundToInt

data class VehicleControlUiMetadata(
    val key: String,
    val categoryKey: String = "",
    val section: String,
    val title: String,
    val summary: String,
    val info: String,
    val limitations: String,
    val dependencies: String,
    @param:DrawableRes val illustrationRes: Int? = null,
    val editor: VehicleEditorUiKind,
    val enumLabels: Map<Int, String> = emptyMap(),
    val emptyValueLabel: String = "",
    val valueLabel: (Any) -> String = Any::toString,
)

fun VehicleFeatureState.toUiControls(
    metadata: List<VehicleControlUiMetadata>,
    errorMessage: (VehiclePropertyError) -> String,
): List<VehicleControlUiModel> {
    val metadataByKey = metadata.associateBy { it.key }
    return controls
        .flatMap { control ->
            val presentation = metadataByKey[control.definition.key] ?: return@flatMap emptyList()
            control.resolvedAreas().map { area ->
                area.toUiModel(control, presentation, errorMessage)
            }
        }
}

private fun VehicleFeatureControlState.resolvedAreas(): List<VehicleFeatureAreaState> =
    areas.ifEmpty {
        listOf(
            VehicleFeatureAreaState(
                area = VehiclePropertyArea.GLOBAL,
                access = VehiclePropertyAccess.NONE,
                error = error,
            ),
        )
    }

private fun VehicleFeatureAreaState.toUiModel(
    control: VehicleFeatureControlState,
    presentation: VehicleControlUiMetadata,
    errorMessage: (VehiclePropertyError) -> String,
): VehicleControlUiModel {
    val numeric = (value as? Number)?.toFloat()
    val min = (minValue as? Number)?.toFloat()
    val max = (maxValue as? Number)?.toFloat()
    val enumValues = resolvedEnumValues(presentation.editor, min, max)
    return VehicleControlUiModel(
        key = control.definition.key,
        propertyId = control.definition.spec.propertyId,
        areaId = area.areaId,
        areaType = control.areaType,
        categoryKey = presentation.categoryKey.ifBlank { presentation.section },
        section = presentation.section,
        title = presentation.title,
        summary = presentation.summary,
        info = presentation.info,
        limitations = presentation.limitations,
        dependencies = presentation.dependencies,
        illustrationRes = presentation.illustrationRes,
        editor = presentation.editor,
        readable = access.canRead,
        writable = access.canWrite,
        supported = control.supported,
        available = isAvailable(control.supported, presentation.editor, min, max, enumValues),
        pending = pending,
        booleanValue = value as? Boolean,
        numericValue = numeric,
        valueLabel = value?.let(presentation.valueLabel) ?: presentation.emptyValueLabel,
        range = validRange(min, max),
        steps = rangeSteps(min, max),
        selectedEnumKey = numeric?.roundToInt()?.toString(),
        enumOptions = enumValues.toOptions(presentation.enumLabels),
        errorMessage = (error ?: control.error)?.let(errorMessage),
        requiresUnrestrictedUx = control.definition.requiresUnrestrictedUx,
    )
}

private fun VehicleFeatureAreaState.resolvedEnumValues(
    editor: VehicleEditorUiKind,
    min: Float?,
    max: Float?,
): List<Int> {
    val discovered = supportedEnumValues.mapNotNull { (it as? Number)?.toInt() }
    if (editor != VehicleEditorUiKind.ENUM || discovered.isNotEmpty()) return discovered
    return derivedEnumRange(min, max)
}

private fun isValidDerivedRange(
    min: Float,
    max: Float,
): Boolean = max >= min && max - min <= MAX_DERIVED_ENUM_RANGE

private fun derivedEnumRange(
    min: Float?,
    max: Float?,
): List<Int> {
    if (min == null || max == null) return emptyList()
    return if (isValidDerivedRange(min, max)) {
        (min.roundToInt()..max.roundToInt()).toList()
    } else {
        emptyList()
    }
}

private fun VehicleFeatureAreaState.isAvailable(
    supported: Boolean,
    editor: VehicleEditorUiKind,
    min: Float?,
    max: Float?,
    enumValues: List<Int>,
): Boolean {
    if (!supported || status != VehiclePropertyStatus.AVAILABLE || error != null) return false
    val writeOnlyEditor = access.canWrite && editor != VehicleEditorUiKind.STATUS
    val hasValidValue = value != null || writeOnlyEditor
    val hasValidEditorRange = editor != VehicleEditorUiKind.SLIDER || hasValidRange(min, max)
    val hasValidEnum = editor != VehicleEditorUiKind.ENUM || enumValues.isNotEmpty()
    return hasValidValue && hasValidEditorRange && hasValidEnum
}

private fun hasValidRange(
    min: Float?,
    max: Float?,
): Boolean = min != null && max != null && max > min

private fun validRange(
    min: Float?,
    max: Float?,
): ClosedFloatingPointRange<Float> {
    if (min == null || max == null || max <= min) return 0f..1f
    return min..max
}

private fun rangeSteps(
    min: Float?,
    max: Float?,
): Int = if (min != null && max != null) (max - min).roundToInt().minus(1).coerceAtLeast(0) else 0

private fun List<Int>.toOptions(labels: Map<Int, String>): List<VehicleEnumOption> =
    map { enumValue ->
        VehicleEnumOption(
            key = enumValue.toString(),
            label = labels[enumValue] ?: enumValue.toString(),
        )
    }

private const val MAX_DERIVED_ENUM_RANGE = 32f
