package com.android.car.settings.feature.search.domain

import com.android.car.settings.core.settings.SettingsDestinationId

enum class SearchAvailability {
    AVAILABLE,
    UNAVAILABLE,
}

/** Compatibility name; destination identity now comes from the shared Settings registry API. */
typealias SearchDestination = SettingsDestinationId

data class SettingsSearchResult(
    val key: String,
    val title: String,
    val summary: String,
    val screenTitle: String,
    val destination: SearchDestination,
    val availability: SearchAvailability = SearchAvailability.AVAILABLE,
    val unavailableReason: String? = null,
)
