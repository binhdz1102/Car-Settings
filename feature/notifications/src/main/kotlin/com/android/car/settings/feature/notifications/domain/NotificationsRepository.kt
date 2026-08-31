package com.android.car.settings.feature.notifications.domain

import com.android.car.settings.core.common.ActionResult
import kotlinx.coroutines.flow.Flow

interface NotificationsRepository {
    val state: Flow<NotificationsState>

    suspend fun refresh(): ActionResult

    suspend fun selectApp(packageName: String): ActionResult

    suspend fun setNotificationsEnabled(
        packageName: String,
        enabled: Boolean,
    ): ActionResult
}
