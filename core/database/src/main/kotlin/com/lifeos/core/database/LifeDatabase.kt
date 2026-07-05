package com.lifeos.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.lifeos.core.database.chat.AiConversationEntity
import com.lifeos.core.database.chat.AiMessageEntity
import com.lifeos.core.database.chat.ChatDao
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
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class LifeDatabase : RoomDatabase() {
    abstract fun vaultBlobDao(): VaultBlobDao
    abstract fun chatDao(): ChatDao

    companion object {
        const val NAME = "life-os.db"
    }
}
