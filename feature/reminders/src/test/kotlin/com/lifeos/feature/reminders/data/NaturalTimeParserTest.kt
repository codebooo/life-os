package com.lifeos.feature.reminders.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NaturalTimeParserTest {

    private val now: Long = Calendar.getInstance().apply {
        set(2026, Calendar.JULY, 5, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `relative minutes`() {
        assertEquals(now + 30 * 60_000L, NaturalTimeParser.parse("in 30 minutes", now))
        assertEquals(now + 2 * 3_600_000L, NaturalTimeParser.parse("in 2 hours", now))
    }

    @Test
    fun `tomorrow at explicit time`() {
        val parsed = NaturalTimeParser.parse("tomorrow at 9", now)!!
        val calendar = Calendar.getInstance().apply { timeInMillis = parsed }
        assertEquals(6, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, calendar.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `pm times convert to 24h`() {
        val parsed = NaturalTimeParser.parse("today at 6pm", now)!!
        val calendar = Calendar.getInstance().apply { timeInMillis = parsed }
        assertEquals(18, calendar.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `bare past time rolls to the next day`() {
        val parsed = NaturalTimeParser.parse("at 8", now)!!
        assertTrue(parsed > now)
    }

    @Test
    fun `weekday resolves to a future date`() {
        val parsed = NaturalTimeParser.parse("friday at 18:30", now)
        assertNotNull(parsed)
        val calendar = Calendar.getInstance().apply { timeInMillis = parsed!! }
        assertEquals(Calendar.FRIDAY, calendar.get(Calendar.DAY_OF_WEEK))
        assertEquals(18, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calendar.get(Calendar.MINUTE))
        assertTrue(parsed!! > now)
    }

    @Test
    fun `unparseable text returns null`() {
        assertNull(NaturalTimeParser.parse("whenever you feel like it", now))
    }
}
