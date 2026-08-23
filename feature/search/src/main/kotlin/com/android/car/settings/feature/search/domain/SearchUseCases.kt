package com.android.car.settings.feature.search.domain

import javax.inject.Inject

class SearchUseCases
    @Inject
    constructor(
        private val repository: SearchRepository,
    ) {
        suspend fun search(query: String): List<SettingsSearchResult> = repository.search(query)
    }
