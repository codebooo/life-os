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
 * SUMMARY/DTSTART/DTEND/LOCATION/DESCRIPTION — the subset Proton's ICS
 * bridges and every calendar app exchange. Deliberately dependency-free.
 */
object IcsCodec {

    private val UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun export(events: List<CalendarEventEntity>): String = buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//LifeOS//Calendar//EN")
        events.forEach { event ->
            appendLine("BEGIN:VEVENT")
            appendLine("UID:lifeos-${event.id}@lifeos.local")
            appendLine("DTSTAMP:${format(event.createdAt)}")
            appendLine("DTSTART:${format(event.startsAt)}")
            appendLine("DTEND:${format(event.endsAt)}")
            appendLine("SUMMARY:${escape(event.title)}")
            event.location?.takeIf { it.isNotBlank() }?.let { appendLine("LOCATION:${escape(it)}") }
            event.notes?.takeIf { it.isNotBlank() }?.let { appendLine("DESCRIPTION:${escape(it)}") }
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
    )

    fun parse(ics: String): List<ParsedEvent> {
        val events = mutableListOf<ParsedEvent>()
        var inEvent = false
        var fields = mutableMapOf<String, String>()
        // RFC 5545 folds long lines with a leading space — unfold first.
        val unfolded = ics.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "")
        unfolded.lineSequence().forEach { line ->
            when {
                line.startsWith("BEGIN:VEVENT") -> {
                    inEvent = true
                    fields = mutableMapOf()
                }
                line.startsWith("END:VEVENT") -> {
                    inEvent = false
                    val start = fields["DTSTART"]?.let(::parseInstant)
                    val title = fields["SUMMARY"]
                    if (start != null && !title.isNullOrBlank()) {
                        events += ParsedEvent(
                            title = unescape(title),
                            startsAt = start,
                            endsAt = fields["DTEND"]?.let(::parseInstant) ?: (start + 3_600_000),
                            location = fields["LOCATION"]?.let(::unescape),
                            notes = fields["DESCRIPTION"]?.let(::unescape),
                        )
                    }
                }
                inEvent -> {
                    val separator = line.indexOf(':')
                    if (separator > 0) {
                        // Strip parameters: "DTSTART;TZID=Europe/Berlin" → "DTSTART".
                        val key = line.substring(0, separator).substringBefore(';')
                        fields[key] = line.substring(separator + 1)
                    }
                }
            }
        }
        return events
    }

    private fun format(epochMs: Long): String =
        UTC_FORMAT.format(Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC))

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
