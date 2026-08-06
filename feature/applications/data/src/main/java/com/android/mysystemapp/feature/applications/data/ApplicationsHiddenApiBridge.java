package com.android.car.settings.feature.applications.data;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.INotificationManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ServiceManager;

/**
 * Narrow bridge for the privileged framework calls used by AAOS Car Settings' Applications
 * controllers. Every call is guarded so the Compose layer can surface a useful failure instead
 * of crashing on an image that does not expose a particular privileged API.
 */
final class ApplicationsHiddenApiBridge {
    private ApplicationsHiddenApiBridge() {}

    static Boolean areNotificationsEnabled(Context context, String packageName, int uid) {
        try {
            INotificationManager manager = notificationService();
            return manager == null ? null : manager.areNotificationsEnabledForPackage(packageName, uid);
        } catch (Exception exception) {
            return null;
        }
    }

    static boolean setNotificationsEnabled(Context context, String packageName, int uid, boolean enabled) {
        INotificationManager manager = notificationService();
        if (manager == null) return false;
        try {
            // This is the same default-channel handling as AAOS Settings' notification controller.
            if (manager.onlyHasDefaultChannel(packageName, uid)) {
                NotificationChannel channel = manager.getNotificationChannelForPackage(
                        packageName,
                        uid,
                        NotificationChannel.DEFAULT_CHANNEL_ID,
                        null,
                        true);
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

    static boolean areNotificationsChangeable(Context context, String packageName, int uid) {
        try {
            INotificationManager manager = notificationService();
            return manager != null && !manager.isImportanceLocked(packageName, uid);
        } catch (Exception exception) {
            return false;
        }
    }

    private static INotificationManager notificationService() {
        return INotificationManager.Stub.asInterface(
                ServiceManager.getService(Context.NOTIFICATION_SERVICE));
    }

    static Integer getUnusedAppOptimizationMode(Context context, String packageName, int uid) {
        try {
            AppOpsManager manager = context.getSystemService(AppOpsManager.class);
            return manager == null ? null : manager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, uid, packageName);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    static boolean setUnusedAppOptimizationEnabled(
            Context context, int uid, boolean enabled) {
        AppOpsManager manager = context.getSystemService(AppOpsManager.class);
        if (manager == null) return false;
        manager.setUidMode(
                AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED,
                uid,
                enabled ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_IGNORED);
        return true;
    }

    static Integer getAppOpMode(Context context, String operation, int uid, String packageName) {
        try {
            AppOpsManager manager = context.getSystemService(AppOpsManager.class);
            return manager == null ? null : manager.unsafeCheckOpNoThrow(operation, uid, packageName);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    static boolean setAppOpMode(
            Context context, String operation, int uid, String packageName, int mode) {
        AppOpsManager manager = context.getSystemService(AppOpsManager.class);
        if (manager == null) return false;
        manager.setMode(operation, uid, packageName, mode);
        return true;
    }

    static boolean forceStop(Context context, String packageName) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager == null) return false;
        manager.forceStopPackage(packageName);
        return true;
    }

    static boolean clearUserData(Context context, String packageName) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        return manager != null && manager.clearApplicationUserData(packageName, null);
    }

    static boolean clearCache(Context context, String packageName) {
        PackageManager manager = context.getPackageManager();
        manager.deleteApplicationCacheFiles(packageName, null);
        return true;
    }
}
