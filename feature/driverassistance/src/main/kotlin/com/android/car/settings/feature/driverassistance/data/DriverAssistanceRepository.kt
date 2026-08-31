package com.android.car.settings.feature.driverassistance.data

import com.android.car.settings.core.vehicle.VehicleFeatureController
import com.android.car.settings.core.vehicle.VehicleFeatureControllerFactory
import com.android.car.settings.core.vehicle.VehicleFeatureDefinition
import com.android.car.settings.core.vehicle.VehiclePropertySpec
import com.android.car.settings.core.vehicle.VehiclePropertyValueType
import com.android.car.settings.feature.driverassistance.domain.DRIVER_ASSISTANCE_DEFINITIONS
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class DriverAssistanceRepository
    @Inject
    constructor(
        private val factory: VehicleFeatureControllerFactory,
    ) {
        fun createController(scope: CoroutineScope): VehicleFeatureController =
            factory.create(
                scope,
                DRIVER_ASSISTANCE_DEFINITIONS.map { definition ->
                    VehicleFeatureDefinition(
                        key = definition.id.name,
                        spec =
                            when (definition.valueType) {
                                VehiclePropertyValueType.BOOLEAN ->
                                    VehiclePropertySpec.boolean(definition.propertyId)
                                VehiclePropertyValueType.INT ->
                                    VehiclePropertySpec.int(definition.propertyId)
                                VehiclePropertyValueType.FLOAT ->
                                    VehiclePropertySpec.float(definition.propertyId)
                            },
                        requiresUnrestrictedUx = definition.uxRestricted,
                    )
                },
            )
    }
