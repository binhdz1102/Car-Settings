package com.android.car.settings.feature.hvac.domain

import javax.inject.Inject

class HvacUseCases @Inject constructor(
    private val repository: HvacRepository,
) {
    fun observeState() = repository.state

    suspend fun refresh() = repository.refresh()

    suspend fun setBoolean(key: String, value: Boolean) = repository.setBoolean(key, value)

    suspend fun setInt(key: String, value: Int) = repository.setInt(key, value)

    suspend fun setFloat(key: String, value: Float) = repository.setFloat(key, value)
}
