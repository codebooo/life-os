package com.lifeos.feature.reminders.data

import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import javax.inject.Inject

/** Executes [LifeAction.CreateReminder] for other modules (calendar, rules). */
internal class RemindersActionHandler @Inject constructor(
    private val remindersRepository: RemindersRepository,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction): Boolean = action is LifeAction.CreateReminder

    override suspend fun execute(action: LifeAction): LifeResult<Long?> {
        val create = action as LifeAction.CreateReminder
        return when (val result = remindersRepository.create(
            title = create.title,
            at = create.at,
            source = create.source,
        )) {
            is LifeResult.Success -> LifeResult.Success(result.value)
            is LifeResult.Failure -> result
        }
    }
}
