package com.lifeos.feature.todo.di

import com.lifeos.core.service.LifeActionHandler
import com.lifeos.feature.todo.data.TodoActionHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TodoModule {

    @Binds
    @IntoSet
    abstract fun bindTodoActionHandler(impl: TodoActionHandler): LifeActionHandler
}
