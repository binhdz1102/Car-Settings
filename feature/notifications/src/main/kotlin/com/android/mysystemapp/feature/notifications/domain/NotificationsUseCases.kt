package com.android.car.settings.feature.notifications.domain

import javax.inject.Inject

class NotificationsUseCases @Inject constructor(
    private val repository: NotificationsRepository,
) {
    fun observeState() = repository.state

    suspend fun refresh() = repository.refresh()

    suspend fun selectApp(packageName: String) = repository.selectApp(packageName)

    suspend fun setNotificationsEnabled(packageName: String, enabled: Boolean) =
        repository.setNotificationsEnabled(packageName, enabled)
}
