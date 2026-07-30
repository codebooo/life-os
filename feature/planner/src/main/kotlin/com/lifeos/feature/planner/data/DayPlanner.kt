package com.lifeos.feature.planner.data

import com.lifeos.core.database.calendar.CalendarDao
import com.lifeos.core.database.capture.CaptureDao
import com.lifeos.core.database.capture.TaskEntity
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** One placement the planner made, so the reply can list them. */
data class PlacedBlock(
    val title: String,
    val startsAt: Long,
    val endsAt: Long,
)

/**
 * Auto-scheduling (§Module Plan).
 *
 * Tasks and the calendar stop being two lists: due-dated tasks are placed into
 * real gaps between existing events, inside working hours, newest deadline
 * first. Every placement is an ordinary calendar event, so it can be moved or
 * deleted like anything else - nothing here is hidden magic.
 */
@Singleton
class DayPlanner @Inject constructor(
    private val calendarDao: CalendarDao,
    private val captureDao: CaptureDao,
    /**
     * Lazily: the planner creates events through the dispatcher, and the
     * planner's own action handler is registered in it - direct injection is a
     * dependency cycle.
     */
    private val dispatcherProvider: Provider<LifeActionDispatcher>,
) {

    private val dispatcher: LifeActionDispatcher get() = dispatcherProvider.get()

    /**
     * Plans one day.
     *
     * @param dayOffset 0 = today, 1 = tomorrow.
     * @return the blocks that were created, in order.
     */
    suspend fun plan(dayOffset: Int = 0): List<PlacedBlock> {
        val dayStart = startOfDay(dayOffset)
        val dayEnd = dayStart + DAY_MS
        val windowStart = maxOf(dayStart + WORK_START_MS, roundUpToQuarter(System.currentTimeMillis()))
        val windowEnd = dayStart + WORK_END_MS
        if (windowStart >= windowEnd) return emptyList()

        val events = calendarDao.observeWindow(dayStart, dayEnd).first().sortedBy { it.startsAt }
        val busy = events.map { it.startsAt to it.endsAt }
        val candidates = openTasks(dayEnd)
        if (candidates.isEmpty()) return emptyList()

        val placed = mutableListOf<PlacedBlock>()
        var cursor = windowStart
        for (task in candidates) {
            val slot = nextFreeSlot(cursor, windowEnd, busy + placed.map { it.startsAt to it.endsAt })
                ?: break
            val length = minOf(BLOCK_MS, slot.second - slot.first)
            if (length < MIN_BLOCK_MS) break
            val start = slot.first
            val end = start + length
            dispatcher.dispatch(
                LifeAction.CreateCalendarEvent(
                    title = "Focus: ${task.title.take(60)}",
                    startsAt = start,
                    endsAt = end,
                    source = SOURCE,
                ),
            )
            placed += PlacedBlock("Focus: ${task.title.take(60)}", start, end)
            cursor = end + GAP_MS
            if (placed.size >= MAX_BLOCKS) break
        }
        return placed
    }

    /** Open tasks worth scheduling: due soonest first, then oldest. */
    private suspend fun openTasks(dayEnd: Long): List<TaskEntity> {
        val tasks = captureDao.observeTasks().first().filter { !it.done }
        val due = tasks
            .mapNotNull { task -> task.dueAt?.let { due -> task to due } }
            .filter { (_, due) -> due <= dayEnd + 3 * DAY_MS }
            .sortedBy { (_, due) -> due }
            .map { (task, _) -> task }
        val rest = tasks.filter { it.dueAt == null }.sortedBy { it.createdAt }
        return (due + rest).take(MAX_BLOCKS)
    }

    /** First gap of at least [MIN_BLOCK_MS] at or after [from]. */
    private fun nextFreeSlot(from: Long, until: Long, busy: List<Pair<Long, Long>>): Pair<Long, Long>? {
        var cursor = from
        val sorted = busy.sortedBy { it.first }
        for ((start, end) in sorted) {
            if (end <= cursor) continue
            if (start - cursor >= MIN_BLOCK_MS) return cursor to minOf(start, until)
            cursor = maxOf(cursor, end)
            if (cursor >= until) return null
        }
        return if (until - cursor >= MIN_BLOCK_MS) cursor to until else null
    }

    private fun startOfDay(offset: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, offset)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun roundUpToQuarter(millis: Long): Long {
        val quarter = 15 * 60_000L
        return ((millis + quarter - 1) / quarter) * quarter
    }

    private companion object {
        val SOURCE = SourceRef(LifeModule.PLANNER, "auto-plan")
        const val DAY_MS = 86_400_000L
        /** Working window: 09:00 to 19:00. */
        const val WORK_START_MS = 9 * 3_600_000L
        const val WORK_END_MS = 19 * 3_600_000L
        const val BLOCK_MS = 50 * 60_000L
        const val MIN_BLOCK_MS = 25 * 60_000L
        const val GAP_MS = 10 * 60_000L
        const val MAX_BLOCKS = 5
    }
}
