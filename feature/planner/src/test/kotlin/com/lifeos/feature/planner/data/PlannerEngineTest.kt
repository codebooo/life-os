package com.lifeos.feature.planner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class PlannerEngineTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `overdue outranks distant, events outrank equal reminders`() {
        val overdue = PlannerEngine.urgencyScore(now - 1000, now, base = 1.0)
        val nextWeek = PlannerEngine.urgencyScore(now + TimeUnit.DAYS.toMillis(7), now, base = 1.0)
        val eventSoon = PlannerEngine.urgencyScore(now + TimeUnit.MINUTES.toMillis(30), now, base = 1.2)
        val reminderSoon = PlannerEngine.urgencyScore(now + TimeUnit.MINUTES.toMillis(30), now, base = 1.0)

        assertTrue(overdue > nextWeek)
        assertTrue(eventSoon > reminderSoon)
    }

    @Test
    fun `reasons read as human time`() {
        assertEquals("event in 30 min", PlannerEngine.reasonForDue(now + TimeUnit.MINUTES.toMillis(30), now, "event"))
        assertEquals("reminder overdue", PlannerEngine.reasonForDue(now - 1000, now, "reminder"))
        assertEquals("event in 2 days", PlannerEngine.reasonForDue(now + TimeUnit.DAYS.toMillis(2), now, "event"))
    }
}
