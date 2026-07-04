package com.lifeos.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lifeos.core.database.vault.VaultBlobDao
import com.lifeos.core.database.vault.VaultBlobEntity

/**
 * The single app database (§1.7), feature-partitioned by package with
 * centralized migrations. Entities land here module-by-module as phases ship.
 */
@Database(
    entities = [
        VaultBlobEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LifeDatabase : RoomDatabase() {
    abstract fun vaultBlobDao(): VaultBlobDao

    companion object {
        const val NAME = "life-os.db"
    }
}
