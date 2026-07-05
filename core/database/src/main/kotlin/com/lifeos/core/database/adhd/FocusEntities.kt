package com.lifeos.core.database.adhd

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A completed (or abandoned) focus-timer session (§Module 5). */
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minutes: Int,
    val startedAt: Long,
    val completed: Boolean,
)

@Dao
interface FocusDao {

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<FocusSessionEntity>>

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE completed = 1")
    fun observeCompletedCount(): Flow<Int>

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long
}
