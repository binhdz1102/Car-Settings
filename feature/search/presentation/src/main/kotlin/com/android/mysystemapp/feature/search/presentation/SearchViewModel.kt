package com.android.car.settings.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.settings.feature.search.domain.SearchUseCases
import com.android.car.settings.feature.search.domain.SettingsSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<SettingsSearchResult> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val useCases: SearchUseCases,
) : ViewModel() {
    private val query = MutableStateFlow("")

    private val results: StateFlow<List<SettingsSearchResult>> =
        query
            .debounce(120)
            .mapLatest { value -> useCases.search(value) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<SearchUiState> =
        combine(query, results) { value, matches -> SearchUiState(value, matches) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun updateQuery(value: String) {
        query.value = value
    }
}
