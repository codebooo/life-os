package com.lifeos.feature.calendar.di

import com.lifeos.core.service.LifeActionHandler
import com.lifeos.feature.calendar.data.CalendarActionHandler
import com.lifeos.feature.calendar.data.CalendarRepository
import com.lifeos.feature.calendar.data.DefaultCalendarRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CalendarModule {

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(impl: DefaultCalendarRepository): CalendarRepository

    @Binds
    @IntoSet
    abstract fun bindCalendarActionHandler(impl: CalendarActionHandler): LifeActionHandler
}
