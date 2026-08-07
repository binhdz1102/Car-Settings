package com.android.car.settings.feature.privacy.data;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.SensorPrivacyManager;
import android.location.LocationManager;
import android.os.Process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/** Keeps non-SDK AAOS privacy APIs out of the Kotlin compiler classpath. */
final class PrivacyHiddenApiBridge {
    interface SensorChangedListener {
        void onChanged();
    }

    static final class RecentAccess {
        final String packageName;
        final long lastAccessMillis;

        RecentAccess(String packageName, long lastAccessMillis) {
            this.packageName = packageName;
            this.lastAccessMillis = lastAccessMillis;
        }
    }

    private PrivacyHiddenApiBridge() {}

    static int microphoneSensor() { return SensorPrivacyManager.Sensors.MICROPHONE; }
    static int cameraSensor() { return SensorPrivacyManager.Sensors.CAMERA; }

    static boolean isSensorToggleSupported(Context context, int sensor) {
        return SensorPrivacyManager.getInstance(context).supportsSensorToggle(sensor);
    }

    static boolean isSensorAccessEnabled(Context context, int sensor) {
        return !SensorPrivacyManager.getInstance(context).isSensorPrivacyEnabled(sensor);
    }

    static void setSensorAccessEnabled(Context context, int sensor, boolean enabled) {
        SensorPrivacyManager.getInstance(context).setSensorPrivacyForProfileGroup(
                SensorPrivacyManager.Sources.SETTINGS, sensor, !enabled);
    }

    static void registerSensorChangedListener(Context context, Executor executor,
            SensorChangedListener listener) {
        SensorPrivacyManager manager = SensorPrivacyManager.getInstance(context);
        SensorPrivacyManager.OnSensorPrivacyChangedListener delegate = (sensor, blocked) ->
                listener.onChanged();
        manager.addSensorPrivacyListener(microphoneSensor(), executor, delegate);
        manager.addSensorPrivacyListener(cameraSensor(), executor, delegate);
    }

    static boolean isLocationEnabled(Context context) {
        LocationManager manager = context.getSystemService(LocationManager.class);
        return manager != null && manager.isLocationEnabled();
    }

    static void setLocationEnabled(Context context, boolean enabled) {
        LocationManager manager = context.getSystemService(LocationManager.class);
        if (manager == null) throw new IllegalStateException("Location is not supported");
        manager.setLocationEnabledForUser(enabled, Process.myUserHandle());
    }

    static void setRuntimePermission(Context context, String packageName, String permission,
            boolean granted) {
        PackageManager manager = context.getPackageManager();
        if (granted) {
            manager.grantRuntimePermission(packageName, permission, Process.myUserHandle());
        } else {
            manager.revokeRuntimePermission(packageName, permission, Process.myUserHandle());
        }
    }

    static int[] microphoneOps() {
        return new int[]{AppOpsManager.OP_RECORD_AUDIO, AppOpsManager.OP_RECORD_AUDIO_HOTWORD};
    }

    static int[] cameraOps() { return new int[]{AppOpsManager.OP_CAMERA}; }

    static int[] locationOps() {
        return new int[]{AppOpsManager.OP_COARSE_LOCATION, AppOpsManager.OP_FINE_LOCATION};
    }

    static List<RecentAccess> getRecentAccesses(Context context, int[] operations) {
        try {
            AppOpsManager manager = context.getSystemService(AppOpsManager.class);
            if (manager == null) return Collections.emptyList();
            List<AppOpsManager.PackageOps> packages = manager.getPackagesForOps(operations);
            if (packages == null) return Collections.emptyList();
            List<RecentAccess> result = new ArrayList<>();
            for (AppOpsManager.PackageOps packageOps : packages) {
                long lastAccess = 0L;
                for (AppOpsManager.OpEntry op : packageOps.getOps()) {
                    for (int operation : operations) {
                        if (op.getOp() == operation) {
                            lastAccess = Math.max(lastAccess,
                                    op.getLastAccessTime(AppOpsManager.OP_FLAGS_ALL));
                        }
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
}
