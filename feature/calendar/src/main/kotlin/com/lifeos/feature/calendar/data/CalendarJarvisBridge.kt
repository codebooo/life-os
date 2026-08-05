package com.lifeos.feature.calendar.data

import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.calendar.CalendarDao
import com.lifeos.core.service.ActionEcho
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import com.lifeos.core.service.LifeDataProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Calendar as Jarvis reads it (§Module 9): the calendars themselves, what is
 * coming up, and where the subscriptions stand.
 */
internal class CalendarProvider @Inject constructor(
    private val calendarDao: CalendarDao,
    private val calendarRepository: CalendarRepository,
) : LifeDataProvider {

    override val topic: String = "calendar"
    override val description: String = "your calendars, their colours, and upcoming events"

    override suspend fun read(query: String?): String {
        val days = query?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(1, 90) ?: 14
        val now = System.currentTimeMillis()
        val calendars = calendarRepository.calendars()
        val events = calendarDao.allEvents()
            .filter { it.endsAt > now && it.startsAt < now + days * 86_400_000L }
            .sortedBy { it.startsAt }
            .take(25)
        val byId = calendars.associateBy { it.id }
        return buildString {
            if (calendars.isEmpty()) {
                appendLine("Calendars: none yet (one is created the first time you save an event).")
            } else {
                appendLine("Calendars (${calendars.size}):")
                calendars.forEach { calendar ->
                    val bits = buildList {
                        add(CalendarPalette.nameOf(calendar.colorArgb))
                        if (calendar.isDefault) add("default")
                        if (!calendar.visible) add("hidden")
                        calendar.subscriptionUrl?.let { add("subscribed") }
                        calendar.lastSyncedAt?.let { add("synced ${STAMP.format(Date(it))}") }
                    }
                    appendLine("- ${calendar.name} (${bits.joinToString(", ")})")
                }
            }
            if (events.isEmpty()) {
                appendLine("Nothing scheduled in the next $days day(s).")
            } else {
                appendLine("Next $days day(s):")
                events.forEach { event ->
                    val where = event.calendarId?.let { byId[it]?.name } ?: "unfiled"
                    val when0 = if (event.allDay) {
                        "${DAY.format(Date(event.startsAt))} all day"
                    } else {
                        "${DAY.format(Date(event.startsAt))} ${TIME.format(Date(event.startsAt))}"
                    }
                    val alerts = DefaultCalendarRepository.decodeReminders(event.reminderMinutes)
                    appendLine(
                        "- $when0 · ${event.title} [$where]" +
                            (event.location?.takeIf { it.isNotBlank() }?.let { " @ $it" } ?: "") +
                            (if (alerts.isEmpty()) {
                                ""
                            } else {
                                " (alerts " +
                                    alerts.joinToString("/") { DefaultCalendarRepository.humanOffset(it) } +
                                    " before)"
                            }),
                    )
                }
            }
        }.trim()
    }

    private companion object {
        val DAY = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        val TIME = SimpleDateFormat("HH:mm", Locale.getDefault())
        val STAMP = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
    }
}

/**
 * Everything Jarvis can do to the calendar: file an event (into a named
 * calendar, with its own alerts), create a calendar, subscribe to an ICS feed,
 * and refresh those feeds.
 */
internal class CalendarActionHandler @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val subscriptionSync: CalendarSubscriptionSync,
    private val echo: ActionEcho,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction) = action is LifeAction.CreateCalendarEvent ||
        action is LifeAction.CreateCalendar ||
        action is LifeAction.SubscribeCalendar ||
        action is LifeAction.SyncCalendars

    override suspend fun execute(action: LifeAction): LifeResult<Long?> = when (action) {
        is LifeAction.CreateCalendarEvent -> {
            val calendarId = matchCalendar(action.calendarName)
            val result = calendarRepository.create(
                EventDraft(
                    title = action.title,
                    startsAt = action.startsAt,
                    endsAt = action.endsAt,
                    location = action.location.trim().ifBlank { null },
                    notes = action.notes.trim().ifBlank { null },
                    calendarId = calendarId,
                    reminderMinutes = action.reminderMinutes.ifEmpty { listOf(30) },
                ),
            )
            when (result) {
                is LifeResult.Success -> LifeResult.Success(result.value)
                is LifeResult.Failure -> result
            }
        }

        is LifeAction.CreateCalendar -> {
            val used = calendarRepository.calendars().map { it.colorArgb }
            val colour = if (action.colorName.isBlank()) {
                CalendarPalette.nextUnused(used)
            } else {
                CalendarPalette.parse(action.colorName)
            }
            when (
                val result = calendarRepository.createCalendar(
                    name = action.name,
                    colorArgb = colour,
                    makeDefault = action.makeDefault,
                )
            ) {
                is LifeResult.Success -> {
                    echo.text("Calendar \"${action.name}\" (${CalendarPalette.nameOf(colour)}) created")
                    LifeResult.Success(result.value)
                }
                is LifeResult.Failure -> result
            }
        }

        is LifeAction.SubscribeCalendar -> {
            if (!action.url.contains("://")) {
                LifeResult.Failure(LifeError.Validation("That does not look like an ICS link"))
            } else {
                val used = calendarRepository.calendars().map { it.colorArgb }
                val colour = if (action.colorName.isBlank()) {
                    CalendarPalette.nextUnused(used)
                } else {
                    CalendarPalette.parse(action.colorName)
                }
                when (
                    val created = calendarRepository.createCalendar(
                        name = action.name,
                        colorArgb = colour,
                        subscriptionUrl = action.url,
                    )
                ) {
                    is LifeResult.Success -> {
                        // Pull it immediately so the answer can say how many landed.
                        when (val synced = subscriptionSync.syncOne(created.value)) {
                            is LifeResult.Success ->
                                echo.text("Subscribed to \"${action.name}\": ${synced.value} event(s)")
                            is LifeResult.Failure ->
                                echo.text("Subscribed to \"${action.name}\", but the first pull failed: ${synced.error.message}")
                        }
                        LifeResult.Success(created.value)
                    }
                    is LifeResult.Failure -> created
                }
            }
        }

        is LifeAction.SyncCalendars -> when (val result = subscriptionSync.syncAll()) {
            is LifeResult.Success -> {
                echo.text("Subscriptions refreshed: ${result.value} event(s)")
                LifeResult.Success(result.value.toLong())
            }
            is LifeResult.Failure -> result
        }

        else -> LifeResult.Failure(LifeError.Validation("Unsupported action"))
    }

    /** Loose name matching, so "work" finds "Work travel". */
    private suspend fun matchCalendar(name: String): Long? {
        if (name.isBlank()) return null
        val needle = name.trim().lowercase()
        val calendars = calendarRepository.calendars().filter { it.subscriptionUrl == null }
        return (
            calendars.firstOrNull { it.name.lowercase() == needle }
                ?: calendars.firstOrNull { needle in it.name.lowercase() }
                ?: calendars.firstOrNull { it.name.lowercase() in needle }
            )?.id
    }
}
