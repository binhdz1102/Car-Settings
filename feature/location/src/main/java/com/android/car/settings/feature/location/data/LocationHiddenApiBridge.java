package com.android.car.settings.feature.location.data;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Keeps Automotive-only LocationManager calls out of the Kotlin source set. */
final class LocationHiddenApiBridge {
    static final class RecentAccess {
        final String packageName;
        final long lastAccessMillis;

        RecentAccess(String packageName, long lastAccessMillis) {
            this.packageName = packageName;
            this.lastAccessMillis = lastAccessMillis;
        }
    }

    private LocationHiddenApiBridge() {}

    static boolean isLocationEnabled(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false;
        LocationManager manager = context.getSystemService(LocationManager.class);
        return manager != null && manager.isLocationEnabled();
    }

    static void setLocationEnabled(Context context, boolean enabled) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw new IllegalStateException("Location toggle is not supported on this platform");
        }
        LocationManager manager = context.getSystemService(LocationManager.class);
        if (manager == null) throw new IllegalStateException("Location is not supported");
        manager.setLocationEnabledForUser(enabled, Process.myUserHandle());
    }

    static boolean supportsAdasLocation(Context context) {
        LocationManager manager = context.getSystemService(LocationManager.class);
        if (manager == null) return false;
        try {
            manager.getAdasAllowlist();
            manager.isAdasGnssLocationEnabled();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static boolean isAdasLocationEnabled(Context context) {
        LocationManager manager = context.getSystemService(LocationManager.class);
        return manager != null && manager.isAdasGnssLocationEnabled();
    }

    static void setAdasLocationEnabled(Context context, boolean enabled) {
        LocationManager manager = context.getSystemService(LocationManager.class);
        if (manager == null) throw new IllegalStateException("ADAS location is not supported");
        manager.setAdasGnssLocationEnabled(enabled);
    }

    static List<RecentAccess> getRecentAccesses(Context context) {
        try {
            AppOpsManager manager = context.getSystemService(AppOpsManager.class);
            if (manager == null) return Collections.emptyList();
            List<AppOpsManager.PackageOps> packages = manager.getPackagesForOps(
                    new int[]{AppOpsManager.OP_COARSE_LOCATION, AppOpsManager.OP_FINE_LOCATION});
            if (packages == null) return Collections.emptyList();
            List<RecentAccess> result = new ArrayList<>();
            for (AppOpsManager.PackageOps packageOps : packages) {
                long lastAccess = 0L;
                for (AppOpsManager.OpEntry op : packageOps.getOps()) {
                    if (op.getOp() == AppOpsManager.OP_COARSE_LOCATION
                            || op.getOp() == AppOpsManager.OP_FINE_LOCATION) {
                        lastAccess = Math.max(lastAccess,
                                op.getLastAccessTime(AppOpsManager.OP_FLAGS_ALL));
                    }
                }
                if (lastAccess > 0L) {
                    result.add(new RecentAccess(packageOps.getPackageName(), lastAccess));
                }
            }
            return result;
        } catch (RuntimeException exception) {
            return Collections.emptyList();
        }
    }

    static void setLocationPermissions(Context context, String packageName, boolean granted) {
        PackageManager packageManager = context.getPackageManager();
        if (granted) {
            packageManager.grantRuntimePermission(
                    packageName,
                    "android.permission.ACCESS_COARSE_LOCATION",
                    Process.myUserHandle());
            packageManager.grantRuntimePermission(
                    packageName,
                    "android.permission.ACCESS_FINE_LOCATION",
                    Process.myUserHandle());
        } else {
            packageManager.revokeRuntimePermission(
                    packageName,
                    "android.permission.ACCESS_COARSE_LOCATION",
                    Process.myUserHandle());
            packageManager.revokeRuntimePermission(
                    packageName,
                    "android.permission.ACCESS_FINE_LOCATION",
                    Process.myUserHandle());
        }
    }
}
