package com.android.car.settings.feature.vehiclelighting.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.feature.vehiclelighting.data.VehicleLightingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VehicleLightingViewModel
    @Inject
    constructor(
        repository: VehicleLightingRepository,
    ) : ViewModel() {
        private val controller = repository.createController(viewModelScope)
        val state = controller.state

        fun refresh() = controller.refresh()

        fun setBoolean(
            key: String,
            areaId: Int,
            value: Boolean,
        ) = controller.setBoolean(key, areaId, value)

        fun setInt(
            key: String,
            areaId: Int,
            value: Int,
        ) = controller.setInt(key, areaId, value)
    }
