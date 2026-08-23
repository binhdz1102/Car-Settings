package com.android.car.settings.core.ui

import com.b231001.bmaterial.ccp.rotaryfocus.FocusItemRole

/**
 * Pure interaction contract for one vehicle control row.
 *
 * The composable renderer should only translate this contract to B-Material callbacks. Keeping
 * capability and Center-button decisions here prevents a value/pending recomposition from
 * re-running domain rules in the layout phase and gives the latest-request behaviour a small,
 * deterministic unit-test surface.
 */
internal data class VehicleControlInteraction(
    val canWrite: Boolean,
    val focusRole: FocusItemRole,
    val centerAction: VehicleCenterAction,
)

internal sealed interface VehicleCenterAction {
    data class Toggle(
        val value: Boolean,
    ) : VehicleCenterAction

    data class SelectEnum(
        val value: Int,
    ) : VehicleCenterAction

    data object OpenInfo : VehicleCenterAction
}

internal fun vehicleControlCanWrite(
    control: VehicleControlUiModel,
    connected: Boolean,
    restricted: Boolean,
): Boolean {
    val uxBlocked = restricted && control.requiresUnrestrictedUx
    return connected && control.supported && control.available && control.writable && !uxBlocked
}

internal fun buildVehicleControlInteraction(
    control: VehicleControlUiModel,
    connected: Boolean,
    restricted: Boolean,
): VehicleControlInteraction {
    val canWrite = vehicleControlCanWrite(control, connected, restricted)
    val focusRole =
        when (control.editor) {
            VehicleEditorUiKind.SWITCH -> FocusItemRole.Toggle
            VehicleEditorUiKind.SLIDER -> FocusItemRole.Adjustable
            VehicleEditorUiKind.ENUM,
            VehicleEditorUiKind.STATUS,
            -> FocusItemRole.Button
        }
    val centerAction =
        when (control.editor) {
            VehicleEditorUiKind.SWITCH ->
                if (canWrite && control.booleanValue != null) {
                    VehicleCenterAction.Toggle(!control.booleanValue)
                } else {
                    VehicleCenterAction.OpenInfo
                }
            VehicleEditorUiKind.ENUM ->
                if (canWrite) {
                    nextVehicleEnumOption(control.enumOptions, control.selectedEnumKey)
                        ?.key
                        ?.toIntOrNull()
                        ?.let(VehicleCenterAction::SelectEnum)
                        ?: VehicleCenterAction.OpenInfo
                } else {
                    VehicleCenterAction.OpenInfo
                }
            VehicleEditorUiKind.SLIDER,
            VehicleEditorUiKind.STATUS,
            -> VehicleCenterAction.OpenInfo
        }
    return VehicleControlInteraction(
        canWrite = canWrite,
        focusRole = focusRole,
        centerAction = centerAction,
    )
}
