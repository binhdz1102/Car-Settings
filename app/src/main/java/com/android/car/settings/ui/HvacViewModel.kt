package com.android.car.settings.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.car.HvacTemperatureController
import com.android.car.settings.car.TemperatureSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.round

internal class HvacViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(HvacUiState())
    val uiState: StateFlow<HvacUiState> = _uiState.asStateFlow()

    private val controller = AtomicReference<HvacTemperatureController?>()
    private var connectionJob: Job? = null

    init {
        connect()
    }

    fun connect() {
        if (connectionJob?.isActive == true) return

        connectionJob =
            viewModelScope.launch(Dispatchers.IO) {
                controller.getAndSet(null)?.close()
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                runCatching {
                    HvacTemperatureController(
                        context = getApplication(),
                        onTemperatureChanged = ::onTemperatureChanged,
                        onError = ::showError,
                    )
                }.onSuccess { newController ->
                    controller.set(newController)
                    runCatching(newController::start)
                        .onSuccess(::onInitialTemperaturesLoaded)
                        .onFailure { error ->
                            controller.compareAndSet(newController, null)
                            newController.close()
                            showError(error.message ?: "Không thể đọc nhiệt độ điều hòa")
                        }
                }.onFailure { error ->
                    showError(error.message ?: "Không thể kết nối CarPropertyManager")
                }
            }
    }

    fun decreaseDriverTemperature() {
        adjustTemperature(HvacTemperatureController.DRIVER_AREA_ID, direction = -1)
    }

    fun increaseDriverTemperature() {
        adjustTemperature(HvacTemperatureController.DRIVER_AREA_ID, direction = 1)
    }

    fun decreasePassengerTemperature() {
        adjustTemperature(HvacTemperatureController.PASSENGER_AREA_ID, direction = -1)
    }

    fun increasePassengerTemperature() {
        adjustTemperature(HvacTemperatureController.PASSENGER_AREA_ID, direction = 1)
    }

    private fun adjustTemperature(
        areaId: Int,
        direction: Int,
    ) {
        var previousValue: Float? = null
        var targetValue: Float? = null

        _uiState.update { state ->
            val zone = state.zoneFor(areaId)
            val currentValue = zone.value ?: return@update state
            val target =
                roundToStep(
                    value = currentValue + direction * zone.step,
                    step = zone.step,
                ).coerceIn(zone.minimum, zone.maximum)

            if (target == currentValue) return@update state

            previousValue = currentValue
            targetValue = target
            state.withZone(areaId, zone.copy(value = target, isUpdating = true))
        }

        val valueToWrite = targetValue ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                checkNotNull(controller.get()) { "Chưa kết nối với CarPropertyManager" }
                    .setTemperature(areaId, valueToWrite)
            }.onFailure { error ->
                _uiState.update { state ->
                    val zone = state.zoneFor(areaId)
                    state
                        .withZone(areaId, zone.copy(value = previousValue, isUpdating = false))
                        .copy(errorMessage = error.message ?: "Không thể đặt nhiệt độ")
                }
            }
        }
    }

    private fun onInitialTemperaturesLoaded(temperatures: Map<Int, TemperatureSnapshot>) {
        _uiState.update { state ->
            state.copy(
                driver =
                    temperatures
                        .getValue(HvacTemperatureController.DRIVER_AREA_ID)
                        .toUiState(),
                passenger =
                    temperatures
                        .getValue(HvacTemperatureController.PASSENGER_AREA_ID)
                        .toUiState(),
                isLoading = false,
                errorMessage = null,
            )
        }
    }

    private fun onTemperatureChanged(
        areaId: Int,
        temperature: Float,
    ) {
        _uiState.update { state ->
            val zone = state.zoneFor(areaId)
            state
                .withZone(
                    areaId,
                    zone.copy(
                        value = temperature,
                        isAvailable = true,
                        isUpdating = false,
                    ),
                ).copy(isLoading = false, errorMessage = null)
        }
    }

    private fun showError(message: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = message,
            )
        }
    }

    override fun onCleared() {
        connectionJob?.cancel()
        controller.getAndSet(null)?.close()
        super.onCleared()
    }
}

internal data class HvacUiState(
    val driver: TemperatureZoneUiState = TemperatureZoneUiState(),
    val passenger: TemperatureZoneUiState = TemperatureZoneUiState(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    fun zoneFor(areaId: Int): TemperatureZoneUiState =
        when (areaId) {
            HvacTemperatureController.DRIVER_AREA_ID -> driver
            HvacTemperatureController.PASSENGER_AREA_ID -> passenger
            else -> error("AreaId không được hỗ trợ: $areaId")
        }

    fun withZone(
        areaId: Int,
        zone: TemperatureZoneUiState,
    ): HvacUiState =
        when (areaId) {
            HvacTemperatureController.DRIVER_AREA_ID -> copy(driver = zone)
            HvacTemperatureController.PASSENGER_AREA_ID -> copy(passenger = zone)
            else -> this
        }
}

internal data class TemperatureZoneUiState(
    val value: Float? = null,
    val minimum: Float = 17.5f,
    val maximum: Float = 32.5f,
    val step: Float = 0.5f,
    val isAvailable: Boolean = false,
    val isUpdating: Boolean = false,
)

private fun TemperatureSnapshot.toUiState() =
    TemperatureZoneUiState(
        value = value,
        minimum = minimum,
        maximum = maximum,
        step = step,
        isAvailable = true,
    )

private fun roundToStep(
    value: Float,
    step: Float,
): Float = round(value / step) * step
