package com.lifeos.feature.route.data

import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.CrossModuleRule
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeEvent
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * R6 (§3): a calendar event with a location gets a "leave by" reminder.
 * Travel-time estimation joins once location services land; a fixed 45-minute
 * head start is the honest v1.
 */
class LeaveByRule @Inject constructor() : CrossModuleRule {

    override val id = "location-event-leave-by"

    override fun matches(event: LifeEvent): Boolean =
        event is LifeEvent.CalendarEventChanged && event.hasLocation &&
            event.startsAt - LEAD_MILLIS > System.currentTimeMillis()

    override suspend fun produce(event: LifeEvent): List<LifeAction> {
        val calendarEvent = event as LifeEvent.CalendarEventChanged
        return listOf(
            LifeAction.CreateReminder(
                title = "Leave for: ${calendarEvent.title}",
                at = calendarEvent.startsAt - LEAD_MILLIS,
                source = SourceRef(LifeModule.ROUTE, calendarEvent.eventId.toString()),
            ),
        )
    }

    private companion object {
        val LEAD_MILLIS = TimeUnit.MINUTES.toMillis(45)
    }
}
