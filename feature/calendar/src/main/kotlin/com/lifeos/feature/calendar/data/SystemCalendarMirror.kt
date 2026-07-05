package com.lifeos.feature.calendar.data

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.calendar.CalendarDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-way mirror into the system Calendar Provider (§Module 19/§8.6): LifeOS
 * events become visible to every calendar app (and to Proton via the system
 * sync). Requires READ/WRITE_CALENDAR, requested from the Calendar screen.
 */
@Singleton
class SystemCalendarMirror @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendarDao: CalendarDao,
) {

    /** Pushes every not-yet-mirrored event; returns how many were written. */
    suspend fun mirrorAll(): LifeResult<Int> = withContext(Dispatchers.IO) {
        val calendarId = primaryCalendarId()
            ?: return@withContext LifeResult.Failure(
                LifeError.NotFound("No writable system calendar found — add an account in the Calendar app first"),
            )
        try {
            var written = 0
            calendarDao.unmirroredEvents().forEach { event ->
                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, event.title)
                    put(CalendarContract.Events.DTSTART, event.startsAt)
                    put(CalendarContract.Events.DTEND, event.endsAt)
                    put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
                    put(CalendarContract.Events.EVENT_LOCATION, event.location)
                    put(CalendarContract.Events.DESCRIPTION, event.notes)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                }
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                val systemId = uri?.lastPathSegment?.toLongOrNull()
                if (systemId != null) {
                    calendarDao.update(event.copy(systemEventId = systemId, updatedAt = System.currentTimeMillis()))
                    written++
                }
            }
            LifeResult.Success(written)
        } catch (se: SecurityException) {
            LifeResult.Failure(LifeError.Validation("Calendar permission missing — grant it and retry"))
        } catch (t: Throwable) {
            LifeResult.Failure(LifeError.Unknown("Mirror failed: ${t.message}", t))
        }
    }

    private fun primaryCalendarId(): Long? = try {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            "${CalendarContract.Calendars.IS_PRIMARY} DESC",
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
    } catch (_: SecurityException) {
        null
    }
}
