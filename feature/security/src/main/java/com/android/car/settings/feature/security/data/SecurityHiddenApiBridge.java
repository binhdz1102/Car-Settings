package com.android.car.settings.feature.security.data;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.security.KeyChain;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternView;
import com.android.internal.widget.LockscreenCredential;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Java bridge for lockscreen and KeyChain APIs that are hidden from the app Kotlin SDK. */
final class SecurityHiddenApiBridge {
    static final int TYPE_NONE = 0;
    static final int TYPE_PATTERN = 1;
    static final int TYPE_PIN = 2;
    static final int TYPE_PASSWORD = 3;
    static final int TYPE_UNKNOWN = 4;

    static final class Admin {
        final String componentName;
        final String packageName;
        final String label;

        Admin(String componentName, String packageName, String label) {
            this.componentName = componentName;
            this.packageName = packageName;
            this.label = label;
        }
    }

    private SecurityHiddenApiBridge() {}

    static boolean canManageScreenLock(Context context) {
        return context.checkSelfPermission("android.permission.ACCESS_KEYGUARD_SECURE_STORAGE")
                == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission("android.permission.SET_AND_VERIFY_LOCKSCREEN_CREDENTIALS")
                == PackageManager.PERMISSION_GRANTED;
    }

    static int getLockType(Context context) {
        return toType(new LockPatternUtils(context).getCredentialTypeForUser(userId()));
    }

    static boolean isGuestUser(Context context) {
        UserManager manager = context.getSystemService(UserManager.class);
        return manager != null && manager.isGuestUser();
    }

    static boolean setLock(Context context, int newType, String currentValue, String newValue) {
        LockPatternUtils utils = new LockPatternUtils(context);
        int currentType = toType(utils.getCredentialTypeForUser(userId()));
        LockscreenCredential current = credentialFor(currentType, currentValue);
        LockscreenCredential replacement = credentialFor(newType, newValue);
        try {
            if (currentType != TYPE_NONE && !utils.verifyCredential(current, userId(), 0).isMatched()) {
                return false;
            }
            return utils.setLockCredential(replacement, current, userId());
        } finally {
            current.zeroize();
            replacement.zeroize();
        }
    }

    static boolean resetCredentials(Context context, String currentValue) {
        LockPatternUtils utils = new LockPatternUtils(context);
        int currentType = toType(utils.getCredentialTypeForUser(userId()));
        LockscreenCredential current = credentialFor(currentType, currentValue);
        try {
            if (currentType != TYPE_NONE && !utils.verifyCredential(current, userId(), 0).isMatched()) {
                return false;
            }
            utils.resetKeyStore(userId());
            try (KeyChain.KeyChainConnection connection = KeyChain.bindAsUser(context,
                    Process.myUserHandle())) {
                return connection.getService().reset();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception exception) {
                return false;
            }
        } finally {
            current.zeroize();
        }
    }

    static List<Admin> getActiveAdmins(Context context) {
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        if (manager == null || manager.getActiveAdmins() == null) return Collections.emptyList();
        List<Admin> result = new ArrayList<>();
        for (ComponentName component : manager.getActiveAdmins()) {
            String label;
            try {
                label = context.getPackageManager().getApplicationInfo(component.getPackageName(), 0)
                        .loadLabel(context.getPackageManager()).toString();
            } catch (Exception exception) {
                label = component.getPackageName();
            }
            result.add(new Admin(component.flattenToString(), component.getPackageName(), label));
        }
        return result;
    }

    static void removeActiveAdmin(Context context, String flattenedComponent) {
        ComponentName component = ComponentName.unflattenFromString(flattenedComponent);
        if (component == null) throw new IllegalArgumentException("Invalid device administrator");
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        if (manager == null) throw new IllegalStateException("Device policy service is unavailable");
        manager.removeActiveAdmin(component);
    }

    private static int userId() { return UserHandle.getUserId(Process.myUid()); }

    private static int toType(int credentialType) {
        switch (credentialType) {
            case LockPatternUtils.CREDENTIAL_TYPE_NONE: return TYPE_NONE;
            case LockPatternUtils.CREDENTIAL_TYPE_PATTERN: return TYPE_PATTERN;
            case LockPatternUtils.CREDENTIAL_TYPE_PIN: return TYPE_PIN;
            case LockPatternUtils.CREDENTIAL_TYPE_PASSWORD: return TYPE_PASSWORD;
            default: return TYPE_UNKNOWN;
        }
    }

    private static LockscreenCredential credentialFor(int type, String value) {
        switch (type) {
            case TYPE_NONE: return LockscreenCredential.createNone();
            case TYPE_PIN: return LockscreenCredential.createPin(value);
            case TYPE_PASSWORD: return LockscreenCredential.createPassword(value);
            case TYPE_PATTERN: return LockscreenCredential.createPattern(parsePattern(value));
            default: return LockscreenCredential.createPasswordOrNone(value);
        }
    }

    private static List<LockPatternView.Cell> parsePattern(String value) {
        String[] parts = value.split(",");
        List<LockPatternView.Cell> cells = new ArrayList<>();
        boolean[] seen = new boolean[9];
        for (String part : parts) {
            if (part.trim().isEmpty()) continue;
            int cell;
            try {
                cell = Integer.parseInt(part.trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Pattern must use the numbered grid");
            }
            if (cell < 1 || cell > 9 || seen[cell - 1]) {
                throw new IllegalArgumentException("Pattern cells must be unique values from 1 to 9");
            }
            seen[cell - 1] = true;
            cells.add(LockPatternView.Cell.of((cell - 1) / 3, (cell - 1) % 3));
        }
        return cells;
    }
}
