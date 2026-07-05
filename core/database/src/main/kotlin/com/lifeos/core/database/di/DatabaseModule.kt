package com.lifeos.core.database.di

import android.content.Context
import androidx.room.Room
import com.lifeos.core.database.LifeDatabase
import com.lifeos.core.database.calendar.CalendarDao
import com.lifeos.core.database.capture.CaptureDao
import com.lifeos.core.database.chat.ChatDao
import com.lifeos.core.database.dhl.PackageDao
import com.lifeos.core.database.messages.MessageDao
import com.lifeos.core.database.notes.NoteDao
import com.lifeos.core.database.reminders.ReminderDao
import com.lifeos.core.database.todo.TodoDao
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

    @Provides
    fun provideCaptureDao(database: LifeDatabase): CaptureDao = database.captureDao()

    @Provides
    fun provideNoteDao(database: LifeDatabase): NoteDao = database.noteDao()

    @Provides
    fun provideReminderDao(database: LifeDatabase): ReminderDao = database.reminderDao()

    @Provides
    fun provideTodoDao(database: LifeDatabase): TodoDao = database.todoDao()

    @Provides
    fun provideCalendarDao(database: LifeDatabase): CalendarDao = database.calendarDao()

    @Provides
    fun provideMessageDao(database: LifeDatabase): MessageDao = database.messageDao()

    @Provides
    fun providePackageDao(database: LifeDatabase): PackageDao = database.packageDao()
}
