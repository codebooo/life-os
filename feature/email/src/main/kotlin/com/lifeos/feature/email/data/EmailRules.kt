package com.lifeos.feature.email.data

import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.CrossModuleRule
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeEvent
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** R2 (§3): invoice-looking mail suggests a pay-task. */
class InvoiceEmailRule @Inject constructor() : CrossModuleRule {
    override val id = "invoice-email-to-task"

    override fun matches(event: LifeEvent) =
        event is LifeEvent.EmailReceived && event.hasInvoiceSignal

    override suspend fun produce(event: LifeEvent): List<LifeAction> {
        val email = event as LifeEvent.EmailReceived
        return listOf(
            LifeAction.CreateTask(
                title = "Pay invoice: ${email.subject.take(60)}",
                source = SourceRef(LifeModule.EMAIL, email.emailId.toString()),
            ),
        )
    }
}

/** R7 (§3): parsed .ics invite becomes a calendar event. */
class InviteEmailRule @Inject constructor() : CrossModuleRule {
    override val id = "invite-email-to-event"

    override fun matches(event: LifeEvent) =
        event is LifeEvent.EmailReceived && event.inviteStartsAt != null

    override suspend fun produce(event: LifeEvent): List<LifeAction> {
        val email = event as LifeEvent.EmailReceived
        val startsAt = email.inviteStartsAt ?: return emptyList()
        if (startsAt < System.currentTimeMillis()) return emptyList()
        return listOf(
            LifeAction.CreateCalendarEvent(
                title = email.inviteTitle ?: email.subject,
                startsAt = startsAt,
                endsAt = startsAt + TimeUnit.HOURS.toMillis(1),
                source = SourceRef(LifeModule.EMAIL, email.emailId.toString()),
            ),
        )
    }
}
