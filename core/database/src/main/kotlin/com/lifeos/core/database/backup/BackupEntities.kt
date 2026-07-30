package com.lifeos.core.database.backup

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One backup attempt (§Module Sync): what was written, where, and whether it verified. */
@Entity(tableName = "backup_runs")
data class BackupRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val destination: String,
    val fileName: String,
    val sizeBytes: Long,
    /** OK, FAILED, VERIFIED */
    val status: String,
    val detail: String = "",
)

@Dao
interface BackupDao {

    @Insert
    suspend fun insert(run: BackupRunEntity): Long

    @Query("SELECT * FROM backup_runs ORDER BY at DESC LIMIT 30")
    fun observeRuns(): Flow<List<BackupRunEntity>>

    @Query("SELECT * FROM backup_runs ORDER BY at DESC LIMIT 1")
    suspend fun latest(): BackupRunEntity?

    @Query("SELECT * FROM backup_runs ORDER BY at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<BackupRunEntity>
}
