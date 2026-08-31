package com.android.car.settings.feature.notifications.data;

import android.app.INotificationManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.ServiceManager;

/** Small guarded bridge for the same privileged notification APIs used by AAOS Settings. */
final class NotificationsHiddenApiBridge {
    private NotificationsHiddenApiBridge() {}

    static Boolean areNotificationsEnabled(String packageName, int uid) {
        try {
            INotificationManager manager = manager();
            return manager == null ? null : manager.areNotificationsEnabledForPackage(packageName, uid);
        } catch (Exception exception) {
            return null;
        }
    }

    static boolean areNotificationsChangeable(String packageName, int uid) {
        try {
            INotificationManager manager = manager();
            return manager != null && !manager.isImportanceLocked(packageName, uid);
        } catch (Exception exception) {
            return false;
        }
    }

    static boolean setNotificationsEnabled(String packageName, int uid, boolean enabled) {
        try {
            INotificationManager manager = manager();
            if (manager == null || manager.isImportanceLocked(packageName, uid)) return false;
            // Keep the default channel in sync, matching Settings' controller behavior.
            if (manager.onlyHasDefaultChannel(packageName, uid)) {
                NotificationChannel channel = manager.getNotificationChannelForPackage(
                        packageName, uid, NotificationChannel.DEFAULT_CHANNEL_ID, null, true);
                if (channel != null) {
                    channel.setImportance(enabled
                            ? NotificationManager.IMPORTANCE_UNSPECIFIED
                            : NotificationManager.IMPORTANCE_NONE);
                    manager.updateNotificationChannelForPackage(packageName, uid, channel);
                }
            }
            manager.setNotificationsEnabledForPackage(packageName, uid, enabled);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private static INotificationManager manager() {
        return INotificationManager.Stub.asInterface(
                ServiceManager.getService(Context.NOTIFICATION_SERVICE));
    }
}
