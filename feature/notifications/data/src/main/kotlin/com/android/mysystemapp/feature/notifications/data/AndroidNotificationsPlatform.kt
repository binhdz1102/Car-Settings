package com.android.car.settings.feature.notifications.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.feature.notifications.domain.NotificationApp
import com.android.car.settings.feature.notifications.domain.NotificationsState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AAOS Notifications parity implementation.  The two lists use the same app eligibility as
 * NotificationsFragment: downloaded apps plus launchable system apps, never background-only
 * system packages.
 */
@Singleton
internal class AndroidNotificationsPlatform @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : NotificationsPlatform {
    private val packageManager = context.packageManager
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(NotificationsState())

    override val state: StateFlow<NotificationsState> = mutableState.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope.launch { refresh() }
        }
    }

    init {
        context.registerReceiver(
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(ACTION_APP_BLOCK_STATE_CHANGED)
                addDataScheme("package")
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        scope.launch { refresh() }
    }

    override suspend fun refresh(): ActionResult = execute {
        val recentTimes = loadRecentNotificationTimes()
        val apps = packageManager
            .getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
            .asSequence()
            .filter(::isVisibleToNotifications)
            .map { it.toNotificationApp(recentTimes[it.packageName]) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
        val selectedPackage = mutableState.value.selectedApp?.packageName
        mutableState.value = NotificationsState(
            recentlySent = apps
                .asSequence()
                .filter { it.lastNotifiedMillis != null }
                .sortedByDescending { it.lastNotifiedMillis }
                .take(MAX_RECENT_APPS)
                .toList(),
            allApps = apps,
            selectedApp = apps.firstOrNull { it.packageName == selectedPackage },
        )
    }

    override suspend fun selectApp(packageName: String): ActionResult = execute {
        val selected = mutableState.value.allApps.firstOrNull { it.packageName == packageName }
            ?: packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
                .takeIf(::isVisibleToNotifications)
                ?.toNotificationApp(loadRecentNotificationTimes()[packageName])
        requireNotNull(selected) { "App is no longer available" }
        mutableState.value = mutableState.value.copy(selectedApp = selected)
    }

    override suspend fun setNotificationsEnabled(packageName: String, enabled: Boolean): ActionResult =
        execute {
            val info = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
            check(NotificationsHiddenApiBridge.areNotificationsChangeable(packageName, info.uid)) {
                "Notification settings are locked for this app"
            }
            check(NotificationsHiddenApiBridge.setNotificationsEnabled(packageName, info.uid, enabled)) {
                "The system did not allow changing notifications for this app"
            }
            refreshOrThrow()
        }

    private fun isVisibleToNotifications(info: ApplicationInfo): Boolean =
        !info.isSystemApp() || packageManager.getLaunchIntentForPackage(info.packageName) != null

    private fun ApplicationInfo.toNotificationApp(lastNotifiedMillis: Long?): NotificationApp {
        val enabled = NotificationsHiddenApiBridge.areNotificationsEnabled(packageName, uid) ?: false
        return NotificationApp(
            packageName = packageName,
            label = loadLabel(packageManager).toString(),
            isSystemApp = isSystemApp(),
            isEnabled = this.enabled,
            notificationsEnabled = enabled,
            notificationsChangeable = NotificationsHiddenApiBridge.areNotificationsChangeable(packageName, uid),
            lastNotifiedMillis = lastNotifiedMillis,
        )
    }

    private fun loadRecentNotificationTimes(): Map<String, Long> {
        val start = System.currentTimeMillis() - RECENT_WINDOW_MILLIS
        return runCatching {
            val events = usageStatsManager?.queryEvents(start, System.currentTimeMillis()) ?: return emptyMap()
            val event = UsageEvents.Event()
            buildMap {
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    // Hidden framework constant UsageEvents.Event.NOTIFICATION_INTERRUPTION.
                    if (event.eventType == EVENT_NOTIFICATION_INTERRUPTION) {
                        event.packageName?.let { packageName ->
                            val timestamp = event.timeStamp
                            if (timestamp > (get(packageName) ?: Long.MIN_VALUE)) {
                                put(packageName, timestamp)
                            }
                        }
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private suspend fun execute(block: suspend () -> Unit): ActionResult = withContext(dispatcher) {
        try {
            block()
            ActionResult.Success
        } catch (throwable: Throwable) {
            val message = throwable.cause?.message ?: throwable.message ?: "Unknown notification error"
            mutableState.value = mutableState.value.copy(lastError = message)
            ActionResult.Failure(message, throwable)
        }
    }

    private suspend fun refreshOrThrow() {
        check(refresh() == ActionResult.Success) { "Unable to refresh notification settings" }
    }
}

private fun ApplicationInfo.isSystemApp(): Boolean =
    flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

private const val ACTION_APP_BLOCK_STATE_CHANGED = "android.app.action.APP_BLOCK_STATE_CHANGED"
private const val RECENT_WINDOW_MILLIS = 7 * 24 * 60 * 60 * 1_000L
private const val MAX_RECENT_APPS = 5
private const val EVENT_NOTIFICATION_INTERRUPTION = 12
