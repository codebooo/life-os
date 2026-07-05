package com.lifeos.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.lifeos.core.database.capture.CaptureDao
import com.lifeos.core.database.capture.CaptureEntity
import com.lifeos.core.database.capture.LogEntryEntity
import com.lifeos.core.database.capture.LogFormEntity
import com.lifeos.core.database.capture.TaskEntity
import com.lifeos.core.database.chat.AiConversationEntity
import com.lifeos.core.database.chat.AiMessageEntity
import com.lifeos.core.database.chat.ChatDao
import com.lifeos.core.database.notes.NoteDao
import com.lifeos.core.database.notes.NoteEmbeddingEntity
import com.lifeos.core.database.notes.NoteEntity
import com.lifeos.core.database.notes.NoteLinkEntity
import com.lifeos.core.database.vault.VaultBlobDao
import com.lifeos.core.database.vault.VaultBlobEntity

/**
 * The single app database (§1.7), feature-partitioned by package with
 * centralized migrations. Entities land here module-by-module as phases ship.
 */
@Database(
    entities = [
        VaultBlobEntity::class,
        AiConversationEntity::class,
        AiMessageEntity::class,
        CaptureEntity::class,
        LogFormEntity::class,
        LogEntryEntity::class,
        TaskEntity::class,
        NoteEntity::class,
        NoteLinkEntity::class,
        NoteEmbeddingEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class LifeDatabase : RoomDatabase() {
    abstract fun vaultBlobDao(): VaultBlobDao
    abstract fun chatDao(): ChatDao
    abstract fun captureDao(): CaptureDao
    abstract fun noteDao(): NoteDao

    companion object {
        const val NAME = "life-os.db"
    }
}
