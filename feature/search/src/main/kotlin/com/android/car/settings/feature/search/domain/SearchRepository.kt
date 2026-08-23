package com.android.car.settings.feature.search.domain

interface SearchRepository {
    suspend fun search(query: String): List<SettingsSearchResult>
}
