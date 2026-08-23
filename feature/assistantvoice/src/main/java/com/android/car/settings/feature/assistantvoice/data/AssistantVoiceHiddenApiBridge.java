package com.android.car.settings.feature.assistantvoice.data;

import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.service.voice.VoiceInteractionServiceInfo;

import java.util.List;

/** Isolates RoleManager calls that are hidden from the public SDK stubs. */
final class AssistantVoiceHiddenApiBridge {
    private AssistantVoiceHiddenApiBridge() {}

    static String getAssistantHolder(Context context) {
        if (Build.VERSION.SDK_INT < 29) return null;
        RoleManager manager = context.getSystemService(RoleManager.class);
        if (manager == null) return null;
        try {
            List<String> holders = manager.getRoleHolders(RoleManager.ROLE_ASSISTANT);
            return holders == null || holders.isEmpty() ? null : holders.get(0);
        } catch (RuntimeException exception) {
            // A production image may expose role information only to PermissionController.
            // The rest of Assistant & Voice remains usable in that case.
            return null;
        }
    }

    static String getAssistantSettingsActivity(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            for (ResolveInfo resolveInfo : packageManager.queryIntentServices(
                    new android.content.Intent("android.service.voice.VoiceInteractionService"),
                    PackageManager.GET_META_DATA)) {
                if (!packageName.equals(resolveInfo.serviceInfo.packageName)) continue;
                VoiceInteractionServiceInfo info = new VoiceInteractionServiceInfo(
                        packageManager, resolveInfo.serviceInfo);
                String activity = info.getSettingsActivity();
                if (activity == null || activity.isEmpty()) return null;
                return new ComponentName(packageName, activity).flattenToString();
            }
        } catch (RuntimeException exception) {
            return null;
        }
        return null;
    }

    static String getRecognitionComponent(Context context, ResolveInfo resolveInfo) {
        try {
            VoiceInteractionServiceInfo info = new VoiceInteractionServiceInfo(
                    context.getPackageManager(), resolveInfo.serviceInfo);
            String recognition = info.getRecognitionService();
            if (recognition == null || recognition.isEmpty()) return null;
            return new ComponentName(resolveInfo.serviceInfo.packageName, recognition)
                    .flattenToString();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
