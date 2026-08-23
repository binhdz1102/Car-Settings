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
import com.android.car.settings.core.settings.DefaultSettingsRegistry
import com.android.car.settings.core.settings.SettingsDestinationId
import com.android.car.settings.core.ui.MySystemTheme
import com.android.car.settings.feature.accessibility.presentation.accessibilityGraph
import com.android.car.settings.feature.applications.presentation.APPLICATIONS_ROUTE
import com.android.car.settings.feature.applications.presentation.applicationsGraph
import com.android.car.settings.feature.assistantvoice.presentation.assistantVoiceGraph
import com.android.car.settings.feature.bluetooth.presentation.BLUETOOTH_ROUTE
import com.android.car.settings.feature.bluetooth.presentation.bluetoothGraph
import com.android.car.settings.feature.display.presentation.DISPLAY_ROUTE
import com.android.car.settings.feature.display.presentation.displayGraph
import com.android.car.settings.feature.doorcontrol.presentation.doorControlGraph
import com.android.car.settings.feature.driverassistance.presentation.driverAssistanceGraph
import com.android.car.settings.feature.hvac.presentation.HVAC_ROUTE
import com.android.car.settings.feature.hvac.presentation.hvacGraph
import com.android.car.settings.feature.location.presentation.locationGraph
import com.android.car.settings.feature.notifications.presentation.NOTIFICATIONS_ROUTE
import com.android.car.settings.feature.notifications.presentation.notificationsGraph
import com.android.car.settings.feature.privacy.presentation.PRIVACY_ROUTE
import com.android.car.settings.feature.privacy.presentation.privacyGraph
import com.android.car.settings.feature.profileaccounts.presentation.PROFILE_ACCOUNTS_ROUTE
import com.android.car.settings.feature.profileaccounts.presentation.profileAccountsGraph
import com.android.car.settings.feature.search.presentation.SEARCH_ROUTE
import com.android.car.settings.feature.search.presentation.searchGraph
import com.android.car.settings.feature.seatcontrol.presentation.seatControlGraph
import com.android.car.settings.feature.security.presentation.SECURITY_ROUTE
import com.android.car.settings.feature.security.presentation.securityGraph
import com.android.car.settings.feature.sound.presentation.SOUND_ROUTE
import com.android.car.settings.feature.sound.presentation.soundGraph
import com.android.car.settings.feature.system.presentation.SYSTEM_ROUTE
import com.android.car.settings.feature.system.presentation.systemGraph
import com.android.car.settings.feature.vehiclelighting.presentation.vehicleLightingGraph
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
                            onVehicle = { navController.navigate(VEHICLE_ROUTE) },
                            onAccessibility = { navController.navigate(ACCESSIBILITY_ROUTE) },
                            onLocation = { navController.navigate(LOCATION_ROUTE) },
                            onAssistantVoice = { navController.navigate(ASSISTANT_VOICE_ROUTE) },
                            onSearch = { navController.navigate(SEARCH_ROUTE) },
                        )
                    }
                    composable(VEHICLE_ROUTE) {
                        VehicleLandingScreen(
                            onDestination = { destination ->
                                navController.navigate(destinationRoute(destination)) { launchSingleTop = true }
                            },
                            onBack = navController::popBackStack,
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
                            navController.navigate(destinationRoute(destination)) {
                                popUpTo(SEARCH_ROUTE) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                    hvacGraph(navController::popBackStack)
                    accessibilityGraph(navController::popBackStack)
                    locationGraph(navController, navController::popBackStack)
                    assistantVoiceGraph(navController::popBackStack)
                    doorControlGraph(navController::popBackStack)
                    seatControlGraph(navController::popBackStack)
                    vehicleLightingGraph(navController::popBackStack)
                    driverAssistanceGraph(navController::popBackStack)
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

    private companion object {
        val VEHICLE_ROUTE: String = DefaultSettingsRegistry.route(SettingsDestinationId.VEHICLE)
        val ACCESSIBILITY_ROUTE: String = DefaultSettingsRegistry.route(SettingsDestinationId.ACCESSIBILITY)
        val LOCATION_ROUTE: String = DefaultSettingsRegistry.route(SettingsDestinationId.LOCATION)
        val ASSISTANT_VOICE_ROUTE: String = DefaultSettingsRegistry.route(SettingsDestinationId.ASSISTANT_VOICE)
    }
}

/** Destinations that are not part of this build fall back to the settings home. */
private val UNROUTED_DESTINATIONS =
    setOf(
        SettingsDestinationId.LAUNCHER,
        SettingsDestinationId.SYSTEM_UI_CATALOG,
        SettingsDestinationId.SYSTEM_UI_COCKPIT,
        SettingsDestinationId.SYSTEM_UI_NOTIFICATION_CENTER,
        SettingsDestinationId.SYSTEM_UI_USER_CENTER,
    )

internal fun destinationRoute(destination: SettingsDestinationId): String =
    if (destination in UNROUTED_DESTINATIONS) SettingsIntentRouter.HOME_ROUTE else DefaultSettingsRegistry.route(destination)
