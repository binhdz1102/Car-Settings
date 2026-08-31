package com.android.car.settings.feature.assistantvoice.domain

enum class VoiceInputKind {
    INTERACTION,
    RECOGNITION,
}

data class VoiceInputOption(
    val componentName: String,
    val packageName: String,
    val label: String,
    val kind: VoiceInputKind,
    val recognitionComponentName: String? = null,
)

data class AssistantVoiceState(
    val assistantPackageName: String? = null,
    val assistantLabel: String = "None selected",
    val assistantSettingsActivity: String? = null,
    val textFromScreenEnabled: Boolean = true,
    val screenshotEnabled: Boolean = true,
    val voiceInput: VoiceInputOption? = null,
    val voiceInputs: List<VoiceInputOption> = emptyList(),
    val lastError: String? = null,
)
