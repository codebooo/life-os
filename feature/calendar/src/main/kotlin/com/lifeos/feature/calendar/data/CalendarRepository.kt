package com.lifeos.feature.calendar.data

import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.result.getOrNull
import com.lifeos.core.common.result.runCatchingLife
import com.lifeos.core.database.calendar.CalendarDao
import com.lifeos.core.database.calendar.CalendarEventEntity
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
import com.lifeos.core.service.LifeEvent
import com.lifeos.core.service.LifeEventBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-first calendar (§Module 19). Events live in Room and publish
 * [LifeEvent.CalendarEventChanged]; the system Calendar Provider mirror and
 * Proton ICS bridges (§8.6) layer on in a follow-up — the [CalendarEventEntity.systemEventId]
 * column is already in place for it.
 */
interface CalendarRepository {
    fun observeWindow(windowStart: Long, windowEnd: Long): Flow<List<CalendarEventEntity>>
    fun observeUpcoming(): Flow<List<CalendarEventEntity>>

    /** Creates an event; when [remindMinutesBefore] is set, links a reminder via LifeAction. */
    suspend fun create(
        title: String,
        startsAt: Long,
        endsAt: Long,
        location: String? = null,
        notes: String? = null,
        allDay: Boolean = false,
        remindMinutesBefore: Int? = null,
    ): LifeResult<Long>

    suspend fun delete(eventId: Long)
}

@Singleton
internal class DefaultCalendarRepository @Inject constructor(
    private val calendarDao: CalendarDao,
    private val actionDispatcher: dagger.Lazy<LifeActionDispatcher>,
    private val eventBus: LifeEventBus,
    private val dispatchers: DispatcherProvider,
) : CalendarRepository {

    override fun observeWindow(windowStart: Long, windowEnd: Long): Flow<List<CalendarEventEntity>> =
        calendarDao.observeWindow(windowStart, windowEnd)

    override fun observeUpcoming(): Flow<List<CalendarEventEntity>> =
        calendarDao.observeUpcoming(System.currentTimeMillis())

    override suspend fun create(
        title: String,
        startsAt: Long,
        endsAt: Long,
        location: String?,
        notes: String?,
        allDay: Boolean,
        remindMinutesBefore: Int?,
    ): LifeResult<Long> = withContext(dispatchers.io) {
        runCatchingLife {
            val now = System.currentTimeMillis()
            val eventId = calendarDao.insert(
                CalendarEventEntity(
                    title = title,
                    location = location,
                    notes = notes,
                    startsAt = startsAt,
                    endsAt = endsAt,
                    allDay = allDay,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            if (remindMinutesBefore != null) {
                val remindAt = startsAt - TimeUnit.MINUTES.toMillis(remindMinutesBefore.toLong())
                if (remindAt > now) {
                    val reminderId = actionDispatcher.get().dispatch(
                        LifeAction.CreateReminder(
                            title = title,
                            at = remindAt,
                            source = SourceRef(LifeModule.CALENDAR, eventId.toString()),
                        ),
                    ).getOrNull()
                    if (reminderId != null) {
                        calendarDao.getById(eventId)?.let {
                            calendarDao.update(it.copy(reminderId = reminderId, updatedAt = now))
                        }
                    }
                }
            }

            eventBus.tryPublish(LifeEvent.CalendarEventChanged(eventId, title, startsAt))
            eventId
        }
    }

    override suspend fun delete(eventId: Long) = withContext(dispatchers.io) {
        calendarDao.delete(eventId)
    }
}
