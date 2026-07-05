package com.lifeos.feature.calendar.data

import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import javax.inject.Inject

/** Executes [LifeAction.CreateCalendarEvent] (R7's target). */
internal class CalendarActionHandler @Inject constructor(
    private val calendarRepository: CalendarRepository,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction) = action is LifeAction.CreateCalendarEvent

    override suspend fun execute(action: LifeAction): LifeResult<Long?> {
        val create = action as LifeAction.CreateCalendarEvent
        return when (
            val result = calendarRepository.create(
                title = create.title,
                startsAt = create.startsAt,
                endsAt = create.endsAt,
                remindMinutesBefore = 30,
            )
        ) {
            is LifeResult.Success -> LifeResult.Success(result.value)
            is LifeResult.Failure -> result
        }
    }
}
