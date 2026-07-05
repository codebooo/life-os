package com.lifeos.core.database.reminders

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders ORDER BY at ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE enabled = 1 AND firedAt IS NULL AND at > :now ORDER BY at ASC")
    suspend fun pendingAfter(now: Long): List<ReminderEntity>

    @Query("UPDATE reminders SET firedAt = :firedAt WHERE id = :id")
    suspend fun markFired(id: Long, firedAt: Long)

    @Query("UPDATE reminders SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)
}
