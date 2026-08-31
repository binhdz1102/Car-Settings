package com.android.car.settings

import android.Manifest
import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConnectivityPermissionPolicyTest {
    @Test
    fun `wifi uses nearby permission from Android 13`() {
        assertThat(ConnectivityPermissionPolicy.wifiPermissions(Build.VERSION_CODES.TIRAMISU))
            .containsExactly(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    @Test
    fun `wifi uses location before Android 13`() {
        assertThat(ConnectivityPermissionPolicy.wifiPermissions(Build.VERSION_CODES.S))
            .containsExactly(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Test
    fun `bluetooth permissions are scoped to Android 12 and newer`() {
        assertThat(ConnectivityPermissionPolicy.bluetoothPermissions(Build.VERSION_CODES.S))
            .containsExactly(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        assertThat(ConnectivityPermissionPolicy.bluetoothPermissions(Build.VERSION_CODES.R)).isEmpty()
    }
}
