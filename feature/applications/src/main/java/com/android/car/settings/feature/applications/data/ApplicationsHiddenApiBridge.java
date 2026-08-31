package com.android.car.settings.feature.applications.data;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.INotificationManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.ComponentName;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.UserHandle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import android.os.Build;
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
            return manager == null ? null : checkOpNoThrow(
                    manager, AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, uid, packageName);
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
            return manager == null ? null : checkOpNoThrow(manager, operation, uid, packageName);
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

    private static int checkOpNoThrow(
            AppOpsManager manager, String operation, int uid, String packageName) {
        if (Build.VERSION.SDK_INT >= 29) {
            return manager.unsafeCheckOpNoThrow(operation, uid, packageName);
        }
        return manager.checkOpNoThrow(operation, uid, packageName);
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

    static boolean isRuntimePermission(PermissionInfo permissionInfo) {
        if (permissionInfo == null) return false;
        return (permissionInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE)
                == PermissionInfo.PROTECTION_DANGEROUS;
    }

    static int getPermissionFlags(
            Context context, String permissionName, String packageName, UserHandle user) {
        try {
            return (Integer) PackageManager.class
                    .getMethod("getPermissionFlags", String.class, String.class, UserHandle.class)
                    .invoke(context.getPackageManager(), permissionName, packageName, user);
        } catch (Exception exception) {
            return 0;
        }
    }

    static boolean setRuntimePermission(
            Context context, String packageName, String permissionName, UserHandle user, boolean granted) {
        try {
            String methodName = granted ? "grantRuntimePermission" : "revokeRuntimePermission";
            PackageManager.class
                    .getMethod(methodName, String.class, String.class, UserHandle.class)
                    .invoke(context.getPackageManager(), packageName, permissionName, user);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    static boolean setDefaultApplication(Context context, String roleName, String packageName) {
        RoleManager roleManager = context.getSystemService(RoleManager.class);
        if (roleManager == null) return false;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean(false);
        try {
            roleManager.setDefaultApplication(
                    roleName,
                    packageName,
                    RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                    context.getMainExecutor(),
                    success -> {
                        result.set(Boolean.TRUE.equals(success));
                        latch.countDown();
                    });
            latch.await(3, TimeUnit.SECONDS);
            return result.get();
        } catch (Exception exception) {
            return false;
        }
    }

    static boolean isRoleAvailable(Context context, String roleName) {
        try {
            RoleManager manager = context.getSystemService(RoleManager.class);
            return manager != null && manager.isRoleAvailable(roleName);
        } catch (Exception exception) {
            return false;
        }
    }

    static List<String> getRoleHolders(Context context, String roleName) {
        try {
            RoleManager manager = context.getSystemService(RoleManager.class);
            return manager == null ? java.util.Collections.emptyList() : manager.getRoleHolders(roleName);
        } catch (Exception exception) {
            return java.util.Collections.emptyList();
        }
    }

    static boolean setDomainVerificationLinkHandlingAllowed(
            Context context, String packageName, boolean enabled) {
        try {
            Object manager = context.getSystemService(
                    Class.forName("android.content.pm.verify.domain.DomainVerificationManager"));
            if (manager == null) return false;
            manager.getClass()
                    .getMethod("setDomainVerificationLinkHandlingAllowed", String.class, boolean.class)
                    .invoke(manager, packageName, enabled);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    static List<String> queryValidDomainPackages(Context context) {
        try {
            Object manager = context.getSystemService(
                    Class.forName("android.content.pm.verify.domain.DomainVerificationManager"));
            if (manager == null) return java.util.Collections.emptyList();
            Object result = manager.getClass().getMethod("queryValidVerificationPackageNames").invoke(manager);
            return result instanceof List ? (List<String>) result : java.util.Collections.emptyList();
        } catch (Exception exception) {
            return java.util.Collections.emptyList();
        }
    }

    static boolean isDomainLinkHandlingAllowed(Context context, String packageName) {
        try {
            Object manager = context.getSystemService(
                    Class.forName("android.content.pm.verify.domain.DomainVerificationManager"));
            if (manager == null) return false;
            Object state = manager.getClass()
                    .getMethod("getDomainVerificationUserState", String.class)
                    .invoke(manager, packageName);
            return state != null && (Boolean) state.getClass().getMethod("isLinkHandlingAllowed").invoke(state);
        } catch (Exception exception) {
            return false;
        }
    }

    static List<String> getDomainHosts(Context context, String packageName) {
        try {
            Object manager = context.getSystemService(
                    Class.forName("android.content.pm.verify.domain.DomainVerificationManager"));
            if (manager == null) return java.util.Collections.emptyList();
            Object state = manager.getClass()
                    .getMethod("getDomainVerificationUserState", String.class)
                    .invoke(manager, packageName);
            if (state == null) return java.util.Collections.emptyList();
            Object map = state.getClass().getMethod("getHostToStateMap").invoke(state);
            return map instanceof Map ? new ArrayList<>(((Map<String, Integer>) map).keySet())
                    : java.util.Collections.emptyList();
        } catch (Exception exception) {
            return java.util.Collections.emptyList();
        }
    }

    static boolean isNotificationListenerEnabled(Context context, String packageName) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        try {
            for (ComponentName component : manager.getEnabledNotificationListeners()) {
                if (packageName.equals(component.getPackageName())) return true;
            }
        } catch (Exception ignored) {
            // Fall through to the secure setting for images with an older NotificationManager API.
        }
        String enabled = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        for (String component : enabled.split(":")) {
            if (component.startsWith(packageName + "/")) return true;
        }
        return false;
    }

    static boolean setNotificationListenerEnabled(
            Context context, String packageName, boolean enabled) {
        try {
            String key = "enabled_notification_listeners";
            String current = Settings.Secure.getString(context.getContentResolver(), key);
            java.util.LinkedHashSet<String> components = new java.util.LinkedHashSet<>();
            if (current != null && !current.isEmpty()) {
                for (String component : current.split(":")) {
                    if (!component.startsWith(packageName + "/")) components.add(component);
                }
            }
            if (enabled) {
                android.content.pm.PackageManager pm = context.getPackageManager();
                android.content.Intent intent = new android.content.Intent(
                        "android.service.notification.NotificationListenerService");
                for (android.content.pm.ResolveInfo resolve : pm.queryIntentServices(
                        intent, android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)) {
                    if (resolve.serviceInfo != null && packageName.equals(resolve.serviceInfo.packageName)) {
                        components.add(new ComponentName(packageName, resolve.serviceInfo.name).flattenToString());
                    }
                }
            }
            return Settings.Secure.putString(
                    context.getContentResolver(), key, String.join(":", components));
        } catch (Exception exception) {
            return false;
        }
    }
}
