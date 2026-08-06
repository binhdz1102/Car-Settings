package com.android.car.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.car.settings.core.ui.SettingsScaffold

@Composable
fun HomeScreen(
    onWifi: () -> Unit,
    onBluetooth: () -> Unit,
    onSound: () -> Unit,
    onDisplay: () -> Unit,
    onApplications: () -> Unit,
    onProfileAccounts: () -> Unit,
    onSystem: () -> Unit,
    onNotifications: () -> Unit,
    onPrivacy: () -> Unit,
    onSecurity: () -> Unit,
    onHvac: () -> Unit,
    onSearch: () -> Unit,
) {
    SettingsScaffold(
        title = "Settings",
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = "Search settings")
            }
        },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SystemFeatureCard(
                title = "Display",
                summary = "Brightness, adaptive brightness and day/night theme",
                icon = { Icon(Icons.Default.Brightness6, contentDescription = null) },
                onClick = onDisplay,
            )
            SystemFeatureCard(
                title = "Wi-Fi",
                summary = "Networks, saved connections, hotspot and preferences",
                icon = { Icon(Icons.Default.NetworkWifi, contentDescription = null) },
                onClick = onWifi,
            )
            SystemFeatureCard(
                title = "Bluetooth",
                summary = "Pairing, connected devices, discoverability and profiles",
                icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                onClick = onBluetooth,
            )
            SystemFeatureCard(
                title = "Sound & vibration",
                summary = "Volume, ringtones, vibration and Do Not Disturb",
                icon = { Icon(Icons.Default.VolumeUp, contentDescription = null) },
                onClick = onSound,
            )
            SystemFeatureCard(
                title = "Climate",
                summary = "Temperature, airflow, defrost and seat comfort",
                icon = { Icon(Icons.Default.AcUnit, contentDescription = null) },
                onClick = onHvac,
            )
            SystemFeatureCard(
                title = "Apps",
                summary = "Recently opened apps, app info, permissions and special access",
                icon = { Icon(Icons.Default.Apps, contentDescription = null) },
                onClick = onApplications,
            )
            SystemFeatureCard(
                title = "Notifications",
                summary = "Recently sent notifications and app notification controls",
                icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                onClick = onNotifications,
            )
            SystemFeatureCard(
                title = "Privacy",
                summary = "Microphone, camera, location and app permissions",
                icon = { Icon(Icons.Default.PrivacyTip, contentDescription = null) },
                onClick = onPrivacy,
            )
            SystemFeatureCard(
                title = "Security",
                summary = "Screen lock, credentials and device admin apps",
                icon = { Icon(Icons.Default.Security, contentDescription = null) },
                onClick = onSecurity,
            )
            SystemFeatureCard(
                title = "Profiles & accounts",
                summary = "Vehicle profiles, accounts and automatic sync",
                icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                onClick = onProfileAccounts,
            )
            SystemFeatureCard(
                title = "System",
                summary = "About, legal information, language, storage and reset controls",
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = onSystem,
            )
        }
    }
}

@Composable
private fun SystemFeatureCard(
    title: String,
    summary: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.size(40.dp)) { icon() }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(summary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
