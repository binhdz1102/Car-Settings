package com.android.car.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.car.settings.core.ui.MySystemTheme
import com.android.car.settings.feature.applications.presentation.ALL_APPLICATIONS_ROUTE
import com.android.car.settings.feature.applications.presentation.APPLICATIONS_ROUTE
import com.android.car.settings.feature.applications.presentation.PERFORMANCE_IMPACTING_APPS_ROUTE
import com.android.car.settings.feature.applications.presentation.SPECIAL_APP_ACCESS_ROUTE
import com.android.car.settings.feature.applications.presentation.applicationsGraph
import com.android.car.settings.feature.bluetooth.presentation.BLUETOOTH_ROUTE
import com.android.car.settings.feature.bluetooth.presentation.bluetoothGraph
import com.android.car.settings.feature.display.presentation.DATE_TIME_ROUTE
import com.android.car.settings.feature.display.presentation.DISPLAY_ROUTE
import com.android.car.settings.feature.display.presentation.TIME_ZONE_ROUTE
import com.android.car.settings.feature.display.presentation.displayGraph
import com.android.car.settings.feature.hvac.presentation.HVAC_ROUTE
import com.android.car.settings.feature.hvac.presentation.hvacGraph
import com.android.car.settings.feature.notifications.presentation.NOTIFICATIONS_ROUTE
import com.android.car.settings.feature.notifications.presentation.notificationsGraph
import com.android.car.settings.feature.privacy.presentation.PRIVACY_CAMERA_ROUTE
import com.android.car.settings.feature.privacy.presentation.PRIVACY_LOCATION_ROUTE
import com.android.car.settings.feature.privacy.presentation.PRIVACY_MICROPHONE_ROUTE
import com.android.car.settings.feature.privacy.presentation.PRIVACY_ROUTE
import com.android.car.settings.feature.privacy.presentation.privacyGraph
import com.android.car.settings.feature.profileaccounts.presentation.PROFILE_ACCOUNTS_ROUTE
import com.android.car.settings.feature.profileaccounts.presentation.PROFILES_ROUTE
import com.android.car.settings.feature.profileaccounts.presentation.profileAccountsGraph
import com.android.car.settings.feature.search.domain.SearchDestination
import com.android.car.settings.feature.search.presentation.SEARCH_ROUTE
import com.android.car.settings.feature.search.presentation.searchGraph
import com.android.car.settings.feature.security.presentation.SECURITY_DEVICE_ADMINS_ROUTE
import com.android.car.settings.feature.security.presentation.SECURITY_LOCK_TYPES_ROUTE
import com.android.car.settings.feature.security.presentation.SECURITY_ROUTE
import com.android.car.settings.feature.security.presentation.securityGraph
import com.android.car.settings.feature.sound.domain.RingtoneKind
import com.android.car.settings.feature.sound.presentation.SOUND_ROUTE
import com.android.car.settings.feature.sound.presentation.ringtoneRoute
import com.android.car.settings.feature.sound.presentation.soundGraph
import com.android.car.settings.feature.system.presentation.LANGUAGE_INPUT_ROUTE
import com.android.car.settings.feature.system.presentation.LEGAL_ROUTE
import com.android.car.settings.feature.system.presentation.RESET_OPTIONS_ROUTE
import com.android.car.settings.feature.system.presentation.STORAGE_ROUTE
import com.android.car.settings.feature.system.presentation.SYSTEM_ABOUT_ROUTE
import com.android.car.settings.feature.system.presentation.SYSTEM_ROUTE
import com.android.car.settings.feature.system.presentation.UNITS_ROUTE
import com.android.car.settings.feature.system.presentation.systemGraph
import com.android.car.settings.feature.wifi.presentation.WIFI_HOTSPOT_ROUTE
import com.android.car.settings.feature.wifi.presentation.WIFI_PREFERENCES_ROUTE
import com.android.car.settings.feature.wifi.presentation.WIFI_ROUTE
import com.android.car.settings.feature.wifi.presentation.wifiGraph
import com.android.car.settings.navigation.SettingsIntentRouter
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var launchIntent by mutableStateOf<Intent?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            Timber.i("Connectivity permission result: %s", results)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchIntent = intent
        enableEdgeToEdge()
        requestConnectivityPermissions()
        Timber.i("Car-Settings created; action=%s data=%s", intent.action, intent.data)
        setContent {
            MySystemTheme {
                val navController = rememberNavController()
                val requestedDestination = SettingsIntentRouter.destinationFor(launchIntent)
                NavHost(
                    navController = navController,
                    startDestination = requestedDestination,
                ) {
                    composable(SettingsIntentRouter.HOME_ROUTE) {
                        HomeScreen(
                            onWifi = { navController.navigate(WIFI_ROUTE) },
                            onBluetooth = { navController.navigate(BLUETOOTH_ROUTE) },
                            onSound = { navController.navigate(SOUND_ROUTE) },
                            onDisplay = { navController.navigate(DISPLAY_ROUTE) },
                            onApplications = { navController.navigate(APPLICATIONS_ROUTE) },
                            onProfileAccounts = { navController.navigate(PROFILE_ACCOUNTS_ROUTE) },
                            onSystem = { navController.navigate(SYSTEM_ROUTE) },
                            onNotifications = { navController.navigate(NOTIFICATIONS_ROUTE) },
                            onPrivacy = { navController.navigate(PRIVACY_ROUTE) },
                            onSecurity = { navController.navigate(SECURITY_ROUTE) },
                            onHvac = { navController.navigate(HVAC_ROUTE) },
                            onSearch = { navController.navigate(SEARCH_ROUTE) },
                        )
                    }
                    wifiGraph(navController, navController::popBackStack)
                    bluetoothGraph(navController, navController::popBackStack)
                    soundGraph(navController, navController::popBackStack)
                    displayGraph(navController, navController::popBackStack)
                    applicationsGraph(navController, navController::popBackStack)
                    profileAccountsGraph(navController, navController::popBackStack)
                    systemGraph(navController, navController::popBackStack)
                    notificationsGraph(navController, navController::popBackStack)
                    privacyGraph(navController, navController::popBackStack)
                    securityGraph(navController, navController::popBackStack)
                    searchGraph(
                        onBack = navController::popBackStack,
                        onDestination = { destination ->
                            navController.navigate(searchDestinationRoute(destination)) {
                                popUpTo(SEARCH_ROUTE) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                    hvacGraph(navController::popBackStack)
                }
                LaunchedEffect(requestedDestination) {
                    if (navController.currentDestination?.route != requestedDestination) {
                        Timber.d("Routing new intent to %s", requestedDestination)
                        navController.navigate(requestedDestination) {
                            popUpTo(SettingsIntentRouter.HOME_ROUTE)
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchIntent = intent
        Timber.i("Car-Settings received new intent; action=%s data=%s", intent.action, intent.data)
    }

    override fun onDestroy() {
        Timber.i("Car-Settings destroyed")
        super.onDestroy()
    }

    private fun requestConnectivityPermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        val missing = permissions.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            Timber.d("Requesting connectivity permissions: %s", missing)
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}

private fun searchDestinationRoute(destination: SearchDestination): String = when (destination) {
    SearchDestination.DISPLAY -> DISPLAY_ROUTE
    SearchDestination.DATE_TIME -> DATE_TIME_ROUTE
    SearchDestination.TIME_ZONE -> TIME_ZONE_ROUTE
    SearchDestination.WIFI -> WIFI_ROUTE
    SearchDestination.WIFI_HOTSPOT -> WIFI_HOTSPOT_ROUTE
    SearchDestination.WIFI_PREFERENCES -> WIFI_PREFERENCES_ROUTE
    SearchDestination.BLUETOOTH -> BLUETOOTH_ROUTE
    SearchDestination.SOUND -> SOUND_ROUTE
    SearchDestination.PHONE_RINGTONE -> ringtoneRoute(RingtoneKind.PHONE)
    SearchDestination.NOTIFICATION_RINGTONE -> ringtoneRoute(RingtoneKind.NOTIFICATION)
    SearchDestination.ALARM_RINGTONE -> ringtoneRoute(RingtoneKind.ALARM)
    SearchDestination.APPLICATIONS -> APPLICATIONS_ROUTE
    SearchDestination.ALL_APPLICATIONS -> ALL_APPLICATIONS_ROUTE
    SearchDestination.SPECIAL_APP_ACCESS -> SPECIAL_APP_ACCESS_ROUTE
    SearchDestination.PERFORMANCE_APPS -> PERFORMANCE_IMPACTING_APPS_ROUTE
    SearchDestination.NOTIFICATIONS -> NOTIFICATIONS_ROUTE
    SearchDestination.PRIVACY -> PRIVACY_ROUTE
    SearchDestination.PRIVACY_MICROPHONE -> PRIVACY_MICROPHONE_ROUTE
    SearchDestination.PRIVACY_CAMERA -> PRIVACY_CAMERA_ROUTE
    SearchDestination.PRIVACY_LOCATION -> PRIVACY_LOCATION_ROUTE
    SearchDestination.SECURITY -> SECURITY_ROUTE
    SearchDestination.SCREEN_LOCK -> SECURITY_LOCK_TYPES_ROUTE
    SearchDestination.DEVICE_ADMINS -> SECURITY_DEVICE_ADMINS_ROUTE
    SearchDestination.PROFILE_ACCOUNTS -> PROFILE_ACCOUNTS_ROUTE
    SearchDestination.PROFILES -> PROFILES_ROUTE
    SearchDestination.SYSTEM -> SYSTEM_ROUTE
    SearchDestination.ABOUT -> SYSTEM_ABOUT_ROUTE
    SearchDestination.LANGUAGE_INPUT -> LANGUAGE_INPUT_ROUTE
    SearchDestination.UNITS -> UNITS_ROUTE
    SearchDestination.STORAGE -> STORAGE_ROUTE
    SearchDestination.LEGAL -> LEGAL_ROUTE
    SearchDestination.RESET_OPTIONS -> RESET_OPTIONS_ROUTE
    SearchDestination.HVAC -> HVAC_ROUTE
}
