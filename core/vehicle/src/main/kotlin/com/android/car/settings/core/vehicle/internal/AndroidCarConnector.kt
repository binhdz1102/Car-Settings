package com.android.car.settings.core.vehicle.internal

import android.car.VehicleAreaType
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyConfig
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarInternalErrorException
import android.car.hardware.property.CarPropertyManager
import android.car.hardware.property.PropertyNotAvailableAndRetryException
import android.car.hardware.property.PropertyNotAvailableException
import android.util.Log
import com.android.car.settings.core.vehicle.CarServiceProvider
import com.android.car.settings.core.vehicle.CarServiceRegistration
import com.android.car.settings.core.vehicle.VehiclePropertyAccess
import com.android.car.settings.core.vehicle.VehiclePropertyAreaType
import com.android.car.settings.core.vehicle.VehiclePropertyChangeMode
import com.android.car.settings.core.vehicle.VehiclePropertyStatus
import com.android.car.settings.core.vehicle.VehiclePropertyValueType
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AndroidCarConnector
    @Inject
    constructor(
        private val carServiceProvider: CarServiceProvider,
    ) : PlatformCarConnector {
        override fun open(listener: PlatformCarConnector.Listener): PlatformCarRegistration {
            val closed = AtomicBoolean(false)
            val registration: CarServiceRegistration =
                carServiceProvider.register { connectedCar, ready ->
                    if (!closed.get()) {
                        if (!ready) {
                            listener.onDisconnected("CarService lifecycle reported not ready")
                        } else {
                            val propertyManager =
                                connectedCar.getCarManager(CarPropertyManager::class.java)
                            if (propertyManager == null) {
                                listener.onConnectionFailed("CarPropertyManager is unavailable")
                            } else {
                                val uxManager =
                                    connectedCar.getCarManager(CarUxRestrictionsManager::class.java)
                                listener.onConnected(
                                    PlatformVehicleSession(
                                        properties = AndroidVehiclePropertyGateway(propertyManager),
                                        uxRestrictions = uxManager?.let(::AndroidVehicleUxGateway),
                                    ),
                                )
                            }
                        }
                    }
                }
            return PlatformCarRegistration {
                if (closed.compareAndSet(false, true)) {
                    registration.close()
                }
            }
        }
    }

private class AndroidVehiclePropertyGateway(
    private val manager: CarPropertyManager,
) : PlatformVehiclePropertyGateway {
    override fun getConfig(propertyId: Int): PlatformPropertyConfig? =
        platformCall {
            manager.getCarPropertyConfig(propertyId)?.toPlatformConfig(manager)
        }

    override fun get(
        valueType: VehiclePropertyValueType,
        propertyId: Int,
        areaId: Int,
    ): PlatformPropertyValue =
        platformCall {
            val value: CarPropertyValue<*>? =
                when (valueType) {
                    VehiclePropertyValueType.BOOLEAN ->
                        manager.getProperty(Boolean::class.javaObjectType, propertyId, areaId)
                    VehiclePropertyValueType.INT ->
                        manager.getProperty(Int::class.javaObjectType, propertyId, areaId)
                    VehiclePropertyValueType.FLOAT ->
                        manager.getProperty(Float::class.javaObjectType, propertyId, areaId)
                }
            value?.toPlatformValue()
                ?: throw PlatformVehicleException.Unavailable(
                    retryable = true,
                    message = "CarPropertyManager returned no value",
                )
        }

    override fun set(
        valueType: VehiclePropertyValueType,
        propertyId: Int,
        areaId: Int,
        value: Any,
    ) {
        platformCall {
            when (valueType) {
                VehiclePropertyValueType.BOOLEAN ->
                    manager.setBooleanProperty(propertyId, areaId, value as Boolean)
                VehiclePropertyValueType.INT ->
                    manager.setIntProperty(propertyId, areaId, value as Int)
                VehiclePropertyValueType.FLOAT ->
                    manager.setFloatProperty(propertyId, areaId, value as Float)
            }
        }
    }

    override fun subscribe(
        propertyId: Int,
        areaIds: Set<Int>,
        updateRateHz: Float,
        callback: (PlatformPropertyEvent) -> Unit,
    ): PlatformPropertySubscription {
        val closed = AtomicBoolean(false)
        val platformCallback =
            object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    if (!closed.get()) callback(PlatformPropertyEvent.Changed(value.toPlatformValue()))
                }

                override fun onErrorEvent(
                    propertyId: Int,
                    areaId: Int,
                ) {
                    onErrorEvent(
                        propertyId,
                        areaId,
                        CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN,
                    )
                }

                override fun onErrorEvent(
                    propertyId: Int,
                    areaId: Int,
                    errorCode: Int,
                ) {
                    if (!closed.get()) {
                        callback(PlatformPropertyEvent.Error(propertyId, areaId, errorCode))
                    }
                }
            }

        val subscribed =
            platformCall {
                if (areaIds.isEmpty()) {
                    manager.subscribePropertyEvents(propertyId, updateRateHz, platformCallback)
                } else {
                    areaIds.all { areaId ->
                        manager.subscribePropertyEvents(
                            propertyId,
                            areaId,
                            updateRateHz,
                            platformCallback,
                        )
                    }
                }
            }
        if (!subscribed) {
            try {
                manager.unsubscribePropertyEvents(platformCallback)
            } catch (_: RuntimeException) {
                // Best-effort cleanup after a partial multi-area registration.
            }
            throw PlatformVehicleException.Service(
                "CarPropertyManager rejected the property subscription",
            )
        }

        return PlatformPropertySubscription {
            if (closed.compareAndSet(false, true)) {
                try {
                    manager.unsubscribePropertyEvents(platformCallback)
                } catch (error: RuntimeException) {
                    Log.w(TAG, "Unable to unsubscribe vehicle property callback", error)
                }
            }
        }
    }
}

private inline fun <T> platformCall(block: () -> T): T =
    try {
        block()
    } catch (error: PropertyNotAvailableAndRetryException) {
        throw PlatformVehicleException.Unavailable(
            retryable = true,
            message = error.message ?: "Vehicle property is temporarily unavailable",
            cause = error,
        )
    } catch (error: PropertyNotAvailableException) {
        throw PlatformVehicleException.Unavailable(
            retryable = false,
            detailCode = error.detailedErrorCode,
            message = error.message ?: "Vehicle property is unavailable",
            cause = error,
        )
    } catch (error: SecurityException) {
        throw PlatformVehicleException.PermissionDenied(
            message = error.message ?: "Vehicle property permission was denied",
            cause = error,
        )
    } catch (error: IllegalArgumentException) {
        throw PlatformVehicleException.InvalidArgument(
            message = error.message ?: "Vehicle property argument is invalid",
            cause = error,
        )
    } catch (error: CarInternalErrorException) {
        throw PlatformVehicleException.Service(
            message = error.message ?: "CarService reported an internal property error",
            cause = error,
        )
    } catch (error: IllegalStateException) {
        throw PlatformVehicleException.Service(
            message = error.message ?: "CarService property operation failed",
            cause = error,
        )
    }

private class AndroidVehicleUxGateway(
    private val manager: CarUxRestrictionsManager,
) : PlatformVehicleUxGateway {
    override fun current(): PlatformUxRestrictions? =
        manager.currentCarUxRestrictions?.let {
            PlatformUxRestrictions(
                requiresDistractionOptimization = it.isRequiresDistractionOptimization,
                activeRestrictions = it.activeRestrictions,
            )
        }

    override fun subscribe(callback: (PlatformUxRestrictions) -> Unit): PlatformUxSubscription {
        val closed = AtomicBoolean(false)
        val listener =
            CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
                if (!closed.get()) {
                    callback(
                        PlatformUxRestrictions(
                            requiresDistractionOptimization =
                                restrictions.isRequiresDistractionOptimization,
                            activeRestrictions = restrictions.activeRestrictions,
                        ),
                    )
                }
            }
        manager.registerListener(listener)
        return PlatformUxSubscription {
            if (closed.compareAndSet(false, true)) {
                try {
                    manager.unregisterListener()
                } catch (error: RuntimeException) {
                    Log.w(TAG, "Unable to unregister Car UX restrictions listener", error)
                }
            }
        }
    }
}

private fun CarPropertyConfig<*>.toPlatformConfig(manager: CarPropertyManager): PlatformPropertyConfig {
    val propertyValueType = propertyType.toVehiclePropertyType()
    return PlatformPropertyConfig(
        propertyId = propertyId,
        valueType = propertyValueType,
        platformTypeName = propertyType.name,
        access = access.toVehicleAccess(),
        changeMode = changeMode.toVehicleChangeMode(),
        areaType = areaType.toVehicleAreaType(),
        areas =
            areaIdConfigs.map { areaConfig ->
                val minMax =
                    if (areaConfig.hasMinSupportedValue() || areaConfig.hasMaxSupportedValue()) {
                        manager.getMinMaxSupportedValue<Any>(propertyId, areaConfig.areaId)
                    } else {
                        null
                    }
                PlatformAreaConfig(
                    areaId = areaConfig.areaId,
                    access = areaConfig.access.toVehicleAccess(),
                    minValue = minMax?.minValue,
                    maxValue = minMax?.maxValue,
                    supportedEnumValues =
                        if (areaConfig.hasSupportedValuesList()) {
                            manager
                                .getSupportedValuesList<Any>(propertyId, areaConfig.areaId)
                                .orEmpty()
                        } else {
                            emptyList()
                        },
                )
            },
        minSampleRateHz = minSampleRate,
        maxSampleRateHz = maxSampleRate,
        configArray = configArray.toList(),
    )
}

private fun CarPropertyValue<*>.toPlatformValue() =
    PlatformPropertyValue(
        propertyId = propertyId,
        areaId = areaId,
        status =
            when (propertyStatus) {
                CarPropertyValue.STATUS_AVAILABLE -> VehiclePropertyStatus.AVAILABLE
                CarPropertyValue.STATUS_UNAVAILABLE -> VehiclePropertyStatus.UNAVAILABLE
                else -> VehiclePropertyStatus.ERROR
            },
        timestampNanos = timestamp,
        value = value,
    )

private fun Class<*>.toVehiclePropertyType(): VehiclePropertyValueType? =
    when (this) {
        Boolean::class.javaObjectType -> VehiclePropertyValueType.BOOLEAN
        Int::class.javaObjectType -> VehiclePropertyValueType.INT
        Float::class.javaObjectType -> VehiclePropertyValueType.FLOAT
        else -> null
    }

private fun Int.toVehicleAccess(): VehiclePropertyAccess =
    when (this) {
        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ -> VehiclePropertyAccess.READ
        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE -> VehiclePropertyAccess.WRITE
        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE -> VehiclePropertyAccess.READ_WRITE
        else -> VehiclePropertyAccess.NONE
    }

private fun Int.toVehicleChangeMode(): VehiclePropertyChangeMode =
    when (this) {
        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC -> VehiclePropertyChangeMode.STATIC
        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE -> VehiclePropertyChangeMode.ON_CHANGE
        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS ->
            VehiclePropertyChangeMode.CONTINUOUS
        else -> VehiclePropertyChangeMode.UNKNOWN
    }

private fun Int.toVehicleAreaType(): VehiclePropertyAreaType =
    when (this) {
        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL -> VehiclePropertyAreaType.GLOBAL
        VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW -> VehiclePropertyAreaType.WINDOW
        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR -> VehiclePropertyAreaType.MIRROR
        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT -> VehiclePropertyAreaType.SEAT
        VehicleAreaType.VEHICLE_AREA_TYPE_DOOR -> VehiclePropertyAreaType.DOOR
        VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL -> VehiclePropertyAreaType.WHEEL
        VehicleAreaType.VEHICLE_AREA_TYPE_VENDOR -> VehiclePropertyAreaType.VENDOR
        else -> VehiclePropertyAreaType.UNKNOWN
    }

private const val TAG = "VehicleCore"
