package com.android.car.settings.feature.hvac.data

import com.android.car.settings.feature.hvac.domain.HvacRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class HvacRepositoryImpl
    @Inject
    constructor(
        private val platform: HvacPlatform,
    ) : HvacRepository {
        override val state = platform.state

        override suspend fun refresh() = platform.refresh()

        override suspend fun setBoolean(
            key: String,
            value: Boolean,
        ) = platform.setBoolean(key, value)

        override suspend fun setInt(
            key: String,
            value: Int,
        ) = platform.setInt(key, value)

        override suspend fun setFloat(
            key: String,
            value: Float,
        ) = platform.setFloat(key, value)
    }
