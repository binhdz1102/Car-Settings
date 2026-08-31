package com.android.car.settings.feature.accessibility.domain

enum class CaptionTextSize(
    val value: Float,
    val title: String,
) {
    VERY_SMALL(0.25f, "Very small"),
    SMALL(0.5f, "Small"),
    DEFAULT(1.0f, "Default"),
    LARGE(1.5f, "Large"),
    VERY_LARGE(2.0f, "Very large"),
}

enum class CaptionTextStyle(
    val value: Int,
    val title: String,
) {
    BY_APP(4, "By app"),
    WHITE_ON_BLACK(0, "White on black"),
    BLACK_ON_WHITE(1, "Black on white"),
    YELLOW_ON_BLACK(2, "Yellow on black"),
    YELLOW_ON_BLUE(3, "Yellow on blue"),
}

data class AccessibilityServiceEntry(
    val componentName: String,
    val label: String,
    val description: String,
    val enabled: Boolean,
    val settingsActivity: String? = null,
)

data class AccessibilityState(
    val captionsEnabled: Boolean = false,
    val captionTextSize: CaptionTextSize = CaptionTextSize.DEFAULT,
    val captionTextStyle: CaptionTextStyle = CaptionTextStyle.BY_APP,
    val screenReaderSupported: Boolean = false,
    val screenReaderName: String = "Screen reader",
    val screenReaderEnabled: Boolean = false,
    val screenReaderComponent: String? = null,
    val screenReaderSettingsActivity: String? = null,
    val services: List<AccessibilityServiceEntry> = emptyList(),
    val lastError: String? = null,
)
