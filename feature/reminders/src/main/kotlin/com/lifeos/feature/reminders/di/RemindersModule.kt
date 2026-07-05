package com.lifeos.feature.reminders.di

import com.lifeos.core.service.LifeActionHandler
import com.lifeos.feature.reminders.data.DefaultRemindersRepository
import com.lifeos.feature.reminders.data.RemindersActionHandler
import com.lifeos.feature.reminders.data.RemindersRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RemindersModule {

    @Binds
    @Singleton
    abstract fun bindRemindersRepository(impl: DefaultRemindersRepository): RemindersRepository

    @Binds
    @IntoSet
    abstract fun bindRemindersActionHandler(impl: RemindersActionHandler): LifeActionHandler
}
