package com.lifeos.core.database.calendar

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A local-first calendar event (§Module 19). Provider/ICS sync layers on top. */
@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val location: String?,
    val notes: String?,
    val startsAt: Long,
    val endsAt: Long,
    val allDay: Boolean = false,
    val reminderId: Long? = null,
    /** Non-null when mirrored to the system Calendar Provider. */
    val systemEventId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface CalendarDao {

    @Insert
    suspend fun insert(event: CalendarEventEntity): Long

    @Update
    suspend fun update(event: CalendarEventEntity)

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun getById(id: Long): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events WHERE startsAt < :windowEnd AND endsAt > :windowStart ORDER BY startsAt")
    fun observeWindow(windowStart: Long, windowEnd: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE endsAt > :now ORDER BY startsAt LIMIT :limit")
    fun observeUpcoming(now: Long, limit: Int = 20): Flow<List<CalendarEventEntity>>

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun delete(id: Long)
}
