package com.android.car.settings.feature.notifications.data

import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.feature.notifications.domain.NotificationsState
import kotlinx.coroutines.flow.StateFlow

internal interface NotificationsPlatform {
    val state: StateFlow<NotificationsState>

    suspend fun refresh(): ActionResult

    suspend fun selectApp(packageName: String): ActionResult

    suspend fun setNotificationsEnabled(
        packageName: String,
        enabled: Boolean,
    ): ActionResult
}
