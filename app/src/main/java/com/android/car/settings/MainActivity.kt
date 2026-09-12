package com.android.car.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.android.car.settings.core.settings.DefaultSettingsRegistry
import com.android.car.settings.core.settings.SettingsCapability
import com.android.car.settings.core.settings.SettingsCategoryId
import com.android.car.settings.core.settings.SettingsDestinationDefinition
import com.android.car.settings.core.settings.SettingsDestinationId
import com.android.car.settings.core.settings.isAllowedWhileRestricted
import com.android.car.settings.core.ui.MySystemTheme
import com.android.car.settings.core.ui.SettingsAppShell
import com.android.car.settings.core.ui.SettingsCardDialog
import com.android.car.settings.core.ui.SettingsCategoryUiModel
import com.android.car.settings.core.ui.SettingsFocusEntryState
import com.android.car.settings.core.ui.VehicleDialogBackGuard
import com.android.car.settings.core.ui.rememberSettingsFocusEntryState
import com.android.car.settings.core.ui.setSafeRotaryContent
import com.android.car.settings.core.ui.shouldRequestDefaultFocus
import com.android.car.settings.core.vehicle.VehicleUxPolicy
import com.android.car.settings.core.vehicle.VehicleUxPolicyState
import com.android.car.settings.feature.accessibility.presentation.accessibilityGraph
import com.android.car.settings.feature.applications.presentation.applicationsGraph
import com.android.car.settings.feature.assistantvoice.presentation.assistantVoiceGraph
import com.android.car.settings.feature.bluetooth.presentation.bluetoothGraph
import com.android.car.settings.feature.display.domain.DisplayRepository
import com.android.car.settings.feature.display.domain.DisplayState
import com.android.car.settings.feature.display.domain.ThemeMode
import com.android.car.settings.feature.display.presentation.displayGraph
import com.android.car.settings.feature.doorcontrol.presentation.doorControlGraph
import com.android.car.settings.feature.driverassistance.presentation.driverAssistanceGraph
import com.android.car.settings.feature.hvac.presentation.hvacGraph
import com.android.car.settings.feature.location.presentation.locationGraph
import com.android.car.settings.feature.notifications.presentation.notificationsGraph
import com.android.car.settings.feature.privacy.presentation.privacyGraph
import com.android.car.settings.feature.profileaccounts.presentation.profileAccountsGraph
import com.android.car.settings.feature.search.presentation.searchGraph
import com.android.car.settings.feature.seatcontrol.presentation.seatControlGraph
import com.android.car.settings.feature.security.presentation.securityGraph
import com.android.car.settings.feature.sound.presentation.soundGraph
import com.android.car.settings.feature.system.presentation.systemGraph
import com.android.car.settings.feature.vehiclelighting.presentation.vehicleLightingGraph
import com.android.car.settings.feature.wifi.presentation.wifiGraph
import com.android.car.settings.navigation.SettingsIntentRouter
import com.b231001.bmaterial.ccp.rotaryfocus.LocalIsInTouchMode
import com.b231001.bmaterial.ccp.rotaryfocus.RotaryFocusController
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var vehicleUxPolicy: VehicleUxPolicy

    @Inject lateinit var displayRepository: DisplayRepository

    private val rotaryFocusController = RotaryFocusController()
    private var launchIntent by mutableStateOf<Intent?>(null)

    private val wifiPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            Timber.i("Wi-Fi capability permission result: %s", results)
        }

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            Timber.i("Bluetooth capability permission result: %s", results)
        }

    @Suppress("LongMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // RotaryFocusDialog is a native window. On some AAOS images the Back key that dismisses
        // that window is delivered once more to the activity while the window is being removed.
        // Keep this callback at the bottom of the dispatcher stack so Compose destinations still
        // own normal Back handling; it only consumes the explicit duplicate armed by the popup.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (rotaryFocusController.isDirectManipulationActive) {
                        rotaryFocusController.exitDirectManipulation()
                        return
                    }
                    if (VehicleDialogBackGuard.consumePending()) return
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            },
        )
        launchIntent = intent
        enableEdgeToEdge()
        Timber.i("Car-Settings created; action=%s data=%s", intent.action, intent.data)
        setSafeRotaryContent(hostId = "main-window", controller = rotaryFocusController) {
            val displayState by displayRepository.state.collectAsState(initial = DisplayState())
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme =
                when (displayState.themeMode) {
                    ThemeMode.DAY -> false
                    ThemeMode.NIGHT -> true
                    ThemeMode.AUTO -> systemDarkTheme
                }
            MySystemTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                val focusEntryState = rememberSettingsFocusEntryState()
                val isInTouchMode = LocalIsInTouchMode.current
                val uxPolicyState by vehicleUxPolicy.state.collectAsState()
                val requestedDefinition = destinationFor(launchIntent)
                var blockedDestination by remember { mutableStateOf<SettingsDestinationDefinition?>(null) }
                val requestedAllowed = requestedDefinition.isAllowed(uxPolicyState)
                val defaultRoute = DefaultSettingsRegistry.route(SettingsDestinationId.VEHICLE)
                val homeRoute = DefaultSettingsRegistry.route(SettingsDestinationId.HOME)
                val searchRoute = DefaultSettingsRegistry.route(SettingsDestinationId.SEARCH)
                val requestedRoute = requestedDefinition.route
                val categoryUi =
                    DefaultSettingsRegistry.categories.map { category ->
                        SettingsCategoryUiModel(
                            key = category.id.name,
                            label = categoryLabel(category.id),
                            icon = categoryIcon(category.id),
                        )
                    }
                val requestedDestination =
                    resolveInitialDestinationRoute(
                        requestedRoute = requestedRoute,
                        requestedAllowed = requestedAllowed,
                        homeRoute = homeRoute,
                        defaultRoute = defaultRoute,
                    )
                Timber.d("Cold-start destination=%s", requestedDestination)

                LaunchedEffect(isInTouchMode) {
                    if (isInTouchMode) focusEntryState.cancelPendingFocus()
                }

                fun openDestination(
                    destinationId: SettingsDestinationId,
                    replaceSearch: Boolean = false,
                    replaceStack: Boolean = false,
                ) {
                    // A pending root handoff belongs to exactly one forward action. Any new
                    // navigation attempt supersedes it, including a touch action or a blocked
                    // destination which never reaches the NavHost.
                    focusEntryState.cancelPendingFocus()
                    val definition = DefaultSettingsRegistry.definition(destinationId)
                    if (!definition.isAllowed(uxPolicyState)) {
                        blockedDestination = definition
                        return
                    }
                    if (destinationId in UNROUTED_DESTINATIONS) return
                    requestCapabilities(definition.capabilities)
                    val route = DefaultSettingsRegistry.route(destinationId)
                    // Reselecting the already visible category is a focus handoff, not a route
                    // change. Re-navigating the same root with popUpTo briefly unregisters its
                    // FocusAreas; the one-shot request can then be consumed before the new tree
                    // is attached and B-Material falls back to the parked Search item.
                    val navigationWillChange =
                        shouldNavigateToDestination(navController.currentDestination?.route, route)
                    val preparedVehicleFocus =
                        navigationWillChange &&
                            prepareVehicleDestinationFocus(
                                destinationId = destinationId,
                                route = route,
                                isInTouchMode = isInTouchMode,
                                focusEntryState = focusEntryState,
                                parkFocus = rotaryFocusController::parkFocus,
                            )
                    if (navigationWillChange) {
                        navController.navigate(route) {
                            if (replaceSearch) popUpTo(searchRoute) { inclusive = true }
                            if (replaceStack) {
                                // A category selection replaces the whole destination stack. Popping
                                // only to startDestinationId retained the launch category underneath
                                // every later category, so Back could unexpectedly re-select it.
                                popUpTo(navController.graph.id) { inclusive = false }
                            }
                            launchSingleTop = true
                        }
                    }
                    if (
                        !preparedVehicleFocus &&
                        shouldRequestDefaultFocus(
                            isInTouchMode = isInTouchMode,
                            navigationAccepted = true,
                        )
                    ) {
                        focusEntryState.requestDefaultFocus(route)
                    }
                }
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentEntryId = currentBackStackEntry?.id
                val currentDefinition =
                    DefaultSettingsRegistry.destinationForRoute(
                        currentBackStackEntry?.destination?.route,
                    )
                var activeCategory by remember(requestedDestination) {
                    mutableStateOf(
                        DefaultSettingsRegistry
                            .destinationForRoute(requestedDestination)
                            ?.categoryId
                            ?.name
                            ?: SettingsCategoryId.VEHICLE.name,
                    )
                }
                // During a pop Navigation briefly publishes graph/null entries. Retain the last
                // real destination instead of flashing a stale launch category in the rail.
                LaunchedEffect(currentDefinition?.categoryId) {
                    currentDefinition?.let { activeCategory = it.categoryId.name }
                }
                val backNavigationGate = remember { NavigationBackGate() }
                LaunchedEffect(currentEntryId) {
                    backNavigationGate.onDestinationChanged(currentEntryId)
                }
                val navigateBack: () -> Unit = {
                    focusEntryState.cancelPendingFocus()
                    if (backNavigationGate.tryStart(currentEntryId)) {
                        if (!navController.popBackStack()) {
                            backNavigationGate.release()
                            finish()
                        }
                    }
                }
                val navigateForward: (String) -> Unit = { route ->
                    focusEntryState.prepareForwardNavigation(isInTouchMode)
                    navController.navigate(route)
                }

                SettingsAppShell(
                    categories = categoryUi,
                    selectedCategoryKey = activeCategory,
                    onCategorySelected = { category ->
                        val root =
                            DefaultSettingsRegistry.category(SettingsCategoryId.valueOf(category.key)).rootDestination
                        openDestination(root, replaceStack = true)
                    },
                    onSearch = { openDestination(SettingsDestinationId.SEARCH) },
                    focusEntryState = focusEntryState,
                    headerTitle = stringResource(R.string.settings_shell_title),
                ) {
                    NavHost(
                        navController = navController,
                        // Resolve the launch intent before composing the graph. Starting on the
                        // Vehicle landing page and navigating to a deep-linked destination later
                        // effect briefly composes two detail FocusAreas with identical bounds;
                        // the native CCP validator correctly reports that as an overlap.  A
                        // route-aware start keeps the first frame single-owner while the normal
                        // launcher resolves directly to Vehicle.
                        startDestination = requestedDestination,
                        // Registering two destinations during a cross-fade creates two competing
                        // CCP focus trees. Screens own their content transition; navigation swaps
                        // the focus destination atomically and restores the parked opener target.
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None },
                    ) {
                        // Keep the legacy home route as a compatibility deep link, but do not render
                        // the retired card-grid. Every entry point now lands in the persistent shell.
                        composable(homeRoute) {
                            LaunchedEffect(Unit) {
                                focusEntryState.cancelPendingFocus()
                                navController.navigate(defaultRoute) {
                                    popUpTo(homeRoute) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                        composable(DefaultSettingsRegistry.route(SettingsDestinationId.VEHICLE)) {
                            VehicleLandingScreen(
                                onDestination = ::openDestination,
                                onBack = navigateBack,
                            )
                        }
                        wifiGraph(
                            onBack = navigateBack,
                            onNavigateForward = navigateForward,
                        )
                        bluetoothGraph(
                            onBack = navigateBack,
                            onNavigateForward = navigateForward,
                        )
                        soundGraph(
                            onBack = navigateBack,
                            onNavigateForward = navigateForward,
                        )
                        displayGraph(
                            onBack = navigateBack,
                            onNavigateForward = navigateForward,
                        )
                        applicationsGraph(
                            onBack = navigateBack,
                            onNavigateForward = navigateForward,
                        )
                        profileAccountsGraph(
                            onBack = navigateBack,
                            onNavigateForward = navigateForward,
                        )
                        systemGraph(
                            onBack = navigateBack,
                            onNavigateForward = navigateForward,
                        )
                        notificationsGraph(
                            onBack = navigateBack,
                            onNavigateForward = navigateForward,
                        )
                        privacyGraph(
                            onBack = navigateBack,
                            onNavigateForward = navigateForward,
                        )
                        securityGraph(
                            onBack = navigateBack,
                            onNavigateForward = navigateForward,
                        )
                        searchGraph(
                            onBack = navigateBack,
                            onDestination = { destination -> openDestination(destination, replaceSearch = true) },
                        )
                        hvacGraph(onBack = navigateBack)
                        driverAssistanceGraph(onBack = navigateBack)
                        seatControlGraph(onBack = navigateBack)
                        vehicleLightingGraph(onBack = navigateBack)
                        doorControlGraph(onBack = navigateBack)
                        locationGraph(
                            onBack = navigateBack,
                            onNavigateForward = navigateForward,
                        )
                        accessibilityGraph(onBack = navigateBack)
                        assistantVoiceGraph(onBack = navigateBack)
                    }
                }
                LaunchedEffect(launchIntent, requestedDefinition.id, requestedAllowed) {
                    // Intents are programmatic navigation, not rotary entry. A new intent must
                    // never replay a token created by an earlier category/search action, even
                    // when it resolves to the destination already on screen.
                    focusEntryState.cancelPendingFocus()
                    if (!requestedAllowed) {
                        blockedDestination = requestedDefinition
                        return@LaunchedEffect
                    }
                    requestCapabilities(requestedDefinition.capabilities)
                    if (requestedDestination != defaultRoute &&
                        navController.currentDestination?.route != requestedDestination
                    ) {
                        Timber.d("Routing new intent to %s", requestedDestination)
                        navController.navigate(requestedDestination) {
                            launchSingleTop = true
                        }
                    }
                }
                LaunchedEffect(currentBackStackEntry?.destination?.route, uxPolicyState) {
                    val activeDefinition =
                        DefaultSettingsRegistry.destinationForRoute(currentBackStackEntry?.destination?.route)
                            ?: return@LaunchedEffect
                    if (!activeDefinition.isAllowed(uxPolicyState)) {
                        focusEntryState.cancelPendingFocus()
                        blockedDestination = activeDefinition
                        if (!navController.popBackStack()) {
                            navController.navigate(defaultRoute) { launchSingleTop = true }
                        }
                    }
                }
                blockedDestination?.let {
                    SettingsCardDialog(
                        dialogKey = "ux-restricted-${it.id.name}",
                        title = stringResource(R.string.ux_restricted_title),
                        message = stringResource(R.string.ux_restricted_message),
                        onDismissRequest = { blockedDestination = null },
                        confirmLabel = stringResource(R.string.action_ok),
                    )
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

    /**
     * Resolves the launch intent through the AAOS Settings contract router, then maps the route
     * back to a registry definition for UX gating and category tracking.
     */
    private fun destinationFor(intent: Intent?): SettingsDestinationDefinition {
        val route = SettingsIntentRouter.destinationFor(intent)
        return DefaultSettingsRegistry.destinationForRoute(route)
            ?: DefaultSettingsRegistry.definition(SettingsDestinationId.BLUETOOTH)
    }

    private fun requestCapabilities(capabilities: Set<SettingsCapability>) {
        if (SettingsCapability.WIFI in capabilities) requestWifiPermissions()
        if (SettingsCapability.BLUETOOTH in capabilities) requestBluetoothPermissions()
    }

    private fun requestWifiPermissions() {
        launchMissingPermissions(
            ConnectivityPermissionPolicy.wifiPermissions(Build.VERSION.SDK_INT),
            wifiPermissionLauncher,
        )
    }

    private fun requestBluetoothPermissions() {
        launchMissingPermissions(
            ConnectivityPermissionPolicy.bluetoothPermissions(Build.VERSION.SDK_INT),
            bluetoothPermissionLauncher,
        )
    }

    private fun launchMissingPermissions(
        permissions: List<String>,
        launcher: ActivityResultLauncher<Array<String>>,
    ) {
        val missing =
            permissions.filter {
                checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        if (missing.isNotEmpty()) {
            Timber.d("Requesting capability permissions: %s", missing)
            launcher.launch(missing.toTypedArray())
        }
    }

    private companion object {
        /** Destinations that are not part of this build; the shell never offers them. */
        private val UNROUTED_DESTINATIONS =
            setOf(
                SettingsDestinationId.LAUNCHER,
                SettingsDestinationId.SYSTEM_UI_CATALOG,
                SettingsDestinationId.SYSTEM_UI_COCKPIT,
                SettingsDestinationId.SYSTEM_UI_NOTIFICATION_CENTER,
                SettingsDestinationId.SYSTEM_UI_USER_CENTER,
            )
    }
}

/**
 * Normal launcher starts resolve to the app's real landing route before NavHost is composed.
 * Rendering the legacy [homeRoute] first briefly selected Connected devices, then redirected to
 * Vehicle on the next frame.
 */
internal fun resolveInitialDestinationRoute(
    requestedRoute: String?,
    requestedAllowed: Boolean,
    homeRoute: String,
    defaultRoute: String,
): String =
    when {
        !requestedAllowed -> defaultRoute
        requestedRoute == null || requestedRoute == homeRoute -> defaultRoute
        else -> requestedRoute
    }

internal fun isCategoryRootDestination(destinationId: SettingsDestinationId): Boolean =
    DefaultSettingsRegistry.categories.any { it.rootDestination == destinationId }

internal fun shouldNavigateToDestination(
    currentRoute: String?,
    destinationRoute: String,
): Boolean = currentRoute != destinationRoute

internal fun isVehicleDestination(destinationId: SettingsDestinationId): Boolean =
    when (destinationId) {
        SettingsDestinationId.HVAC,
        SettingsDestinationId.DRIVER_ASSISTANCE,
        SettingsDestinationId.SEAT_CONTROL,
        SettingsDestinationId.VEHICLE_LIGHTING,
        SettingsDestinationId.DOOR_CONTROL,
        -> true
        else -> false
    }

/** Arms and parks only Vehicle route handoffs before the loading destination replaces its source. */
internal fun prepareVehicleDestinationFocus(
    destinationId: SettingsDestinationId,
    route: String,
    isInTouchMode: Boolean,
    focusEntryState: SettingsFocusEntryState,
    parkFocus: () -> Boolean,
): Boolean {
    if (!isVehicleDestination(destinationId) || isInTouchMode) return false
    val request = focusEntryState.requestDefaultFocus(route)
    val parked = parkFocus()
    Timber.d(
        "VehicleFocus prepare destination=%s route=%s request=%d parked=%s",
        destinationId,
        route,
        request.requestId,
        parked,
    )
    return true
}

@Composable
private fun categoryLabel(id: SettingsCategoryId): String =
    stringResource(
        when (id) {
            SettingsCategoryId.CONNECTED_DEVICES -> R.string.settings_category_connected_devices
            SettingsCategoryId.NETWORK_INTERNET -> R.string.settings_category_network_internet
            SettingsCategoryId.NOTIFICATIONS -> R.string.settings_category_notifications
            SettingsCategoryId.SOUND -> R.string.settings_category_sound
            SettingsCategoryId.DISPLAY -> R.string.settings_category_display
            SettingsCategoryId.PROFILE -> R.string.settings_category_profile
            SettingsCategoryId.LOCATION -> R.string.settings_category_location
            SettingsCategoryId.PRIVACY -> R.string.settings_category_privacy
            SettingsCategoryId.ACCESSIBILITY -> R.string.settings_category_accessibility
            SettingsCategoryId.SECURITY -> R.string.settings_category_security
            SettingsCategoryId.APPS -> R.string.settings_category_apps
            SettingsCategoryId.ASSISTANCE_VOICE -> R.string.settings_category_assistance_voice
            SettingsCategoryId.VEHICLE -> R.string.settings_category_vehicle
            SettingsCategoryId.SYSTEM -> R.string.settings_category_system
        },
    )

private fun categoryIcon(id: SettingsCategoryId) =
    when (id) {
        SettingsCategoryId.CONNECTED_DEVICES -> Icons.Outlined.Devices
        SettingsCategoryId.NETWORK_INTERNET -> Icons.Outlined.Wifi
        SettingsCategoryId.NOTIFICATIONS -> Icons.Outlined.Notifications
        SettingsCategoryId.SOUND -> Icons.Outlined.VolumeUp
        SettingsCategoryId.DISPLAY -> Icons.Outlined.DisplaySettings
        SettingsCategoryId.PROFILE -> Icons.Outlined.Person
        SettingsCategoryId.LOCATION -> Icons.Outlined.LocationOn
        SettingsCategoryId.PRIVACY -> Icons.Outlined.PrivacyTip
        SettingsCategoryId.ACCESSIBILITY -> Icons.Outlined.AccessibilityNew
        SettingsCategoryId.SECURITY -> Icons.Outlined.Lock
        SettingsCategoryId.APPS -> Icons.Outlined.Apps
        SettingsCategoryId.ASSISTANCE_VOICE -> Icons.Outlined.RecordVoiceOver
        SettingsCategoryId.VEHICLE -> Icons.Outlined.DirectionsCar
        SettingsCategoryId.SYSTEM -> Icons.Outlined.Devices
    }

private fun SettingsDestinationDefinition.isAllowed(state: VehicleUxPolicyState): Boolean =
    state is VehicleUxPolicyState.Unrestricted || uxSafetyClass.isAllowedWhileRestricted()

internal object ConnectivityPermissionPolicy {
    @SuppressLint("InlinedApi")
    fun wifiPermissions(sdkInt: Int): List<String> =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    @SuppressLint("InlinedApi")
    fun bluetoothPermissions(sdkInt: Int): List<String> =
        if (sdkInt >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            emptyList()
        }
}
