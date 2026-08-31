package com.android.car.settings.feature.notifications.data

import com.android.car.settings.feature.notifications.domain.NotificationsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationsDataModule {
    @Binds
    abstract fun bindPlatform(implementation: AndroidNotificationsPlatform): NotificationsPlatform

    @Binds
    abstract fun bindRepository(implementation: NotificationsRepositoryImpl): NotificationsRepository
}
