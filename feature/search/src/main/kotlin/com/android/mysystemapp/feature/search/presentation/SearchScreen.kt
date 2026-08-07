package com.android.car.settings.feature.search.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.feature.search.domain.SearchAvailability
import com.android.car.settings.feature.search.domain.SearchDestination
import com.android.car.settings.feature.search.domain.SettingsSearchResult

@Composable
fun SearchRoute(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onDestination: (SearchDestination) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScaffold(title = "Search settings", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::updateQuery,
                label = { Text("Search settings") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (uiState.results.isEmpty() && uiState.query.isNotBlank()) {
                    item { Text("No settings found", modifier = Modifier.padding(24.dp)) }
                }
                items(uiState.results, key = SettingsSearchResult::key) { result ->
                    SettingsActionRow(
                        title = result.title,
                        summary = resultSummary(result),
                        enabled = result.availability == SearchAvailability.AVAILABLE,
                        onClick = { onDestination(result.destination) },
                    )
                }
            }
        }
    }
}

private fun resultSummary(result: SettingsSearchResult): String =
    result.unavailableReason ?: "${result.screenTitle} · ${result.summary}"
