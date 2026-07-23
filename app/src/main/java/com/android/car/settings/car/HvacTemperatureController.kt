package com.android.car.settings.car

import android.car.Car
import android.car.VehicleAreaSeat
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import java.io.Closeable

internal class HvacTemperatureController(
    context: Context,
    private val onTemperatureChanged: (areaId: Int, temperature: Float) -> Unit,
    private val onError: (message: String) -> Unit,
) : Closeable {
    private val car = Car.createCar(context.applicationContext)
    private val propertyManager =
        car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
    private val propertyConfig =
        checkNotNull(propertyManager.getCarPropertyConfig(PROPERTY_ID)) {
            "HVAC_TEMPERATURE_SET không được hỗ trợ trên xe này"
        }

    private val callback =
        object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                if (
                    value.propertyId != PROPERTY_ID ||
                    value.propertyStatus != CarPropertyValue.STATUS_AVAILABLE
                ) {
                    return
                }

                val temperature = value.value as? Float ?: return
                if (value.areaId in SUPPORTED_AREA_IDS) {
                    onTemperatureChanged(value.areaId, temperature)
                }
            }

            override fun onErrorEvent(
                propertyId: Int,
                areaId: Int,
            ) {
                if (propertyId == PROPERTY_ID && areaId in SUPPORTED_AREA_IDS) {
                    onError("Không thể cập nhật nhiệt độ cho vùng $areaId")
                }
            }
        }

    init {
        SUPPORTED_AREA_IDS.forEach { areaId ->
            check(propertyConfig.areaIds.contains(areaId)) {
                "HVAC_TEMPERATURE_SET không hỗ trợ areaId $areaId"
            }
        }
    }

    fun start(): Map<Int, TemperatureSnapshot> {
        SUPPORTED_AREA_IDS.forEach { areaId ->
            check(
                propertyManager.subscribePropertyEvents(
                    PROPERTY_ID,
                    areaId,
                    CarPropertyManager.SENSOR_RATE_ONCHANGE,
                    callback,
                ),
            ) {
                "Không thể đăng ký theo dõi HVAC_TEMPERATURE_SET cho areaId $areaId"
            }
        }

        return SUPPORTED_AREA_IDS.associateWith(::readTemperature)
    }

    fun setTemperature(
        areaId: Int,
        temperature: Float,
    ) {
        require(areaId in SUPPORTED_AREA_IDS) { "AreaId không hợp lệ: $areaId" }
        propertyManager.setFloatProperty(PROPERTY_ID, areaId, temperature)
    }

    private fun readTemperature(areaId: Int): TemperatureSnapshot {
        val minMaxValues =
            propertyManager.getMinMaxSupportedValue<Float>(PROPERTY_ID, areaId)
        val configValues = propertyConfig.configArray
        val step =
            configValues
                .getOrNull(CELSIUS_INCREMENT_INDEX)
                ?.div(CONFIG_TEMPERATURE_SCALE)
                ?.takeIf { it > 0f }
                ?: DEFAULT_TEMPERATURE_STEP

        return TemperatureSnapshot(
            value = propertyManager.getFloatProperty(PROPERTY_ID, areaId),
            minimum = minMaxValues?.minValue ?: DEFAULT_MINIMUM,
            maximum = minMaxValues?.maxValue ?: DEFAULT_MAXIMUM,
            step = step,
        )
    }

    override fun close() {
        propertyManager.unsubscribePropertyEvents(PROPERTY_ID, callback)
        car.disconnect()
    }

    companion object {
        const val DRIVER_AREA_ID = VehicleAreaSeat.SEAT_ROW_1_LEFT
        const val PASSENGER_AREA_ID = VehicleAreaSeat.SEAT_ROW_1_RIGHT

        private const val PROPERTY_ID = VehiclePropertyIds.HVAC_TEMPERATURE_SET
        private const val CELSIUS_INCREMENT_INDEX = 2
        private const val CONFIG_TEMPERATURE_SCALE = 10f
        private const val DEFAULT_TEMPERATURE_STEP = 0.5f
        private const val DEFAULT_MINIMUM = 17.5f
        private const val DEFAULT_MAXIMUM = 32.5f

        private val SUPPORTED_AREA_IDS = setOf(DRIVER_AREA_ID, PASSENGER_AREA_ID)
    }
}

internal data class TemperatureSnapshot(
    val value: Float,
    val minimum: Float,
    val maximum: Float,
    val step: Float,
)
