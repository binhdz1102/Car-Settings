package com.android.car.settings.ui

import android.app.Application
import android.car.VehicleAreaSeat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.realcar.RealCarBatchOptions
import com.example.myapplication.core.realcar.RealCarConnectionState
import com.example.myapplication.core.realcar.RealCarProperty
import com.example.myapplication.core.realcar.RealCarPropertyManager
import com.example.myapplication.core.realcar.RealCarPropertyReadRequest
import com.example.myapplication.core.realcar.RealCarPropertyResult
import com.example.myapplication.core.realcar.RealCarPropertySubscription
import com.example.myapplication.core.realcar.RealCarPropertyValue
import com.example.myapplication.core.realcar.RealCarPropertyWriteRequest
import com.example.myapplication.core.realcar.RealVehiclePropertyIds
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.round

internal class CarPropertyDemoViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val manager = RealCarPropertyManager(application)
    private val _uiState = MutableStateFlow(CarPropertyDemoUiState())
    val uiState: StateFlow<CarPropertyDemoUiState> = _uiState.asStateFlow()

    private var connectJob: Job? = null

    init {
        observeConnection()
        observeLiveProperties()
        connect()
    }

    fun connect() {
        if (connectJob?.isActive == true) return
        connectJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, globalError = null) }
                when (val result = manager.connect()) {
                    is RealCarPropertyResult.Success -> loadAllProperties()
                    is RealCarPropertyResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                globalError = result.error.displayMessage(),
                            )
                        }
                    }
                }
            }
    }

    fun refresh() {
        viewModelScope.launch {
            loadAllProperties()
        }
    }

    fun decreaseDriverTemperature() {
        adjustTemperature(DRIVER_TEMPERATURE, direction = -1)
    }

    fun increaseDriverTemperature() {
        adjustTemperature(DRIVER_TEMPERATURE, direction = 1)
    }

    fun decreasePassengerTemperature() {
        adjustTemperature(PASSENGER_TEMPERATURE, direction = -1)
    }

    fun increasePassengerTemperature() {
        adjustTemperature(PASSENGER_TEMPERATURE, direction = 1)
    }

    fun decreaseFanSpeed() {
        val controls = _uiState.value.controls
        val value = controls.fanSpeed ?: return
        write(FAN_SPEED, (value - 1).coerceAtLeast(controls.fanMinimum), "HVAC_FAN_SPEED")
    }

    fun increaseFanSpeed() {
        val controls = _uiState.value.controls
        val value = controls.fanSpeed ?: return
        write(FAN_SPEED, (value + 1).coerceAtMost(controls.fanMaximum), "HVAC_FAN_SPEED")
    }

    fun toggleAc() {
        val currentValue = _uiState.value.controls.acEnabled ?: return
        write(AC_ENABLED, !currentValue, "HVAC_AC_ON")
    }

    fun runLargeBatchRead() {
        viewModelScope.launch {
            _uiState.update { it.copy(batchReadSummary = "Đang đọc 240 requests…") }
            val requests =
                List(LARGE_BATCH_SIZE) { index ->
                    PERFORMANCE_READS[index % PERFORMANCE_READS.size]
                }
            val result =
                manager.tryGetProperties(
                    requests = requests,
                    options =
                        RealCarBatchOptions(
                            timeoutMillis = 10_000,
                            maxRequestsPerChunk = 60,
                            maxConcurrentChunks = 4,
                        ),
                )
            _uiState.update {
                it.copy(
                    batchReadSummary =
                        "${result.successCount}/${result.items.size} thành công, " +
                            "${result.failureCount} lỗi • ${result.elapsedRealtimeMillis} ms",
                )
            }
        }
    }

    fun runConfirmedBatchWrite() {
        viewModelScope.launch {
            val controls = _uiState.value.controls
            val requests =
                buildList {
                    controls.driverTemperature.value?.let {
                        add(DRIVER_TEMPERATURE.writeRequest(it))
                    }
                    controls.passengerTemperature.value?.let {
                        add(PASSENGER_TEMPERATURE.writeRequest(it))
                    }
                    controls.fanSpeed?.let { add(FAN_SPEED.writeRequest(it)) }
                    controls.acEnabled?.let { add(AC_ENABLED.writeRequest(it)) }
                }
            if (requests.isEmpty()) return@launch

            _uiState.update { it.copy(batchWriteSummary = "Đang ghi và chờ VHAL xác nhận…") }
            val result = manager.trySetProperties(requests)
            _uiState.update {
                it.copy(
                    batchWriteSummary =
                        "${result.successCount}/${result.items.size} VHAL confirmed, " +
                            "${result.failureCount} lỗi • ${result.elapsedRealtimeMillis} ms",
                )
            }
        }
    }

    fun runErrorScenarios() {
        viewModelScope.launch {
            _uiState.update { it.copy(errorScenarioSummary = "Đang chạy các lỗi có kiểm soát…") }

            val readResult =
                manager.tryGetProperties(
                    listOf(
                        RealCarPropertyReadRequest(UNSUPPORTED_PROPERTY_ID),
                        RealCarPropertyReadRequest(
                            propertyId = RealVehiclePropertyIds.HVAC_TEMPERATURE_SET,
                            areaId = INVALID_AREA_ID,
                            expectedType = Float::class.javaObjectType,
                        ),
                        RealCarPropertyReadRequest(
                            propertyId = RealVehiclePropertyIds.HVAC_TEMPERATURE_SET,
                            areaId = VehicleAreaSeat.SEAT_ROW_1_LEFT,
                            expectedType = String::class.java,
                        ),
                    ),
                )
            val writeResult =
                manager.trySetProperties(
                    listOf(
                        RealCarPropertyWriteRequest(
                            propertyId = RealVehiclePropertyIds.INFO_MAKE,
                            value = "should-be-rejected",
                        ),
                        DRIVER_TEMPERATURE.writeRequest(99f),
                    ),
                )
            val errors =
                (readResult.items.map { it.result } + writeResult.items.map { it.result })
                    .mapNotNull { (it as? RealCarPropertyResult.Failure)?.error?.javaClass?.simpleName }

            _uiState.update {
                it.copy(
                    errorScenarioSummary =
                        "${errors.size}/5 lỗi được bắt an toàn: ${errors.distinct().joinToString()}",
                )
            }
            loadAllProperties()
        }
    }

    private fun observeConnection() {
        viewModelScope.launch {
            manager.connectionState.collect { connectionState ->
                _uiState.update { it.copy(connectionState = connectionState) }
            }
        }
    }

    private fun observeLiveProperties() {
        val subscriptions =
            listOf(
                DRIVER_TEMPERATURE,
                PASSENGER_TEMPERATURE,
                FAN_SPEED,
                AC_ENABLED,
                SPEED,
                GEAR,
            ).map { property ->
                RealCarPropertySubscription(
                    propertyId = property.propertyId,
                    areaId = property.areaId,
                    updateRateHz =
                        if (property.propertyId == RealVehiclePropertyIds.SPEED) {
                            RealCarPropertyManager.SENSOR_RATE_UI
                        } else {
                            RealCarPropertyManager.SENSOR_RATE_ONCHANGE
                        },
                )
            }

        viewModelScope.launch {
            manager.observeProperties(subscriptions).collect { result ->
                when (result) {
                    is RealCarPropertyResult.Success -> onLiveValue(result.value)
                    is RealCarPropertyResult.Failure -> {
                        addEvent("ERROR ${result.error.displayMessage()}")
                    }
                }
            }
        }
    }

    private suspend fun loadAllProperties() {
        _uiState.update { it.copy(isLoading = true, globalError = null) }
        val temperatureInfo = manager.getPropertyInfo(RealVehiclePropertyIds.HVAC_TEMPERATURE_SET)
        val fanInfo = manager.getPropertyInfo(RealVehiclePropertyIds.HVAC_FAN_SPEED)
        val result = manager.tryGetProperties(SAMPLE_DEFINITIONS.map { it.request })

        val samples =
            result.items.mapIndexed { index, item ->
                val definition = SAMPLE_DEFINITIONS[index]
                when (val propertyResult = item.result) {
                    is RealCarPropertyResult.Success ->
                        PropertySampleUiState(
                            label = definition.label,
                            type = definition.type,
                            propertyName = definition.propertyName,
                            value = formatValue(propertyResult.value.value),
                            status = SampleStatus.AVAILABLE,
                        )

                    is RealCarPropertyResult.Failure ->
                        PropertySampleUiState(
                            label = definition.label,
                            type = definition.type,
                            propertyName = definition.propertyName,
                            value =
                                propertyResult.staleValue?.value?.let(::formatValue)
                                    ?: propertyResult.error.javaClass.simpleName,
                            status = SampleStatus.ERROR,
                        )
                }
            }

        val controls = _uiState.value.controls
        val driver = result.valueOf(DRIVER_TEMPERATURE)
        val passenger = result.valueOf(PASSENGER_TEMPERATURE)
        val fanSpeed = result.valueOf(FAN_SPEED)
        val acEnabled = result.valueOf(AC_ENABLED)

        val temperatureMetadata = temperatureInfo.getOrNull()
        val fanMetadata = fanInfo.getOrNull()
        val temperatureArea = temperatureMetadata?.areas?.firstOrNull()
        val temperatureStep =
            temperatureMetadata
                ?.configArray
                ?.getOrNull(CELSIUS_INCREMENT_INDEX)
                ?.div(CONFIG_TEMPERATURE_SCALE)
                ?.takeIf { it > 0f }
                ?: controls.driverTemperature.step
        val fanArea = fanMetadata?.areas?.firstOrNull()

        _uiState.update { state ->
            state.copy(
                controls =
                    state.controls.copy(
                        driverTemperature =
                            state.controls.driverTemperature.copy(
                                value = driver as? Float,
                                minimum = temperatureArea?.minimumValue as? Float ?: 17.5f,
                                maximum = temperatureArea?.maximumValue as? Float ?: 32.5f,
                                step = temperatureStep,
                            ),
                        passengerTemperature =
                            state.controls.passengerTemperature.copy(
                                value = passenger as? Float,
                                minimum = temperatureArea?.minimumValue as? Float ?: 17.5f,
                                maximum = temperatureArea?.maximumValue as? Float ?: 32.5f,
                                step = temperatureStep,
                            ),
                        fanSpeed = fanSpeed as? Int,
                        fanMinimum = fanArea?.minimumValue as? Int ?: 1,
                        fanMaximum = fanArea?.maximumValue as? Int ?: 7,
                        acEnabled = acEnabled as? Boolean,
                    ),
                samples = samples,
                isLoading = false,
                globalError =
                    if (result.successCount == 0) {
                        "Không đọc được property nào / No property could be read"
                    } else {
                        null
                    },
            )
        }
    }

    private fun adjustTemperature(
        property: RealCarProperty<Float>,
        direction: Int,
    ) {
        val zone =
            if (property.areaId == DRIVER_TEMPERATURE.areaId) {
                _uiState.value.controls.driverTemperature
            } else {
                _uiState.value.controls.passengerTemperature
            }
        val currentValue = zone.value ?: return
        val target =
            (round((currentValue + direction * zone.step) / zone.step) * zone.step)
                .coerceIn(zone.minimum, zone.maximum)
        write(property, target, property.toString())
    }

    private fun <T : Any> write(
        property: RealCarProperty<T>,
        value: T,
        label: String,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(pendingWrites = it.pendingWrites + property.toString()) }
            when (val result = manager.write(property, value)) {
                is RealCarPropertyResult.Success -> addEvent("WRITE OK $label = ${formatValue(value)}")
                is RealCarPropertyResult.Failure -> {
                    addEvent("WRITE ERROR $label: ${result.error.displayMessage()}")
                    _uiState.update { it.copy(globalError = result.error.displayMessage()) }
                }
            }
            _uiState.update { it.copy(pendingWrites = it.pendingWrites - property.toString()) }
        }
    }

    private fun onLiveValue(value: RealCarPropertyValue) {
        _uiState.update { state ->
            val controls =
                when {
                    value.matches(DRIVER_TEMPERATURE) ->
                        state.controls.copy(
                            driverTemperature =
                                state.controls.driverTemperature.copy(value = value.asFloat()),
                        )

                    value.matches(PASSENGER_TEMPERATURE) ->
                        state.controls.copy(
                            passengerTemperature =
                                state.controls.passengerTemperature.copy(value = value.asFloat()),
                        )

                    value.matches(FAN_SPEED) -> state.controls.copy(fanSpeed = value.asInt())
                    value.matches(AC_ENABLED) -> state.controls.copy(acEnabled = value.asBoolean())
                    value.matches(SPEED) -> state.controls.copy(speedMetersPerSecond = value.asFloat())
                    value.matches(GEAR) -> state.controls.copy(gear = value.asInt())
                    else -> state.controls
                }
            state.copy(controls = controls)
        }
        addEvent(
            "EVENT ${RealVehiclePropertyIds.nameOf(value.propertyId)}[${value.areaId}] " +
                "= ${formatValue(value.value)}",
        )
    }

    private fun addEvent(message: String) {
        _uiState.update { state ->
            state.copy(eventLog = (listOf(message) + state.eventLog).take(MAX_EVENT_LOG_SIZE))
        }
    }

    override fun onCleared() {
        manager.close()
        super.onCleared()
    }

    companion object {
        private const val CELSIUS_INCREMENT_INDEX = 2
        private const val CONFIG_TEMPERATURE_SCALE = 10f
        private const val LARGE_BATCH_SIZE = 240
        private const val MAX_EVENT_LOG_SIZE = 12
        private const val INVALID_AREA_ID = 999
        private const val UNSUPPORTED_PROPERTY_ID = 0x12345678

        private val DRIVER_TEMPERATURE =
            RealCarProperty.float(
                RealVehiclePropertyIds.HVAC_TEMPERATURE_SET,
                VehicleAreaSeat.SEAT_ROW_1_LEFT,
            )
        private val PASSENGER_TEMPERATURE =
            RealCarProperty.float(
                RealVehiclePropertyIds.HVAC_TEMPERATURE_SET,
                VehicleAreaSeat.SEAT_ROW_1_RIGHT,
            )
        private val FAN_SPEED =
            RealCarProperty.int(
                RealVehiclePropertyIds.HVAC_FAN_SPEED,
                VehicleAreaSeat.SEAT_ROW_1_LEFT,
            )
        private val AC_ENABLED =
            RealCarProperty.boolean(
                RealVehiclePropertyIds.HVAC_AC_ON,
                VehicleAreaSeat.SEAT_ROW_1_LEFT,
            )
        private val SPEED = RealCarProperty.float(RealVehiclePropertyIds.SPEED)
        private val GEAR = RealCarProperty.int(RealVehiclePropertyIds.GEAR_SELECTION)

        private val PERFORMANCE_READS =
            listOf(
                DRIVER_TEMPERATURE.asReadRequest(),
                PASSENGER_TEMPERATURE.asReadRequest(),
                FAN_SPEED.asReadRequest(),
                AC_ENABLED.asReadRequest(),
                SPEED.asReadRequest(),
                GEAR.asReadRequest(),
                RealCarProperty.string(RealVehiclePropertyIds.INFO_MAKE).asReadRequest(),
                RealCarProperty.intArray(RealVehiclePropertyIds.INFO_FUEL_TYPE).asReadRequest(),
                RealCarProperty.longArray(RealVehiclePropertyIds.WHEEL_TICK).asReadRequest(),
            )

        private val SAMPLE_DEFINITIONS =
            listOf(
                SampleDefinition("Nhiệt độ tài xế", "Float", DRIVER_TEMPERATURE),
                SampleDefinition("Nhiệt độ hành khách", "Float", PASSENGER_TEMPERATURE),
                SampleDefinition("Tốc độ quạt", "Int", FAN_SPEED),
                SampleDefinition("Điều hòa A/C", "Boolean", AC_ENABLED),
                SampleDefinition("Tốc độ xe", "Float", SPEED),
                SampleDefinition("Số đang chọn", "Int", GEAR),
                SampleDefinition(
                    "Hãng xe",
                    "String",
                    RealCarProperty.string(RealVehiclePropertyIds.INFO_MAKE),
                ),
                SampleDefinition(
                    "Loại nhiên liệu",
                    "IntArray",
                    RealCarProperty.intArray(RealVehiclePropertyIds.INFO_FUEL_TYPE),
                ),
                SampleDefinition(
                    "Wheel ticks",
                    "LongArray",
                    RealCarProperty.longArray(RealVehiclePropertyIds.WHEEL_TICK),
                ),
                SampleDefinition(
                    "OBD live frame",
                    "Mixed",
                    RealCarProperty.mixed(RealVehiclePropertyIds.OBD2_LIVE_FRAME),
                ),
                SampleDefinition(
                    "Gợi ý nhiệt độ",
                    "FloatArray",
                    RealCarProperty.floatArray(
                        RealVehiclePropertyIds.HVAC_TEMPERATURE_VALUE_SUGGESTION,
                    ),
                ),
                SampleDefinition(
                    "Vendor payload",
                    "ByteArray",
                    RealCarProperty.byteArray(RealVehiclePropertyIds.EMULATOR_VENDOR_BYTES),
                ),
                SampleDefinition(
                    "VHAL heartbeat",
                    "Long",
                    RealCarProperty.long(RealVehiclePropertyIds.VHAL_HEARTBEAT),
                ),
            )
    }
}

internal data class CarPropertyDemoUiState(
    val connectionState: RealCarConnectionState = RealCarConnectionState.DISCONNECTED,
    val controls: CarControlsUiState = CarControlsUiState(),
    val samples: List<PropertySampleUiState> = emptyList(),
    val isLoading: Boolean = true,
    val pendingWrites: Set<String> = emptySet(),
    val globalError: String? = null,
    val batchReadSummary: String = "Chưa chạy / Not run",
    val batchWriteSummary: String = "Chưa chạy / Not run",
    val errorScenarioSummary: String = "Chưa chạy / Not run",
    val eventLog: List<String> = emptyList(),
)

internal data class CarControlsUiState(
    val driverTemperature: TemperatureZoneUiState = TemperatureZoneUiState(),
    val passengerTemperature: TemperatureZoneUiState = TemperatureZoneUiState(),
    val fanSpeed: Int? = null,
    val fanMinimum: Int = 1,
    val fanMaximum: Int = 7,
    val acEnabled: Boolean? = null,
    val speedMetersPerSecond: Float? = null,
    val gear: Int? = null,
)

internal data class TemperatureZoneUiState(
    val value: Float? = null,
    val minimum: Float = 17.5f,
    val maximum: Float = 32.5f,
    val step: Float = 0.5f,
)

internal data class PropertySampleUiState(
    val label: String,
    val type: String,
    val propertyName: String,
    val value: String,
    val status: SampleStatus,
)

internal enum class SampleStatus {
    AVAILABLE,
    ERROR,
}

private data class SampleDefinition(
    val label: String,
    val type: String,
    val property: RealCarProperty<*>,
) {
    val request: RealCarPropertyReadRequest = property.asReadRequest()
    val propertyName: String = RealVehiclePropertyIds.nameOf(property.propertyId)
}

private fun RealCarPropertyResult<*>.displayError(): String? = (this as? RealCarPropertyResult.Failure)?.error?.displayMessage()

private fun Throwable.displayMessage(): String = "${javaClass.simpleName}: ${message ?: "Unknown error"}"

private fun RealCarPropertyValue.matches(property: RealCarProperty<*>): Boolean =
    propertyId == property.propertyId && areaId == property.areaId

private fun <T : Any> com.example.myapplication.core.realcar.RealCarBatchResult<RealCarPropertyValue>.valueOf(
    property: RealCarProperty<T>,
): Any? =
    items
        .firstOrNull {
            it.propertyId == property.propertyId &&
                it.areaId == property.areaId
        }?.result
        ?.getOrNull()
        ?.value

private fun formatValue(value: Any?): String =
    when (value) {
        null -> "null"
        is Float -> String.format(Locale.US, "%.2f", value)
        is ByteArray -> value.contentToString().limit()
        is IntArray -> value.contentToString().limit()
        is LongArray -> value.contentToString().limit()
        is FloatArray -> value.contentToString().limit()
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { formatValue(it) }.limit()
        else -> value.toString().limit()
    }

private fun String.limit(maxLength: Int = 110): String = if (length <= maxLength) this else take(maxLength) + "…"
