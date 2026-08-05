package com.lifeos.core.database.calendar

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A calendar the user keeps events in (§Module 19).
 *
 * Colour lives here rather than on the event, so recolouring a calendar
 * recolours everything in it. [subscriptionUrl] makes it a live mirror of a
 * remote ICS feed (holidays, a shared work calendar); those rows are replaced on
 * every sync, which is why locally created events never go into one.
 */
@Entity(tableName = "calendars")
data class CalendarListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB, as stored by Compose's Color.toArgb(). */
    val colorArgb: Int,
    val isDefault: Boolean = false,
    val visible: Boolean = true,
    /** Non-null = read-only subscription refreshed from this URL. */
    val subscriptionUrl: String? = null,
    val lastSyncedAt: Long? = null,
    val createdAt: Long,
)

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
    /** Which calendar this belongs to; null = the default one. */
    @ColumnInfo(defaultValue = "NULL") val calendarId: Long? = null,
    /** Minutes-before offsets, comma separated ("0,30,1440"); empty = no alert. */
    @ColumnInfo(defaultValue = "") val reminderMinutes: String = "",
    /** ICS UID for subscription rows, so a refresh updates instead of duplicating. */
    @ColumnInfo(defaultValue = "NULL") val externalUid: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface CalendarDao {

    // ---- calendars ---------------------------------------------------------

    @Query("SELECT * FROM calendars ORDER BY isDefault DESC, name")
    fun observeCalendars(): Flow<List<CalendarListEntity>>

    @Query("SELECT * FROM calendars ORDER BY isDefault DESC, name")
    suspend fun allCalendars(): List<CalendarListEntity>

    @Query("SELECT * FROM calendars WHERE id = :id")
    suspend fun calendar(id: Long): CalendarListEntity?

    @Query("SELECT * FROM calendars WHERE isDefault = 1 LIMIT 1")
    suspend fun defaultCalendar(): CalendarListEntity?

    @Query("SELECT * FROM calendars WHERE subscriptionUrl IS NOT NULL")
    suspend fun subscriptions(): List<CalendarListEntity>

    @Insert
    suspend fun insertCalendar(calendar: CalendarListEntity): Long

    @Update
    suspend fun updateCalendar(calendar: CalendarListEntity)

    @Query("UPDATE calendars SET isDefault = 0")
    suspend fun clearDefaultCalendar()

    @Query("DELETE FROM calendars WHERE id = :id")
    suspend fun deleteCalendar(id: Long)

    @Query("UPDATE calendar_events SET calendarId = NULL WHERE calendarId = :calendarId")
    suspend fun detachEventsFrom(calendarId: Long)

    @Query("DELETE FROM calendar_events WHERE calendarId = :calendarId")
    suspend fun deleteEventsOf(calendarId: Long)

    @Query("SELECT * FROM calendar_events WHERE calendarId = :calendarId AND externalUid = :uid LIMIT 1")
    suspend fun bySubscriptionUid(calendarId: Long, uid: String): CalendarEventEntity?

    @Query("UPDATE calendars SET lastSyncedAt = :at WHERE id = :id")
    suspend fun markSynced(id: Long, at: Long)

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

    @Query("SELECT * FROM calendar_events ORDER BY startsAt")
    suspend fun allEvents(): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE systemEventId IS NULL ORDER BY startsAt")
    suspend fun unmirroredEvents(): List<CalendarEventEntity>
}
