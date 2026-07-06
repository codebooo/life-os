package com.lifeos.feature.capture.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SmartCaptureParserTest {

    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.JULY, 6, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `timer with minutes maps to TIMER at now plus duration`() {
        val result = SmartCaptureParser.detect("timer 6m", now)!!
        assertEquals(CaptureDestination.TIMER, result.destination)
        assertEquals(now + 6 * 60_000L, result.at)
    }

    @Test
    fun `six pm feed cat is a timed task later today`() {
        val result = SmartCaptureParser.detect("6pm feed cat", now)!!
        assertEquals(CaptureDestination.TASK, result.destination)
        val cal = Calendar.getInstance().apply { timeInMillis = result.at!! }
        assertEquals(18, cal.get(Calendar.HOUR_OF_DAY))
        assertTrue(result.title.contains("feed cat", ignoreCase = true))
    }

    @Test
    fun `remind me routes to REMINDER`() {
        val result = SmartCaptureParser.detect("remind me at 3pm to call mum", now)!!
        assertEquals(CaptureDestination.REMINDER, result.destination)
    }

    @Test
    fun `event keyword with time routes to EVENT`() {
        val result = SmartCaptureParser.detect("dentist appointment tomorrow at 9", now)!!
        assertEquals(CaptureDestination.EVENT, result.destination)
    }

    @Test
    fun `plain text without a time is not intercepted`() {
        assertNull(SmartCaptureParser.detect("buy 2 apples", now))
    }
}
