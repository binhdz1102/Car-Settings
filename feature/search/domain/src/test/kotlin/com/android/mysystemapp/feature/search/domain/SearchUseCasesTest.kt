package com.android.car.settings.feature.search.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SearchUseCasesTest {
    @Test
    fun delegatesTheNormalizedQueryToRepository() = runTest {
        val repository = FakeSearchRepository()
        val useCases = SearchUseCases(repository)

        val result = useCases.search("wifi")

        assertThat(repository.query).isEqualTo("wifi")
        assertThat(result.single().destination).isEqualTo(SearchDestination.WIFI)
    }

    private class FakeSearchRepository : SearchRepository {
        var query = ""

        override suspend fun search(query: String): List<SettingsSearchResult> {
            this.query = query
            return listOf(
                SettingsSearchResult(
                    key = "wifi",
                    title = "Wi-Fi",
                    summary = "Networks",
                    screenTitle = "Wi-Fi",
                    destination = SearchDestination.WIFI,
                ),
            )
        }
    }
}
