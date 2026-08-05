package com.lifeos.feature.reminders.data

import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.reminders.ReminderDao
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import javax.inject.Inject

/** Executes reminder actions for other modules (calendar, rules, Jarvis). */
internal class RemindersActionHandler @Inject constructor(
    private val remindersRepository: RemindersRepository,
    private val reminderDao: ReminderDao,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction): Boolean =
        action is LifeAction.CreateReminder || action is LifeAction.CancelRemindersFor

    override suspend fun execute(action: LifeAction): LifeResult<Long?> = when (action) {
        is LifeAction.CreateReminder -> when (
            val result = remindersRepository.create(
                title = action.title,
                at = action.at,
                recurrence = action.recurrence,
                source = action.source,
            )
        ) {
            is LifeResult.Success -> LifeResult.Success(result.value)
            is LifeResult.Failure -> result
        }

        is LifeAction.CancelRemindersFor -> {
            // Editing an event has to take its old alerts with it, or the phone
            // rings for a time that no longer exists.
            val stale = reminderDao.bySource(action.module, action.entityId)
            stale.forEach { remindersRepository.delete(it.id) }
            LifeResult.Success(stale.size.toLong())
        }

        else -> LifeResult.Success(null)
    }
}
