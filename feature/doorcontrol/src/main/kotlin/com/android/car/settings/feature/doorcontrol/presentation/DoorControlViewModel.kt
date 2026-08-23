package com.android.car.settings.feature.doorcontrol.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.feature.doorcontrol.data.DoorControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DoorControlViewModel
    @Inject
    constructor(
        repository: DoorControlRepository,
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
