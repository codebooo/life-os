package com.lifeos.feature.notes.di

import com.lifeos.core.service.LifeActionHandler
import com.lifeos.feature.notes.data.DefaultNotesRepository
import com.lifeos.feature.notes.data.NotesActionHandler
import com.lifeos.feature.notes.data.NotesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotesModule {

    @Binds
    @Singleton
    abstract fun bindNotesRepository(impl: DefaultNotesRepository): NotesRepository

    @Binds
    @IntoSet
    abstract fun bindNotesActionHandler(impl: NotesActionHandler): LifeActionHandler
}
