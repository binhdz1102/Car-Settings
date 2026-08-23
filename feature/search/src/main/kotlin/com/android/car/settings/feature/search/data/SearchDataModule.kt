package com.android.car.settings.feature.search.data

import com.android.car.settings.feature.search.domain.SearchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SearchDataModule {
    @Binds
    abstract fun bindSearchRepository(implementation: AndroidSearchRepository): SearchRepository
}
