package com.lifeos.feature.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the pieces the calendar's colours, alerts and ICS feeds hang on. */
class CalendarCodecTest {

    @Test
    fun `reminder offsets round trip sorted and deduped`() {
        val encoded = DefaultCalendarRepository.encodeReminders(listOf(60, 0, 30, 30))
        assertEquals("0,30,60", encoded)
        assertEquals(listOf(0, 30, 60), DefaultCalendarRepository.decodeReminders(encoded))
    }

    @Test
    fun `garbage in a reminder column is ignored rather than crashing`() {
        assertEquals(listOf(15), DefaultCalendarRepository.decodeReminders("15,,abc, "))
        assertEquals(emptyList<Int>(), DefaultCalendarRepository.decodeReminders(""))
    }

    @Test
    fun `offsets read the way a person would say them`() {
        assertEquals("now", DefaultCalendarRepository.humanOffset(0))
        assertEquals("45m", DefaultCalendarRepository.humanOffset(45))
        assertEquals("2h", DefaultCalendarRepository.humanOffset(120))
        assertEquals("1d", DefaultCalendarRepository.humanOffset(1440))
        assertEquals("7d", DefaultCalendarRepository.humanOffset(10080))
    }

    @Test
    fun `palette accepts names and hex and refuses nonsense`() {
        assertEquals(0xFF22C55E.toInt(), CalendarPalette.parse("green"))
        assertEquals(0xFF8B5CF6.toInt(), CalendarPalette.parse("#8B5CF6"))
        assertEquals(0xFF8B5CF6.toInt(), CalendarPalette.parse("8b5cf6"))
        assertEquals(CalendarPalette.default, CalendarPalette.parse("purple-ish"))
        assertEquals("#22C55E", CalendarPalette.hex(0xFF22C55E.toInt()))
    }

    @Test
    fun `a new calendar avoids colours already in use`() {
        val used = CalendarPalette.named.take(3).map { it.second }
        val picked = CalendarPalette.nextUnused(used)
        assertTrue(picked !in used)
        assertEquals(CalendarPalette.named[3].second, picked)
    }

    @Test
    fun `VALARM triggers become minutes before the start`() {
        assertEquals(30, IcsCodec.parseTriggerMinutes("-PT30M"))
        assertEquals(120, IcsCodec.parseTriggerMinutes("-PT2H"))
        assertEquals(1440, IcsCodec.parseTriggerMinutes("-P1D"))
        assertEquals(90, IcsCodec.parseTriggerMinutes("-PT1H30M"))
        // Alarms after the start are not something this calendar models.
        assertNull(IcsCodec.parseTriggerMinutes("PT15M"))
    }

    @Test
    fun `a holiday feed parses as all-day events with their uid`() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:2026-01-01-newyear@example.com
            SUMMARY:New Year's Day
            DTSTART;VALUE=DATE:20260101
            DTEND;VALUE=DATE:20260102
            END:VEVENT
            BEGIN:VEVENT
            UID:standup-42@example.com
            SUMMARY:Stand-up
            DTSTART:20260105T083000Z
            DTEND:20260105T090000Z
            LOCATION:Kitchen
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT10M
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsCodec.parse(ics)
        assertEquals(2, events.size)
        val holiday = events.first()
        assertEquals("New Year's Day", holiday.title)
        assertTrue(holiday.allDay)
        assertEquals("2026-01-01-newyear@example.com", holiday.uid)

        val standup = events[1]
        assertTrue(!standup.allDay)
        assertEquals("Kitchen", standup.location)
        assertEquals(listOf(10), standup.reminderMinutes)
    }
}
