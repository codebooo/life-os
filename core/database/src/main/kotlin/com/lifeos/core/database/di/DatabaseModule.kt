package com.lifeos.core.database.di

import android.content.Context
import androidx.room.Room
import com.lifeos.core.database.LifeDatabase
import com.lifeos.core.database.chat.ChatDao
import com.lifeos.core.database.vault.VaultBlobDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun provideLifeDatabase(@ApplicationContext context: Context): LifeDatabase =
        Room.databaseBuilder(context, LifeDatabase::class.java, LifeDatabase.NAME)
            .build()

    @Provides
    fun provideVaultBlobDao(database: LifeDatabase): VaultBlobDao = database.vaultBlobDao()

    @Provides
    fun provideChatDao(database: LifeDatabase): ChatDao = database.chatDao()
}
