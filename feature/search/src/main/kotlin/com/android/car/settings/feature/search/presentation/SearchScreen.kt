package com.android.car.settings.feature.search.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.car.settings.core.ui.SettingsActionRow
import com.android.car.settings.core.ui.SettingsFormTextField
import com.android.car.settings.core.ui.SettingsLeadingIcon
import com.android.car.settings.core.ui.SettingsScaffold
import com.android.car.settings.feature.search.R
import com.android.car.settings.feature.search.domain.SearchAvailability
import com.android.car.settings.feature.search.domain.SearchDestination
import com.android.car.settings.feature.search.domain.SettingsSearchResult
import com.android.car.settings.core.ui.AutomotiveLazyColumn as LazyColumn

@Composable
fun SearchRoute(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onDestination: (SearchDestination) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScaffold(
        title = stringResource(R.string.search_title),
        destinationKey = "search",
        onBack = onBack,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsFormTextField(
                focusId = "search-query",
                label = stringResource(R.string.search_query_label),
                value = uiState.query,
                onValueChange = viewModel::updateQuery,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (uiState.results.isEmpty() && uiState.query.isNotBlank()) {
                    item {
                        Text(
                            stringResource(R.string.search_empty),
                            modifier = Modifier.padding(24.dp),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(uiState.results, key = SettingsSearchResult::key) { result ->
                    SettingsActionRow(
                        navigates = true,
                        title = result.title,
                        summary = resultSummary(result),
                        enabled = result.availability == SearchAvailability.AVAILABLE,
                        leading = { SettingsLeadingIcon(Icons.Default.Search) },
                        onClick = { onDestination(result.destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun resultSummary(result: SettingsSearchResult): String =
    result.unavailableReason
        ?: stringResource(R.string.search_result_summary, result.screenTitle, result.summary)
