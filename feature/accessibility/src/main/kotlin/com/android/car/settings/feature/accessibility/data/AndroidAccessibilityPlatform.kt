package com.android.car.settings.feature.accessibility.data

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.core.common.rethrowIfCancellation
import com.android.car.settings.feature.accessibility.domain.AccessibilityServiceEntry
import com.android.car.settings.feature.accessibility.domain.AccessibilityState
import com.android.car.settings.feature.accessibility.domain.CaptionTextSize
import com.android.car.settings.feature.accessibility.domain.CaptionTextStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AndroidAccessibilityPlatform
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : AccessibilityPlatform {
        private val resolver = context.contentResolver
        private val manager = context.getSystemService(AccessibilityManager::class.java)
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val mutableState = MutableStateFlow(AccessibilityState())

        override val state: StateFlow<AccessibilityState> = mutableState.asStateFlow()

        init {
            scope.launch { refresh() }
        }

        override suspend fun refresh(): ActionResult =
            execute {
                val enabledServices = readEnabledServices()
                val services = loadServices(enabledServices)
                val screenReader = services.firstOrNull { entry -> isSpokenService(entry.componentName) }
                val captionSize =
                    readSecureString(SECURE_CAPTIONING_FONT_SCALE)
                        ?.toFloatOrNull()
                        ?: CaptionTextSize.DEFAULT.value
                val captionStyle =
                    readSecureString(SECURE_CAPTIONING_PRESET)
                        ?.toIntOrNull()
                        ?: CaptionTextStyle.BY_APP.value
                mutableState.value =
                    AccessibilityState(
                        captionsEnabled =
                            readSecureString(SECURE_CAPTIONING_ENABLED)
                                ?.toIntOrNull() == 1,
                        captionTextSize =
                            CaptionTextSize.values().minByOrNull {
                                kotlin.math.abs(it.value - captionSize)
                            } ?: CaptionTextSize.DEFAULT,
                        captionTextStyle =
                            CaptionTextStyle.values().firstOrNull { it.value == captionStyle }
                                ?: CaptionTextStyle.BY_APP,
                        screenReaderSupported = screenReader != null,
                        screenReaderName = screenReader?.label ?: "Screen reader",
                        screenReaderEnabled = screenReader?.enabled == true,
                        screenReaderComponent = screenReader?.componentName,
                        screenReaderSettingsActivity = screenReader?.settingsActivity,
                        services = services,
                    )
            }

        override suspend fun setCaptionsEnabled(enabled: Boolean): ActionResult =
            execute {
                check(
                    Settings.Secure.putInt(
                        resolver,
                        SECURE_CAPTIONING_ENABLED,
                        if (enabled) 1 else 0,
                    ),
                ) { "The system did not allow changing captions" }
                refreshOrThrow()
            }

        override suspend fun setCaptionTextSize(size: CaptionTextSize): ActionResult =
            execute {
                check(
                    Settings.Secure.putFloat(
                        resolver,
                        SECURE_CAPTIONING_FONT_SCALE,
                        size.value,
                    ),
                ) { "The system did not allow changing caption text size" }
                refreshOrThrow()
            }

        override suspend fun setCaptionTextStyle(style: CaptionTextStyle): ActionResult =
            execute {
                check(
                    Settings.Secure.putInt(
                        resolver,
                        SECURE_CAPTIONING_PRESET,
                        style.value,
                    ),
                ) { "The system did not allow changing caption text style" }
                refreshOrThrow()
            }

        override suspend fun setServiceEnabled(
            componentName: String,
            enabled: Boolean,
        ): ActionResult =
            execute {
                val components = readEnabledServices().toMutableSet()
                if (enabled) components += componentName else components -= componentName
                check(
                    Settings.Secure.putString(
                        resolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        components.joinToString(":").ifBlank { null },
                    ),
                ) { "The system did not allow changing accessibility services" }
                check(
                    Settings.Secure.putInt(
                        resolver,
                        Settings.Secure.ACCESSIBILITY_ENABLED,
                        if (components.isEmpty()) 0 else 1,
                    ),
                ) { "The system did not allow changing accessibility state" }
                refreshOrThrow()
            }

        private fun loadServices(enabledServices: Set<String>): List<AccessibilityServiceEntry> =
            manager
                ?.installedAccessibilityServiceList
                .orEmpty()
                .map { info ->
                    val component = info.resolveInfo.serviceInfo.componentName()
                    AccessibilityServiceEntry(
                        componentName = component.flattenToString(),
                        label = info.resolveInfo.loadLabel(context.packageManager).toString(),
                        description = info.loadDescription(context.packageManager)?.toString().orEmpty(),
                        enabled = component.flattenToString() in enabledServices,
                        settingsActivity =
                            info.settingsActivityName?.let {
                                ComponentName(component.packageName, it).flattenToString()
                            },
                    )
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        private fun isSpokenService(componentName: String): Boolean =
            manager
                ?.installedAccessibilityServiceList
                .orEmpty()
                .firstOrNull { info ->
                    info.resolveInfo.serviceInfo
                        .componentName()
                        .flattenToString() == componentName
                }?.let { info ->
                    (info.feedbackType and AccessibilityServiceInfo.FEEDBACK_SPOKEN) != 0
                } == true

        private fun readEnabledServices(): Set<String> =
            readSecureString(
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty().split(':').filter(String::isNotBlank).toSet()

        private fun readSecureString(key: String): String? =
            runCatching {
                resolver
                    .call(Settings.Secure.CONTENT_URI, "GET_secure", key, null)
                    ?.getString("value")
            }.getOrNull() ?: Settings.Secure.getString(resolver, key)

        private fun android.content.pm.ServiceInfo.componentName(): ComponentName = ComponentName(packageName, name)

        private suspend fun execute(block: suspend () -> Unit): ActionResult =
            withContext(dispatcher) {
                try {
                    block()
                    ActionResult.Success
                } catch (throwable: Throwable) {
                    throwable.rethrowIfCancellation()
                    val message = throwable.cause?.message ?: throwable.message ?: "Unknown accessibility error"
                    mutableState.value = mutableState.value.copy(lastError = message)
                    ActionResult.Failure(message, throwable)
                }
            }

        private suspend fun refreshOrThrow() {
            check(refresh() == ActionResult.Success) { "Unable to refresh accessibility state" }
        }
    }

private const val SECURE_CAPTIONING_ENABLED = "accessibility_captioning_enabled"
private const val SECURE_CAPTIONING_FONT_SCALE = "accessibility_captioning_font_scale"
private const val SECURE_CAPTIONING_PRESET = "accessibility_captioning_preset"
