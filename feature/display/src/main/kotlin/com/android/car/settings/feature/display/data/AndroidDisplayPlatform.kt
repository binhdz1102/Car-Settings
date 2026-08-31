@file:SuppressLint("MissingPermission", "InlinedApi", "DiscouragedApi")

package com.android.car.settings.feature.display.data

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.app.time.Capabilities
import android.app.time.TimeConfiguration
import android.app.time.TimeManager
import android.app.time.TimeZoneConfiguration
import android.app.timedetector.TimeDetector
import android.app.timezonedetector.TimeZoneDetector
import android.car.Car
import android.car.CarOccupantZoneManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings
import android.text.format.DateFormat
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.core.common.rethrowIfCancellation
import com.android.car.settings.core.vehicle.CarServiceProvider
import com.android.car.settings.feature.display.domain.DateTimeState
import com.android.car.settings.feature.display.domain.DisplayState
import com.android.car.settings.feature.display.domain.GAMMA_SPACE_MAX
import com.android.car.settings.feature.display.domain.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Framework implementation of the AAOS Display settings controls. It follows Car Settings:
 * brightness is stored in linear space while the UI uses gamma space for a usable low-light ramp.
 */
@Singleton
internal class AndroidDisplayPlatform
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
        private val carServiceProvider: CarServiceProvider,
    ) : DisplayPlatform {
        private val resolver = context.contentResolver
        private val displayManager = context.getSystemService(DisplayManager::class.java)
        private val powerManager = context.getSystemService(PowerManager::class.java)
        private val userManager = context.getSystemService(UserManager::class.java)
        private val uiModeManager = context.getSystemService(UiModeManager::class.java)
        private val timeManager = context.getSystemService(TimeManager::class.java)
        private val timeDetector = context.getSystemService(TimeDetector::class.java)
        private val timeZoneDetector = context.getSystemService(TimeZoneDetector::class.java)
        private val deviceProtectedContext =
            if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()
        private val themePreferences: SharedPreferences =
            deviceProtectedContext.getSharedPreferences(THEME_PREFERENCES_NAME, Context.MODE_PRIVATE)
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val mutableState = MutableStateFlow(DisplayState())
        private val minimumBacklight = powerManager?.systemInt("getMinimumScreenBrightnessSetting") ?: 1
        private val maximumBacklight = powerManager?.systemInt("getMaximumScreenBrightnessSetting") ?: 255
        private val supportsVisibleBackgroundUsers =
            userManager?.systemBoolean("isVisibleBackgroundUsersSupported") == true

        @Volatile private var occupantDisplayId: Int? = null
        override val state: StateFlow<DisplayState> = mutableState.asStateFlow()

        private val settingObserver =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    scope.launch { refresh() }
                }
            }

        private val timeChangeReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    scope.launch { refresh() }
                }
            }

        init {
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                false,
                settingObserver,
            )
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
                false,
                settingObserver,
            )
            resolver.registerContentObserver(
                Settings.Global.getUriFor(FORCED_DAY_NIGHT_MODE),
                false,
                settingObserver,
            )
            resolver.registerContentObserver(
                Settings.Secure.getUriFor(UI_NIGHT_MODE),
                false,
                settingObserver,
            )
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AUTO_TIME),
                false,
                settingObserver,
            )
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AUTO_TIME_ZONE),
                false,
                settingObserver,
            )
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.TIME_12_24),
                false,
                settingObserver,
            )
            context.registerReceiver(
                timeChangeReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                    addAction(Intent.ACTION_TIME_TICK)
                },
                Context.RECEIVER_NOT_EXPORTED,
            )
            carServiceProvider.register { connectedCar, ready ->
                onCarLifecycleChanged(connectedCar, ready)
            }
            scope.launch { refresh() }
        }

        override suspend fun refresh(): ActionResult =
            execute {
                val linear = readLinearBrightness()
                mutableState.value =
                    DisplayState(
                        brightnessGamma = linearToGamma(linear, minimumBacklight, maximumBacklight),
                        adaptiveBrightnessEnabled =
                            Settings.System.getInt(
                                resolver,
                                Settings.System.SCREEN_BRIGHTNESS_MODE,
                                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                            ) != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                        adaptiveBrightnessAvailable = supportsAdaptiveBrightness(),
                        themeMode = readThemeMode(),
                        // The Automotive global is preferred when the image exposes it.  The
                        // app-owned preference below is a real, persisted fallback for emulator
                        // and OEM images that omit that optional setting, so Display always has a
                        // usable light/dark control instead of silently hiding the feature.
                        themeModeAvailable = true,
                        dateTime = readDateTimeState(),
                    )
            }

        override suspend fun setBrightness(gamma: Int): ActionResult =
            execute {
                val linear = gammaToLinear(gamma, minimumBacklight, maximumBacklight)
                val changedWithDisplayManager =
                    activeDisplayId != null &&
                        runCatching {
                            requireNotNull(displayManager).setSystemBrightness(
                                requireNotNull(activeDisplayId),
                                linear.toFloat() / maximumBacklight,
                            )
                        }.isSuccess
                if (!changedWithDisplayManager) {
                    check(Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, linear)) {
                        "The system did not allow changing display brightness"
                    }
                }
                refreshOrThrow()
            }

        override suspend fun setAdaptiveBrightness(enabled: Boolean): ActionResult =
            execute {
                check(supportsAdaptiveBrightness()) { "Adaptive brightness is not supported" }
                check(
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        if (enabled) {
                            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                        } else {
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                        },
                    ),
                ) { "The system did not allow changing adaptive brightness" }
                refreshOrThrow()
            }

        override suspend fun setThemeMode(mode: ThemeMode): ActionResult =
            execute {
                // Apply the theme mode across all available system layers.
                // 1. UiModeManager nightMode (system-wide) & applicationNightMode (per-process)
                if (uiModeManager != null) {
                    runCatching {
                        uiModeManager.nightMode = mode.toSystemNightMode()
                    }.onFailure { Timber.w(it, "Failed to set UiModeManager.nightMode") }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            uiModeManager.setApplicationNightMode(mode.toSystemNightMode())
                        }.onFailure { Timber.w(it, "Failed to set UiModeManager.setApplicationNightMode") }
                    }
                }

                // 2. UiModeManagerService secure setting
                runCatching {
                    Settings.Secure.putString(
                        resolver,
                        UI_NIGHT_MODE,
                        mode.toLegacyNightModeValue(),
                    )
                }.onFailure { Timber.w(it, "Failed to set Settings.Secure.UI_NIGHT_MODE") }

                // 3. Automotive forced-mode global setting
                if (hasGlobalThemeModeSetting()) {
                    runCatching {
                        Settings.Global.putInt(resolver, FORCED_DAY_NIGHT_MODE, mode.toPlatformValue())
                    }.onFailure { Timber.w(it, "Failed to set Settings.Global.FORCED_DAY_NIGHT_MODE") }
                }

                // 4. Persisted app preference (guarantees local persistence & fallback)
                check(
                    themePreferences
                        .edit()
                        .putInt(KEY_THEME_MODE, mode.toPlatformValue())
                        .commit(),
                ) { "The system did not allow changing the day/night theme" }

                Timber.i("Applied system day/night theme mode %s", mode)
                refreshOrThrow()
            }

        override suspend fun setAutoTime(enabled: Boolean): ActionResult =
            execute {
                check(updateAutoTime(enabled)) { "Automatic time is not supported" }
                refreshOrThrow()
            }

        override suspend fun setAutoTimeZone(enabled: Boolean): ActionResult =
            execute {
                check(updateAutoTimeZone(enabled)) { "Automatic time zone is not supported" }
                refreshOrThrow()
            }

        override suspend fun setUse24HourFormat(enabled: Boolean): ActionResult =
            execute {
                check(
                    Settings.System.putString(
                        resolver,
                        Settings.System.TIME_12_24,
                        if (enabled) HOURS_24 else HOURS_12,
                    ),
                ) { "The system did not allow changing the time format" }
                refreshOrThrow()
            }

        override suspend fun setManualTime(epochMillis: Long): ActionResult =
            execute {
                check(!readDateTimeState().autoTimeEnabled) {
                    "Turn off automatic time before setting date or time"
                }
                check(suggestManualTime(epochMillis)) {
                    "The system rejected the manual time suggestion"
                }
                refreshOrThrow()
            }

        override suspend fun setManualTimeZone(timeZoneId: String): ActionResult =
            execute {
                check(TimeZone.getAvailableIDs().contains(timeZoneId)) { "Unknown time zone" }
                check(!readDateTimeState().autoTimeZoneEnabled) {
                    "Turn off automatic time zone before choosing a time zone"
                }
                // Some AAOS emulator images return `false` when the requested zone already
                // matches the current zone, even though the state is valid and no write is
                // needed. Treat that idempotent case as success; a genuinely rejected change
                // still surfaces a safe ActionResult.Failure to the UI/logcat.
                check(
                    suggestManualTimeZone(timeZoneId) || TimeZone.getDefault().id == timeZoneId,
                ) {
                    "The system rejected the manual time zone suggestion"
                }
                refreshOrThrow()
            }

        private fun readLinearBrightness(): Int {
            val displayId = activeDisplayId
            if (displayId != null) {
                val brightness = requireNotNull(displayManager).getSystemBrightness(displayId)
                if (!brightness.isNaN() && brightness >= 0f) {
                    return (brightness * maximumBacklight)
                        .roundToInt()
                        .coerceIn(minimumBacklight, maximumBacklight)
                }
            }
            return Settings.System
                .getInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    maximumBacklight,
                ).coerceIn(minimumBacklight, maximumBacklight)
        }

        private fun supportsAdaptiveBrightness(): Boolean {
            val resourceId =
                context.resources.getIdentifier(
                    "config_automatic_brightness_available",
                    "bool",
                    "android",
                )
            return resourceId != 0 && context.resources.getBoolean(resourceId)
        }

        private fun readThemeMode(): ThemeMode {
            // 1. Automotive Global forced mode if supported by the image
            if (hasGlobalThemeModeSetting()) {
                val forcedValue = Settings.Global.getInt(
                    resolver,
                    FORCED_DAY_NIGHT_MODE,
                    themePreferences.getInt(KEY_THEME_MODE, THEME_AUTO),
                )
                return when (forcedValue) {
                    THEME_DAY -> ThemeMode.DAY
                    THEME_NIGHT -> ThemeMode.NIGHT
                    else -> ThemeMode.AUTO
                }
            }

            // 2. Explicitly saved theme preference
            val savedMode = themePreferences.getInt(KEY_THEME_MODE, THEME_AUTO)
            if (savedMode != THEME_AUTO) {
                return when (savedMode) {
                    THEME_DAY -> ThemeMode.DAY
                    THEME_NIGHT -> ThemeMode.NIGHT
                    else -> ThemeMode.AUTO
                }
            }

            // 3. System night mode from UiModeManager
            if (uiModeManager != null) {
                uiModeManager.nightMode.toThemeMode()?.let { return it }
            }

            return ThemeMode.AUTO
        }

        private fun hasGlobalThemeModeSetting(): Boolean =
            runCatching {
                Settings.Global.getString(resolver, FORCED_DAY_NIGHT_MODE) != null
            }.getOrDefault(false)

        private fun readDateTimeState(): DateTimeState {
            val autoTimeEnabled = readAutoTimeEnabled()
            val autoTimeZoneEnabled = readAutoTimeZoneEnabled()
            return DateTimeState(
                currentEpochMillis = System.currentTimeMillis(),
                autoTimeEnabled = autoTimeEnabled,
                autoTimeAvailable = canConfigureAutoTime(),
                autoTimeZoneEnabled = autoTimeZoneEnabled,
                autoTimeZoneAvailable = canConfigureAutoTimeZone(),
                use24HourFormat = DateFormat.is24HourFormat(context),
                timeZoneId = TimeZone.getDefault().id,
                canSetManualTime = !autoTimeEnabled && canSetManualTime(),
                canSetManualTimeZone = !autoTimeZoneEnabled && canSetManualTimeZone(),
            )
        }

        @Synchronized
        private fun onCarLifecycleChanged(
            connectedCar: Car,
            ready: Boolean,
        ) {
            occupantDisplayId =
                if (ready && supportsVisibleBackgroundUsers) {
                    connectedCar.findMyMainDisplayId()
                } else {
                    null
                }
            scope.launch { refresh() }
        }

        private fun Car.findMyMainDisplayId(): Int? =
            runCatching {
                val manager = getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE) as? CarOccupantZoneManager
                val occupantZone = manager?.getMyOccupantZone() ?: return@runCatching null
                manager
                    .getDisplayForOccupant(occupantZone, CarOccupantZoneManager.DISPLAY_TYPE_MAIN)
                    ?.displayId
            }.getOrNull()

        private val activeDisplayId: Int?
            get() = occupantDisplayId?.takeIf { displayManager != null }

        private fun readAutoTimeEnabled(): Boolean =
            runCatching {
                timeManager
                    ?.getTimeCapabilitiesAndConfig()
                    ?.getConfiguration()
                    ?.isAutoDetectionEnabled
            }.getOrNull()
                ?: (Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME, 0) != 0)

        private fun readAutoTimeZoneEnabled(): Boolean =
            runCatching {
                timeManager
                    ?.getTimeZoneCapabilitiesAndConfig()
                    ?.getConfiguration()
                    ?.isAutoDetectionEnabled
            }.getOrNull()
                ?: (Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME_ZONE, 0) != 0)

        private fun canConfigureAutoTime(): Boolean =
            runCatching {
                timeManager
                    ?.getTimeCapabilitiesAndConfig()
                    ?.getCapabilities()
                    ?.getConfigureAutoDetectionEnabledCapability()
                    ?.let { it == Capabilities.CAPABILITY_POSSESSED }
            }.getOrNull() ?: true

        private fun canConfigureAutoTimeZone(): Boolean =
            runCatching {
                timeManager
                    ?.getTimeZoneCapabilitiesAndConfig()
                    ?.getCapabilities()
                    ?.getConfigureAutoDetectionEnabledCapability()
                    ?.let { it == Capabilities.CAPABILITY_POSSESSED }
            }.getOrNull() ?: true

        private fun canSetManualTime(): Boolean =
            runCatching {
                timeManager
                    ?.getTimeCapabilitiesAndConfig()
                    ?.getCapabilities()
                    ?.getSetManualTimeCapability()
                    ?.let { it == Capabilities.CAPABILITY_POSSESSED }
            }.getOrNull() ?: true

        private fun canSetManualTimeZone(): Boolean =
            runCatching {
                timeManager
                    ?.getTimeZoneCapabilitiesAndConfig()
                    ?.getCapabilities()
                    ?.getSetManualTimeZoneCapability()
                    ?.let { it == Capabilities.CAPABILITY_POSSESSED }
            }.getOrNull() ?: true

        private fun updateAutoTime(enabled: Boolean): Boolean =
            runCatching {
                timeManager?.updateTimeConfiguration(
                    TimeConfiguration.Builder().setAutoDetectionEnabled(enabled).build(),
                )
            }.getOrNull()
                ?: Settings.Global.putInt(resolver, Settings.Global.AUTO_TIME, if (enabled) 1 else 0)

        private fun updateAutoTimeZone(enabled: Boolean): Boolean =
            runCatching {
                timeManager?.updateTimeZoneConfiguration(
                    TimeZoneConfiguration.Builder().setAutoDetectionEnabled(enabled).build(),
                )
            }.getOrNull()
                ?: Settings.Global.putInt(resolver, Settings.Global.AUTO_TIME_ZONE, if (enabled) 1 else 0)

        /** Uses the same suggestion API as Car Settings' date and time pickers. */
        private fun suggestManualTime(epochMillis: Long): Boolean =
            runCatching {
                requireNotNull(timeDetector).suggestManualTime(
                    TimeDetector.createManualTimeSuggestion(
                        epochMillis,
                        "MySystemApp: Set date or time",
                    ),
                )
            }.getOrDefault(false)

        /** Uses the same suggestion API as Car Settings' time-zone picker. */
        private fun suggestManualTimeZone(timeZoneId: String): Boolean =
            runCatching {
                requireNotNull(timeZoneDetector).suggestManualTimeZone(
                    TimeZoneDetector.createManualTimeZoneSuggestion(
                        timeZoneId,
                        "MySystemApp: Set time zone",
                    ),
                )
            }.getOrDefault(false)

        private suspend fun execute(block: suspend () -> Unit): ActionResult =
            withContext(dispatcher) {
                try {
                    block()
                    ActionResult.Success
                } catch (throwable: Throwable) {
                    throwable.rethrowIfCancellation()
                    ActionResult.Failure(
                        message = throwable.message ?: throwable.javaClass.simpleName,
                        cause = throwable,
                    )
                }
            }

        private suspend fun refreshOrThrow() {
            check(refresh() == ActionResult.Success) { "Unable to refresh display state" }
        }
    }

private const val FORCED_DAY_NIGHT_MODE = "android.car.FORCED_DAY_NIGHT_MODE"
private const val UI_NIGHT_MODE = "ui_night_mode"
private const val THEME_PREFERENCES_NAME = "display_theme"
private const val KEY_THEME_MODE = "theme_mode"
private const val HOURS_12 = "12"
private const val HOURS_24 = "24"
private const val THEME_AUTO = 0
private const val THEME_DAY = 1
private const val THEME_NIGHT = 2
private const val GAMMA_R = 0.5f
private const val GAMMA_A = 0.17883277f
private const val GAMMA_B = 0.28466892f
private const val GAMMA_C = 0.55991073f
private const val LINEAR_SCALE = 12f

private fun gammaToLinear(
    gamma: Int,
    minimum: Int,
    maximum: Int,
): Int {
    val normalized = gamma.coerceIn(0, GAMMA_SPACE_MAX).toFloat() / GAMMA_SPACE_MAX
    val linear =
        if (normalized <= GAMMA_R) {
            (normalized / GAMMA_R) * (normalized / GAMMA_R)
        } else {
            exp(((normalized - GAMMA_C) / GAMMA_A).toDouble()).toFloat() + GAMMA_B
        }
    return (minimum + (maximum - minimum) * linear / LINEAR_SCALE)
        .roundToInt()
        .coerceIn(minimum, maximum)
}

private fun linearToGamma(
    linear: Int,
    minimum: Int,
    maximum: Int,
): Int {
    val normalized =
        (linear.coerceIn(minimum, maximum) - minimum).toFloat() / (maximum - minimum) * LINEAR_SCALE
    val gamma =
        if (normalized <= 1f) {
            GAMMA_R * sqrt(normalized)
        } else {
            GAMMA_A * ln((normalized - GAMMA_B).toDouble()).toFloat() + GAMMA_C
        }
    return (gamma * GAMMA_SPACE_MAX).roundToInt().coerceIn(0, GAMMA_SPACE_MAX)
}

private fun ThemeMode.toPlatformValue(): Int =
    when (this) {
        ThemeMode.AUTO -> THEME_AUTO
        ThemeMode.DAY -> THEME_DAY
        ThemeMode.NIGHT -> THEME_NIGHT
    }

/** Maps a UI ThemeMode onto UiModeManager's system night mode constants (API 31+). */
private fun ThemeMode.toSystemNightMode(): Int =
    when (this) {
        ThemeMode.AUTO -> UiModeManager.MODE_NIGHT_AUTO
        ThemeMode.DAY -> UiModeManager.MODE_NIGHT_NO
        ThemeMode.NIGHT -> UiModeManager.MODE_NIGHT_YES
    }

/** UiModeManagerService's secure-setting representation used before API 31. */
private fun ThemeMode.toLegacyNightModeValue(): String =
    when (this) {
        ThemeMode.AUTO -> "auto"
        ThemeMode.DAY -> "no"
        ThemeMode.NIGHT -> "yes"
    }

private fun Int.toThemeMode(): ThemeMode? =
    when (this) {
        UiModeManager.MODE_NIGHT_NO -> ThemeMode.DAY
        UiModeManager.MODE_NIGHT_YES -> ThemeMode.NIGHT
        UiModeManager.MODE_NIGHT_AUTO -> ThemeMode.AUTO
        else -> null
    }

private fun Any.systemInt(methodName: String): Int? = runCatching { javaClass.getMethod(methodName).invoke(this) as Int }.getOrNull()

private fun Any.systemBoolean(methodName: String): Boolean? =
    runCatching { javaClass.getMethod(methodName).invoke(this) as Boolean }.getOrNull()

private fun DisplayManager.getSystemBrightness(displayId: Int): Float =
    javaClass.getMethod("getBrightness", Int::class.javaPrimitiveType).invoke(this, displayId) as Float

private fun DisplayManager.setSystemBrightness(
    displayId: Int,
    brightness: Float,
) {
    javaClass
        .getMethod("setBrightness", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType)
        .invoke(this, displayId, brightness)
}

private fun Any.invokeMethod(
    methodName: String,
    vararg arguments: Any,
): Any =
    requireNotNull(
        javaClass.methods
            .first { method -> method.name == methodName && method.parameterCount == arguments.size }
            .invoke(this, *arguments),
    )
