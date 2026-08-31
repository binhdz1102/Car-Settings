package com.android.car.settings.feature.seatcontrol.data

import com.android.car.settings.core.vehicle.VehicleFeatureController
import com.android.car.settings.core.vehicle.VehicleFeatureControllerFactory
import com.android.car.settings.core.vehicle.VehicleFeatureDefinition
import com.android.car.settings.core.vehicle.VehiclePropertySpec
import com.android.car.settings.core.vehicle.VehiclePropertyValueType
import com.android.car.settings.feature.seatcontrol.domain.SEAT_CONTROL_DEFINITIONS
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class SeatControlRepository
    @Inject
    constructor(
        private val factory: VehicleFeatureControllerFactory,
    ) {
        fun createController(scope: CoroutineScope): VehicleFeatureController =
            factory.create(
                scope,
                SEAT_CONTROL_DEFINITIONS.map { definition ->
                    VehicleFeatureDefinition(
                        definition.id.name,
                        when (definition.valueType) {
                            VehiclePropertyValueType.BOOLEAN -> VehiclePropertySpec.boolean(definition.propertyId)
                            VehiclePropertyValueType.INT -> VehiclePropertySpec.int(definition.propertyId)
                            VehiclePropertyValueType.FLOAT -> VehiclePropertySpec.float(definition.propertyId)
                        },
                        definition.requiresUnrestrictedUx,
                    )
                },
            )
    }
