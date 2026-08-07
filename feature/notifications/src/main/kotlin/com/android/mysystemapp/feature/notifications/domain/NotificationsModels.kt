package com.android.car.settings.feature.notifications.domain

/** Mirrors AAOS Settings' notification overview: recent senders and the app list. */
data class NotificationApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val notificationsEnabled: Boolean,
    val notificationsChangeable: Boolean,
    val lastNotifiedMillis: Long? = null,
)

data class NotificationsState(
    val recentlySent: List<NotificationApp> = emptyList(),
    val allApps: List<NotificationApp> = emptyList(),
    val selectedApp: NotificationApp? = null,
    val lastError: String? = null,
)
