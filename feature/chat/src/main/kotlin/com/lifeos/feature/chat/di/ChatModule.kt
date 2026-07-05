package com.lifeos.feature.chat.di

import com.lifeos.feature.chat.data.ChatRepository
import com.lifeos.feature.chat.data.DefaultChatRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ChatModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: DefaultChatRepository): ChatRepository
}
