package com.android.car.settings.feature.vehiclelighting.data

import com.android.car.settings.core.vehicle.VehicleFeatureController
import com.android.car.settings.core.vehicle.VehicleFeatureControllerFactory
import com.android.car.settings.core.vehicle.VehicleFeatureDefinition
import com.android.car.settings.core.vehicle.VehiclePropertySpec
import com.android.car.settings.feature.vehiclelighting.domain.VEHICLE_LIGHTING_DEFINITIONS
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class VehicleLightingRepository
    @Inject
    constructor(
        private val factory: VehicleFeatureControllerFactory,
    ) {
        fun createController(scope: CoroutineScope): VehicleFeatureController = factory.create(scope, vehicleLightingDefinitions())

        companion object {
            /**
             * Every AAOS `*_LIGHTS_SWITCH` property is an INT32 `VehicleLightSwitch` enum
             * (OFF/AUTOMATIC/ON/FLASH); a Boolean spec fails capability discovery with
             * TypeMismatch and disables the whole feature against a conformant VHAL.
             */
            fun vehicleLightingDefinitions(): List<VehicleFeatureDefinition> =
                VEHICLE_LIGHTING_DEFINITIONS.map {
                    VehicleFeatureDefinition(it.id.name, VehiclePropertySpec.int(it.propertyId), true)
                }
        }
    }
