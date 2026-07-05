package com.lifeos.feature.calendar.di

import com.lifeos.feature.calendar.data.CalendarRepository
import com.lifeos.feature.calendar.data.DefaultCalendarRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CalendarModule {

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(impl: DefaultCalendarRepository): CalendarRepository
}
