package com.android.car.settings.feature.bluetooth.data;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;

import java.time.Duration;

/** Java bridge for platform APIs hidden from Kotlin's source-level API view. */
public final class BluetoothHiddenApiBridge {
    private BluetoothHiddenApiBridge() {}

    public static int setDiscoverableTimeout(
            BluetoothAdapter adapter,
            Duration timeout) {
        return adapter.setDiscoverableTimeout(timeout);
    }

    public static int setScanMode(BluetoothAdapter adapter, int scanMode) {
        return adapter.setScanMode(scanMode);
    }

    public static int connectionPolicyAllowed() {
        return BluetoothProfile.CONNECTION_POLICY_ALLOWED;
    }

    public static int connectionPolicyForbidden() {
        return BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
    }
}
