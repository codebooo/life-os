package com.lifeos.feature.calendar.data

import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.result.getOrNull
import com.lifeos.core.common.result.runCatchingLife
import com.lifeos.core.database.calendar.CalendarDao
import com.lifeos.core.database.calendar.CalendarEventEntity
import com.lifeos.core.database.calendar.CalendarListEntity
import com.lifeos.core.database.capture.CaptureDao
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
import com.lifeos.core.service.LifeEvent
import com.lifeos.core.service.LifeEventBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Everything an event carries when it is written. */
data class EventDraft(
    val title: String,
    val startsAt: Long,
    val endsAt: Long,
    val location: String? = null,
    val notes: String? = null,
    val allDay: Boolean = false,
    val calendarId: Long? = null,
    /** Minutes-before offsets, Proton-style: 0 = at start, 1440 = a day before. */
    val reminderMinutes: List<Int> = emptyList(),
)

/**
 * Local-first calendar (§Module 19). Events live in Room, belong to a coloured
 * calendar, publish [LifeEvent.CalendarEventChanged], and can be mirrored into
 * the system Calendar Provider or exchanged as ICS.
 */
interface CalendarRepository {
    fun observeWindow(windowStart: Long, windowEnd: Long): Flow<List<CalendarEventEntity>>
    fun observeUpcoming(): Flow<List<CalendarEventEntity>>
    fun observeCalendars(): Flow<List<CalendarListEntity>>

    /** Creates the starter calendar the first time one is needed. */
    suspend fun ensureDefaultCalendar(): Long
    suspend fun calendars(): List<CalendarListEntity>

    suspend fun createCalendar(
        name: String,
        colorArgb: Int,
        subscriptionUrl: String? = null,
        makeDefault: Boolean = false,
    ): LifeResult<Long>

    suspend fun renameCalendar(calendarId: Long, name: String)
    suspend fun recolourCalendar(calendarId: Long, colorArgb: Int)
    suspend fun setCalendarVisible(calendarId: Long, visible: Boolean)
    suspend fun setDefaultCalendar(calendarId: Long)

    /** Removes a calendar; its events are deleted or set loose. */
    suspend fun deleteCalendar(calendarId: Long, deleteEvents: Boolean)

    suspend fun create(draft: EventDraft): LifeResult<Long>
    suspend fun update(eventId: Long, draft: EventDraft): LifeResult<Unit>

    /** Copies an event to the same slot, so only the date needs changing. */
    suspend fun duplicate(eventId: Long): LifeResult<Long>

    /** Moves an event by whole days, for drag-free rescheduling. */
    suspend fun shiftBy(eventId: Long, deltaMs: Long): LifeResult<Unit>

    suspend fun delete(eventId: Long)

    suspend fun search(query: String, limit: Int = 40): List<CalendarEventEntity>

    /** Imports parsed ICS events; deduped by UID inside a calendar, else by title+start. */
    suspend fun importParsed(
        events: List<IcsCodec.ParsedEvent>,
        calendarId: Long? = null,
        replaceCalendar: Boolean = false,
    ): LifeResult<Int>

    /** The whole local calendar as an RFC 5545 document (Proton ICS bridge, §8.6). */
    suspend fun exportIcs(): String
}

@Singleton
internal class DefaultCalendarRepository @Inject constructor(
    private val calendarDao: CalendarDao,
    private val captureDao: CaptureDao,
    private val actionDispatcher: dagger.Lazy<LifeActionDispatcher>,
    private val eventBus: LifeEventBus,
    private val dispatchers: DispatcherProvider,
) : CalendarRepository {

    override fun observeWindow(windowStart: Long, windowEnd: Long): Flow<List<CalendarEventEntity>> =
        combine(
            calendarDao.observeWindow(windowStart, windowEnd),
            captureDao.observeTimedTasks(windowStart, windowEnd),
        ) { events, tasks ->
            // Time-stamped to-dos appear as read-only entries (negative id marks them).
            val taskEvents = tasks.map { task ->
                CalendarEventEntity(
                    id = -task.id,
                    title = task.title,
                    location = null,
                    notes = TASK_MARKER,
                    startsAt = task.dueAt ?: 0L,
                    endsAt = (task.dueAt ?: 0L) + 30 * 60_000L,
                    createdAt = task.createdAt,
                    updatedAt = task.createdAt,
                )
            }
            (events + taskEvents).sortedBy { it.startsAt }
        }

    override fun observeUpcoming(): Flow<List<CalendarEventEntity>> =
        calendarDao.observeUpcoming(System.currentTimeMillis())

    override fun observeCalendars(): Flow<List<CalendarListEntity>> = calendarDao.observeCalendars()

    override suspend fun ensureDefaultCalendar(): Long = withContext(dispatchers.io) {
        calendarDao.defaultCalendar()?.id
            ?: calendarDao.allCalendars().firstOrNull()?.id
            ?: calendarDao.insertCalendar(
                CalendarListEntity(
                    name = "Personal",
                    colorArgb = CalendarPalette.default,
                    isDefault = true,
                    createdAt = System.currentTimeMillis(),
                ),
            )
    }

    override suspend fun calendars(): List<CalendarListEntity> = withContext(dispatchers.io) {
        calendarDao.allCalendars()
    }

    override suspend fun createCalendar(
        name: String,
        colorArgb: Int,
        subscriptionUrl: String?,
        makeDefault: Boolean,
    ): LifeResult<Long> = withContext(dispatchers.io) {
        runCatchingLife {
            val clean = name.trim().take(60)
            require(clean.isNotEmpty()) { "A calendar needs a name" }
            // A subscription is a read-only mirror, so it can never be the default.
            val asDefault = makeDefault && subscriptionUrl == null
            if (asDefault) calendarDao.clearDefaultCalendar()
            val existing = calendarDao.allCalendars()
            calendarDao.insertCalendar(
                CalendarListEntity(
                    name = clean,
                    colorArgb = colorArgb,
                    isDefault = asDefault || existing.none { it.subscriptionUrl == null },
                    subscriptionUrl = subscriptionUrl?.trim()?.ifBlank { null },
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun renameCalendar(calendarId: Long, name: String) = withContext(dispatchers.io) {
        val calendar = calendarDao.calendar(calendarId) ?: return@withContext
        calendarDao.updateCalendar(calendar.copy(name = name.trim().take(60).ifEmpty { calendar.name }))
    }

    override suspend fun recolourCalendar(calendarId: Long, colorArgb: Int) = withContext(dispatchers.io) {
        val calendar = calendarDao.calendar(calendarId) ?: return@withContext
        calendarDao.updateCalendar(calendar.copy(colorArgb = colorArgb))
    }

    override suspend fun setCalendarVisible(calendarId: Long, visible: Boolean) =
        withContext(dispatchers.io) {
            val calendar = calendarDao.calendar(calendarId) ?: return@withContext
            calendarDao.updateCalendar(calendar.copy(visible = visible))
        }

    override suspend fun setDefaultCalendar(calendarId: Long) = withContext(dispatchers.io) {
        val calendar = calendarDao.calendar(calendarId) ?: return@withContext
        if (calendar.subscriptionUrl != null) return@withContext
        calendarDao.clearDefaultCalendar()
        calendarDao.updateCalendar(calendar.copy(isDefault = true))
    }

    override suspend fun deleteCalendar(calendarId: Long, deleteEvents: Boolean) =
        withContext(dispatchers.io) {
            if (deleteEvents) calendarDao.deleteEventsOf(calendarId) else calendarDao.detachEventsFrom(calendarId)
            val wasDefault = calendarDao.calendar(calendarId)?.isDefault == true
            calendarDao.deleteCalendar(calendarId)
            // Never leave the user without a default to save into.
            if (wasDefault) {
                calendarDao.allCalendars().firstOrNull { it.subscriptionUrl == null }?.let {
                    calendarDao.updateCalendar(it.copy(isDefault = true))
                }
            }
        }

    override suspend fun create(draft: EventDraft): LifeResult<Long> = withContext(dispatchers.io) {
        runCatchingLife {
            val now = System.currentTimeMillis()
            val calendarId = draft.calendarId ?: ensureDefaultCalendar()
            val eventId = calendarDao.insert(
                CalendarEventEntity(
                    title = draft.title,
                    location = draft.location,
                    notes = draft.notes,
                    startsAt = draft.startsAt,
                    endsAt = draft.endsAt,
                    allDay = draft.allDay,
                    calendarId = calendarId,
                    reminderMinutes = encodeReminders(draft.reminderMinutes),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            armReminders(eventId, draft)
            eventBus.tryPublish(
                LifeEvent.CalendarEventChanged(
                    eventId,
                    draft.title,
                    draft.startsAt,
                    hasLocation = draft.location != null,
                ),
            )
            eventId
        }
    }

    override suspend fun update(eventId: Long, draft: EventDraft): LifeResult<Unit> =
        withContext(dispatchers.io) {
            runCatchingLife {
                val existing = calendarDao.getById(eventId) ?: error("Event not found")
                calendarDao.update(
                    existing.copy(
                        title = draft.title,
                        startsAt = draft.startsAt,
                        endsAt = draft.endsAt,
                        location = draft.location,
                        notes = draft.notes,
                        allDay = draft.allDay,
                        calendarId = draft.calendarId ?: existing.calendarId,
                        reminderMinutes = encodeReminders(draft.reminderMinutes),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                // Old alerts belong to old times; drop them before arming new ones.
                dispatch(
                    LifeAction.CancelRemindersFor(
                        module = LifeModule.CALENDAR.name,
                        entityId = eventId,
                        source = sourceOf(eventId),
                    ),
                )
                armReminders(eventId, draft)
                eventBus.tryPublish(
                    LifeEvent.CalendarEventChanged(
                        eventId,
                        draft.title,
                        draft.startsAt,
                        hasLocation = draft.location != null,
                    ),
                )
                Unit
            }
        }

    override suspend fun duplicate(eventId: Long): LifeResult<Long> = withContext(dispatchers.io) {
        runCatchingLife {
            val existing = calendarDao.getById(eventId) ?: error("Event not found")
            val now = System.currentTimeMillis()
            calendarDao.insert(
                existing.copy(
                    id = 0,
                    title = "${existing.title} (copy)",
                    reminderId = null,
                    systemEventId = null,
                    externalUid = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    override suspend fun shiftBy(eventId: Long, deltaMs: Long): LifeResult<Unit> =
        withContext(dispatchers.io) {
            runCatchingLife {
                val existing = calendarDao.getById(eventId) ?: error("Event not found")
                calendarDao.update(
                    existing.copy(
                        startsAt = existing.startsAt + deltaMs,
                        endsAt = existing.endsAt + deltaMs,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                Unit
            }
        }

    override suspend fun delete(eventId: Long) = withContext(dispatchers.io) {
        dispatch(
            LifeAction.CancelRemindersFor(
                module = LifeModule.CALENDAR.name,
                entityId = eventId,
                source = sourceOf(eventId),
            ),
        )
        calendarDao.delete(eventId)
    }

    override suspend fun search(query: String, limit: Int): List<CalendarEventEntity> =
        withContext(dispatchers.io) {
            val needle = query.trim().lowercase()
            if (needle.isEmpty()) return@withContext emptyList()
            calendarDao.allEvents()
                .filter {
                    it.title.lowercase().contains(needle) ||
                        it.location.orEmpty().lowercase().contains(needle) ||
                        it.notes.orEmpty().lowercase().contains(needle)
                }
                .sortedBy { it.startsAt }
                .take(limit)
        }

    override suspend fun importParsed(
        events: List<IcsCodec.ParsedEvent>,
        calendarId: Long?,
        replaceCalendar: Boolean,
    ): LifeResult<Int> = withContext(dispatchers.io) {
        runCatchingLife {
            val target = calendarId ?: ensureDefaultCalendar()
            // A subscription is a mirror: wipe and rewrite so cancellations vanish too.
            if (replaceCalendar) calendarDao.deleteEventsOf(target)
            val existing = calendarDao.allEvents().map { it.title to it.startsAt }.toSet()
            val now = System.currentTimeMillis()
            var written = 0
            events.forEach { event ->
                val byUid = event.uid?.let { calendarDao.bySubscriptionUid(target, it) }
                when {
                    byUid != null -> calendarDao.update(
                        byUid.copy(
                            title = event.title,
                            startsAt = event.startsAt,
                            endsAt = event.endsAt,
                            location = event.location,
                            notes = event.notes,
                            allDay = event.allDay,
                            updatedAt = now,
                        ),
                    )

                    (event.title to event.startsAt) !in existing -> {
                        calendarDao.insert(
                            CalendarEventEntity(
                                title = event.title,
                                location = event.location,
                                notes = event.notes,
                                startsAt = event.startsAt,
                                endsAt = event.endsAt,
                                allDay = event.allDay,
                                calendarId = target,
                                externalUid = event.uid,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                        written++
                    }
                }
            }
            written
        }
    }

    override suspend fun exportIcs(): String = withContext(dispatchers.io) {
        IcsCodec.export(calendarDao.allEvents())
    }

    /** Schedules one alert per offset, and remembers the first on the event. */
    private suspend fun armReminders(eventId: Long, draft: EventDraft) {
        if (draft.reminderMinutes.isEmpty()) return
        val now = System.currentTimeMillis()
        var firstId: Long? = null
        draft.reminderMinutes.distinct().sorted().forEach { minutes ->
            val remindAt = draft.startsAt - TimeUnit.MINUTES.toMillis(minutes.toLong())
            if (remindAt <= now) return@forEach
            val reminderId = dispatch(
                LifeAction.CreateReminder(
                    title = if (minutes == 0) draft.title else "${draft.title} in ${humanOffset(minutes)}",
                    at = remindAt,
                    source = sourceOf(eventId),
                ),
            )
            if (firstId == null) firstId = reminderId
        }
        val linked = firstId
        if (linked != null) {
            calendarDao.getById(eventId)?.let {
                calendarDao.update(it.copy(reminderId = linked, updatedAt = now))
            }
        }
    }

    private suspend fun dispatch(action: LifeAction): Long? =
        actionDispatcher.get().dispatch(action).getOrNull()

    private fun sourceOf(eventId: Long) = SourceRef(LifeModule.CALENDAR, eventId.toString())

    companion object {
        /** Marks the read-only rows mirrored in from Tasks. */
        const val TASK_MARKER = "lifeos:task"

        fun encodeReminders(minutes: List<Int>): String =
            minutes.distinct().sorted().joinToString(",")

        fun decodeReminders(value: String): List<Int> =
            value.split(',').mapNotNull { it.trim().toIntOrNull() }.distinct().sorted()

        fun humanOffset(minutes: Int): String = when {
            minutes == 0 -> "now"
            minutes % 1440 == 0 -> "${minutes / 1440}d"
            minutes % 60 == 0 -> "${minutes / 60}h"
            else -> "${minutes}m"
        }
    }
}
