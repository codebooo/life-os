package com.lifeos.feature.calendar.data

import com.lifeos.core.database.calendar.CalendarEventEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Minimal iCalendar (RFC 5545) reader/writer (§8.6): VEVENT with
 * SUMMARY/DTSTART/DTEND/LOCATION/DESCRIPTION/UID plus VALARM offsets — the
 * subset Proton's ICS bridges, holiday feeds and every calendar app exchange.
 * Deliberately dependency-free.
 */
object IcsCodec {

    private val UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun export(events: List<CalendarEventEntity>): String = buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//LifeOS//Calendar//EN")
        appendLine("CALSCALE:GREGORIAN")
        events.forEach { event ->
            appendLine("BEGIN:VEVENT")
            appendLine("UID:${event.externalUid ?: "lifeos-${event.id}@lifeos.local"}")
            appendLine("DTSTAMP:${format(event.createdAt)}")
            if (event.allDay) {
                appendLine("DTSTART;VALUE=DATE:${formatDate(event.startsAt)}")
                appendLine("DTEND;VALUE=DATE:${formatDate(event.endsAt)}")
            } else {
                appendLine("DTSTART:${format(event.startsAt)}")
                appendLine("DTEND:${format(event.endsAt)}")
            }
            appendLine("SUMMARY:${escape(event.title)}")
            event.location?.takeIf { it.isNotBlank() }?.let { appendLine("LOCATION:${escape(it)}") }
            event.notes?.takeIf { it.isNotBlank() }?.let { appendLine("DESCRIPTION:${escape(it)}") }
            DefaultCalendarRepository.decodeReminders(event.reminderMinutes).forEach { minutes ->
                appendLine("BEGIN:VALARM")
                appendLine("ACTION:DISPLAY")
                appendLine("DESCRIPTION:${escape(event.title)}")
                appendLine("TRIGGER:-PT${minutes}M")
                appendLine("END:VALARM")
            }
            appendLine("END:VEVENT")
        }
        appendLine("END:VCALENDAR")
    }

    data class ParsedEvent(
        val title: String,
        val startsAt: Long,
        val endsAt: Long,
        val location: String?,
        val notes: String?,
        val uid: String? = null,
        val allDay: Boolean = false,
        /** Minutes-before offsets carried by the feed's VALARM blocks. */
        val reminderMinutes: List<Int> = emptyList(),
    )

    fun parse(ics: String): List<ParsedEvent> {
        val events = mutableListOf<ParsedEvent>()
        var inEvent = false
        var fields = mutableMapOf<String, String>()
        var alarms = mutableListOf<Int>()
        // RFC 5545 folds long lines with a leading space — unfold first.
        val unfolded = ics.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "")
        unfolded.lineSequence().forEach { line ->
            when {
                line.startsWith("BEGIN:VEVENT") -> {
                    inEvent = true
                    fields = mutableMapOf()
                    alarms = mutableListOf()
                }
                line.startsWith("END:VEVENT") -> {
                    inEvent = false
                    val rawStart = fields["DTSTART"]
                    val start = rawStart?.let(::parseInstant)
                    val title = fields["SUMMARY"]
                    if (rawStart != null && start != null && !title.isNullOrBlank()) {
                        // A date-only DTSTART is the ICS way of saying "all day".
                        val allDay = rawStart.length == 8 ||
                            fields["DTSTART_PARAMS"]?.contains("DATE") == true
                        events += ParsedEvent(
                            title = unescape(title),
                            startsAt = start,
                            endsAt = fields["DTEND"]?.let(::parseInstant)
                                ?: (start + if (allDay) 86_400_000L else 3_600_000L),
                            location = fields["LOCATION"]?.let(::unescape),
                            notes = fields["DESCRIPTION"]?.let(::unescape),
                            uid = fields["UID"]?.trim()?.ifBlank { null },
                            allDay = allDay,
                            reminderMinutes = alarms.toList(),
                        )
                    }
                }
                inEvent && line.startsWith("TRIGGER") -> {
                    parseTriggerMinutes(line.substringAfter(':'))?.let { alarms += it }
                }
                inEvent -> {
                    val separator = line.indexOf(':')
                    if (separator > 0) {
                        // Strip parameters: "DTSTART;TZID=Europe/Berlin" → "DTSTART",
                        // keeping them aside so VALUE=DATE still marks all-day rows.
                        val head = line.substring(0, separator)
                        val key = head.substringBefore(';')
                        if (head.contains(';')) fields["${key}_PARAMS"] = head.substringAfter(';')
                        fields[key] = line.substring(separator + 1)
                    }
                }
            }
        }
        return events
    }

    /** "-PT30M", "-PT2H", "-P1D" become minutes before the start. */
    internal fun parseTriggerMinutes(value: String): Int? {
        val trimmed = value.trim()
        if (!trimmed.startsWith("-P")) return null
        val body = trimmed.removePrefix("-P")
        val days = Regex("(\\d+)D").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val time = body.substringAfter('T', "")
        val hours = Regex("(\\d+)H").find(time)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minutes = Regex("(\\d+)M").find(time)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val total = days * 1440 + hours * 60 + minutes
        return if (total in 0..(60 * 24 * 30)) total else null
    }

    private fun format(epochMs: Long): String =
        UTC_FORMAT.format(Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC))

    private fun formatDate(epochMs: Long): String =
        DATE_FORMAT.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    private fun parseInstant(value: String): Long? = try {
        when {
            value.endsWith("Z") -> LocalDateTime.parse(value, UTC_FORMAT)
                .toInstant(ZoneOffset.UTC).toEpochMilli()
            value.length == 8 -> LocalDate.parse(value, DATE_FORMAT)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            else -> LocalDateTime.parse(value, LOCAL_FORMAT)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    } catch (_: Exception) {
        null
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    private fun unescape(value: String): String =
        value.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
