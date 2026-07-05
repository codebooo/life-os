package com.lifeos.feature.reminders.data

import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.result.runCatchingLife
import com.lifeos.core.database.reminders.ReminderDao
import com.lifeos.core.database.reminders.ReminderEntity
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeEvent
import com.lifeos.core.service.LifeEventBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface RemindersRepository {
    fun observeAll(): Flow<List<ReminderEntity>>
    suspend fun create(
        title: String,
        at: Long,
        notes: String? = null,
        recurrence: String = "NONE",
        source: SourceRef? = null,
    ): LifeResult<Long>

    suspend fun setEnabled(reminderId: Long, enabled: Boolean)
    suspend fun delete(reminderId: Long)
    suspend fun snooze(reminderId: Long, byMinutes: Int): LifeResult<Long>

    /** Called by the alarm receiver; marks fired, publishes, and re-arms recurrences. */
    suspend fun onFired(reminderId: Long)

    /** Boot-time re-arm of every pending alarm (§Module 2). */
    suspend fun rescheduleAll()
}

@Singleton
internal class DefaultRemindersRepository @Inject constructor(
    private val reminderDao: ReminderDao,
    private val scheduler: ReminderScheduler,
    private val eventBus: LifeEventBus,
    private val dispatchers: DispatcherProvider,
) : RemindersRepository {

    override fun observeAll(): Flow<List<ReminderEntity>> = reminderDao.observeAll()

    override suspend fun create(
        title: String,
        at: Long,
        notes: String?,
        recurrence: String,
        source: SourceRef?,
    ): LifeResult<Long> = withContext(dispatchers.io) {
        runCatchingLife {
            val reminder = ReminderEntity(
                title = title,
                notes = notes,
                at = at,
                recurrence = recurrence,
                sourceModule = source?.module?.name,
                sourceEntityId = source?.entityId?.toLongOrNull(),
                createdAt = System.currentTimeMillis(),
            )
            val id = reminderDao.insert(reminder)
            scheduler.schedule(reminder.copy(id = id))
            id
        }
    }

    override suspend fun setEnabled(reminderId: Long, enabled: Boolean) =
        withContext(dispatchers.io) {
            reminderDao.setEnabled(reminderId, enabled)
            val reminder = reminderDao.getById(reminderId) ?: return@withContext
            if (enabled) scheduler.schedule(reminder) else scheduler.cancel(reminderId)
        }

    override suspend fun delete(reminderId: Long) = withContext(dispatchers.io) {
        scheduler.cancel(reminderId)
        reminderDao.delete(reminderId)
    }

    override suspend fun snooze(reminderId: Long, byMinutes: Int): LifeResult<Long> =
        withContext(dispatchers.io) {
            runCatchingLife {
                val reminder = reminderDao.getById(reminderId) ?: error("Reminder not found")
                val newAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(byMinutes.toLong())
                val updated = reminder.copy(at = newAt, firedAt = null)
                reminderDao.update(updated)
                scheduler.schedule(updated)
                reminderId
            }
        }

    override suspend fun onFired(reminderId: Long) = withContext(dispatchers.io) {
        val reminder = reminderDao.getById(reminderId) ?: return@withContext
        reminderDao.markFired(reminderId, System.currentTimeMillis())
        eventBus.tryPublish(LifeEvent.ReminderFired(reminderId, reminder.title))

        nextOccurrence(reminder.at, reminder.recurrence)?.let { nextAt ->
            val next = reminder.copy(at = nextAt, firedAt = null)
            reminderDao.update(next)
            scheduler.schedule(next)
        }
        Unit
    }

    override suspend fun rescheduleAll() = withContext(dispatchers.io) {
        reminderDao.pendingAfter(System.currentTimeMillis()).forEach(scheduler::schedule)
    }

    companion object {
        /** Next fire time for simple recurrences; null when non-recurring. */
        fun nextOccurrence(lastAt: Long, recurrence: String): Long? {
            val interval = when (recurrence) {
                "DAILY" -> TimeUnit.DAYS.toMillis(1)
                "WEEKLY" -> TimeUnit.DAYS.toMillis(7)
                "MONTHLY" -> TimeUnit.DAYS.toMillis(30)
                else -> return null
            }
            var next = lastAt + interval
            val now = System.currentTimeMillis()
            while (next <= now) next += interval
            return next
        }
    }
}
