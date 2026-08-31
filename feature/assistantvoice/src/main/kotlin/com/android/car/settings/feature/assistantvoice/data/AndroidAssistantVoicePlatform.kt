package com.android.car.settings.feature.assistantvoice.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.speech.RecognitionService
import com.android.car.settings.core.common.ActionResult
import com.android.car.settings.core.common.IoDispatcher
import com.android.car.settings.core.common.rethrowIfCancellation
import com.android.car.settings.feature.assistantvoice.domain.AssistantVoiceState
import com.android.car.settings.feature.assistantvoice.domain.VoiceInputKind
import com.android.car.settings.feature.assistantvoice.domain.VoiceInputOption
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
internal class AndroidAssistantVoicePlatform
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : AssistantVoicePlatform {
        private val resolver = context.contentResolver
        private val packageManager = context.packageManager
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val mutableState = MutableStateFlow(AssistantVoiceState())

        override val state: StateFlow<AssistantVoiceState> = mutableState.asStateFlow()

        init {
            scope.launch { refresh() }
        }

        override suspend fun refresh(): ActionResult =
            execute {
                val voiceInputs = loadVoiceInputs()
                val assistantPackage = AssistantVoiceHiddenApiBridge.getAssistantHolder(context)
                val assistantLabel =
                    assistantPackage?.let { packageName ->
                        runCatching {
                            packageManager
                                .getApplicationLabel(
                                    packageManager.getApplicationInfo(packageName, 0),
                                ).toString()
                        }.getOrDefault(packageName)
                    } ?: "None selected"
                val interactionComponent = readSecureString(SECURE_VOICE_INTERACTION_SERVICE)
                val recognitionComponent = readSecureString(SECURE_VOICE_RECOGNITION_SERVICE)
                mutableState.value =
                    AssistantVoiceState(
                        assistantPackageName = assistantPackage,
                        assistantLabel = assistantLabel,
                        assistantSettingsActivity =
                            assistantPackage?.let {
                                AssistantVoiceHiddenApiBridge.getAssistantSettingsActivity(context, it)
                            },
                        textFromScreenEnabled = readSecureBoolean(SECURE_ASSIST_STRUCTURE_ENABLED, true),
                        screenshotEnabled = readSecureBoolean(SECURE_ASSIST_SCREENSHOT_ENABLED, true),
                        voiceInput =
                            voiceInputs.firstOrNull {
                                it.componentName == interactionComponent || it.componentName == recognitionComponent
                            },
                        voiceInputs = voiceInputs,
                    )
            }

        override suspend fun setTextFromScreenEnabled(enabled: Boolean): ActionResult =
            execute {
                check(
                    Settings.Secure.putInt(
                        resolver,
                        SECURE_ASSIST_STRUCTURE_ENABLED,
                        if (enabled) 1 else 0,
                    ),
                ) { "The system did not allow changing screen context" }
                if (!enabled) {
                    check(
                        Settings.Secure.putInt(
                            resolver,
                            SECURE_ASSIST_SCREENSHOT_ENABLED,
                            0,
                        ),
                    ) { "The system did not allow disabling screenshots" }
                }
                refreshOrThrow()
            }

        override suspend fun setScreenshotEnabled(enabled: Boolean): ActionResult =
            execute {
                check(readSecureBoolean(SECURE_ASSIST_STRUCTURE_ENABLED, true) || !enabled) {
                    "Enable text from screen before enabling screenshots"
                }
                check(
                    Settings.Secure.putInt(
                        resolver,
                        SECURE_ASSIST_SCREENSHOT_ENABLED,
                        if (enabled) 1 else 0,
                    ),
                ) { "The system did not allow changing screenshots" }
                refreshOrThrow()
            }

        override suspend fun setDefaultVoiceInput(option: VoiceInputOption): ActionResult =
            execute {
                val known = loadVoiceInputs().any { it.componentName == option.componentName }
                check(known) { "The selected voice input is no longer available" }
                when (option.kind) {
                    VoiceInputKind.INTERACTION -> {
                        check(
                            Settings.Secure.putString(
                                resolver,
                                SECURE_VOICE_INTERACTION_SERVICE,
                                option.componentName,
                            ),
                        ) { "The system did not allow changing voice interaction" }
                        option.recognitionComponentName?.let { recognitionComponent ->
                            check(
                                Settings.Secure.putString(
                                    resolver,
                                    SECURE_VOICE_RECOGNITION_SERVICE,
                                    recognitionComponent,
                                ),
                            ) { "The system did not allow changing voice recognition" }
                        }
                    }
                    VoiceInputKind.RECOGNITION -> {
                        check(
                            Settings.Secure.putString(
                                resolver,
                                SECURE_VOICE_INTERACTION_SERVICE,
                                null,
                            ),
                        ) { "The system did not allow clearing voice interaction" }
                        check(
                            Settings.Secure.putString(
                                resolver,
                                SECURE_VOICE_RECOGNITION_SERVICE,
                                option.componentName,
                            ),
                        ) { "The system did not allow changing voice recognition" }
                    }
                }
                refreshOrThrow()
            }

        private fun loadVoiceInputs(): List<VoiceInputOption> =
            buildList {
                queryServices(VoiceInteractionService.SERVICE_INTERFACE).forEach { resolveInfo ->
                    add(resolveInfo.toVoiceOption(VoiceInputKind.INTERACTION))
                }
                queryServices(RecognitionService.SERVICE_INTERFACE).forEach { resolveInfo ->
                    add(resolveInfo.toVoiceOption(VoiceInputKind.RECOGNITION))
                }
            }.distinctBy { "${it.kind}: ${it.componentName}" }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        private fun queryServices(action: String): List<ResolveInfo> =
            runCatching {
                packageManager.queryIntentServices(
                    Intent(action),
                    PackageManager.MATCH_ALL,
                )
            }.getOrDefault(emptyList())

        private fun ResolveInfo.toVoiceOption(kind: VoiceInputKind): VoiceInputOption {
            val serviceInfo = serviceInfo
            val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
            return VoiceInputOption(
                componentName = component.flattenToString(),
                packageName = serviceInfo.packageName,
                label = loadLabel(packageManager).toString(),
                kind = kind,
                recognitionComponentName =
                    if (kind == VoiceInputKind.INTERACTION) {
                        AssistantVoiceHiddenApiBridge.getRecognitionComponent(context, this)
                    } else {
                        null
                    },
            )
        }

        private suspend fun execute(block: suspend () -> Unit): ActionResult =
            withContext(dispatcher) {
                try {
                    block()
                    ActionResult.Success
                } catch (throwable: Throwable) {
                    throwable.rethrowIfCancellation()
                    val message =
                        throwable.cause?.message ?: throwable.message
                            ?: "Unknown assistant and voice error"
                    mutableState.value = mutableState.value.copy(lastError = message)
                    ActionResult.Failure(message, throwable)
                }
            }

        private suspend fun refreshOrThrow() {
            check(refresh() == ActionResult.Success) { "Unable to refresh assistant and voice state" }
        }

        private fun readSecureBoolean(
            key: String,
            default: Boolean,
        ): Boolean {
            // Settings.Secure keeps a process-local NameValueCache. Calling the provider directly
            // avoids returning a stale default when the value was changed by the Settings app.
            return readSecureString(key)
                ?.toIntOrNull()
                ?.let { it != 0 }
                ?: default
        }

        private fun readSecureString(key: String): String? =
            runCatching {
                resolver
                    .call(Settings.Secure.CONTENT_URI, "GET_secure", key, null)
                    ?.getString("value")
            }.getOrNull() ?: Settings.Secure.getString(resolver, key)
    }

private const val SECURE_ASSIST_STRUCTURE_ENABLED = "assist_structure_enabled"
private const val SECURE_ASSIST_SCREENSHOT_ENABLED = "assist_screenshot_enabled"
private const val SECURE_VOICE_INTERACTION_SERVICE = "voice_interaction_service"
private const val SECURE_VOICE_RECOGNITION_SERVICE = "voice_recognition_service"
