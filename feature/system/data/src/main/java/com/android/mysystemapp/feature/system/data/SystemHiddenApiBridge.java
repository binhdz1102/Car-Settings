package com.android.car.settings.feature.system.data;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.INotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

import java.util.List;
import java.util.Locale;

/** Framework-only calls copied from AAOS ResetAppPrefFragment. */
final class SystemHiddenApiBridge {
    private SystemHiddenApiBridge() {}

    static boolean resetApplicationPreferences(Context context) {
        try {
            INotificationManager notifications = INotificationManager.Stub.asInterface(
                    ServiceManager.getService(Context.NOTIFICATION_SERVICE));
            IPackageManager packages = IPackageManager.Stub.asInterface(
                    ServiceManager.getService("package"));
            if (notifications == null || packages == null) return false;

            PackageManager packageManager = context.getPackageManager();
            List<ApplicationInfo> apps = packageManager.getInstalledApplications(
                    PackageManager.MATCH_DISABLED_COMPONENTS);
            for (ApplicationInfo app : apps) {
                notifications.setNotificationsEnabledForPackage(app.packageName, app.uid, true);
                if (!app.enabled && packageManager.getApplicationEnabledSetting(app.packageName)
                        == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) {
                    packageManager.setApplicationEnabledSetting(app.packageName,
                            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                            PackageManager.DONT_KILL_APP);
                }
            }
            packages.resetApplicationPreferences(ActivityManager.getCurrentUser());
            AppOpsManager appOps = context.getSystemService(AppOpsManager.class);
            if (appOps != null) appOps.resetAllModes();
            return true;
        } catch (RemoteException | SecurityException exception) {
            return false;
        }
    }

    static boolean updateSystemLocale(String languageTag) {
        try {
            Class<?> localePicker = Class.forName("com.android.internal.app.LocalePicker");
            localePicker.getMethod("updateLocale", Locale.class).invoke(
                    null, Locale.forLanguageTag(languageTag));
            return true;
        } catch (ReflectiveOperationException | SecurityException exception) {
            return false;
        }
    }
}
