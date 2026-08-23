package com.android.car.settings.feature.notifications.data

import com.android.car.settings.feature.notifications.domain.NotificationsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class NotificationsRepositoryImpl
    @Inject
    constructor(
        private val platform: NotificationsPlatform,
    ) : NotificationsRepository {
        override val state = platform.state

        override suspend fun refresh() = platform.refresh()

        override suspend fun selectApp(packageName: String) = platform.selectApp(packageName)

        override suspend fun setNotificationsEnabled(
            packageName: String,
            enabled: Boolean,
        ) = platform.setNotificationsEnabled(packageName, enabled)
    }
