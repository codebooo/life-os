package com.lifeos.feature.planner.data

import com.lifeos.core.ai.AiRouter
import com.lifeos.core.ai.model.AiMessage
import com.lifeos.core.ai.model.AiRequest
import com.lifeos.core.ai.model.AiRole
import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.calendar.CalendarDao
import com.lifeos.core.database.capture.CaptureDao
import com.lifeos.core.database.dhl.PackageDao
import com.lifeos.core.database.finance.FinanceDao
import com.lifeos.core.database.reminders.ReminderDao
import com.lifeos.core.datastore.SettingsRepository
import com.lifeos.core.model.LifeModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One ranked "what's next" candidate (§Module 24, [src 40]). */
data class PlanItem(
    val module: LifeModule,
    val entityId: Long,
    val title: String,
    val reason: String,
    val score: Double,
    val dueAt: Long?,
)

/**
 * The Jarvis loop (§5.2): gather a compact cross-module snapshot, score with
 * a transparent heuristic (urgency × importance × time-fit), optionally let
 * on-device Gemma write a day rationale. Reads everything, writes nothing —
 * the planner proposes, the user disposes.
 */
@Singleton
class PlannerEngine @Inject constructor(
    private val reminderDao: ReminderDao,
    private val calendarDao: CalendarDao,
    private val captureDao: CaptureDao,
    private val packageDao: PackageDao,
    private val financeDao: FinanceDao,
    private val settingsRepository: SettingsRepository,
    private val aiRouter: AiRouter,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun computePlan(now: Long = System.currentTimeMillis()): List<PlanItem> =
        withContext(dispatchers.io) {
            val dismissed = settingsRepository.plannerDismissed.first()
            val items = mutableListOf<PlanItem>()

            reminderDao.pendingAfter(now - TimeUnit.DAYS.toMillis(1)).forEach { reminder ->
                items += PlanItem(
                    module = LifeModule.REMINDERS,
                    entityId = reminder.id,
                    title = reminder.title,
                    reason = reasonForDue(reminder.at, now, "reminder"),
                    score = urgencyScore(reminder.at, now, base = 1.0),
                    dueAt = reminder.at,
                )
            }

            calendarDao.observeUpcoming(now, limit = 10).first().forEach { event ->
                items += PlanItem(
                    module = LifeModule.CALENDAR,
                    entityId = event.id,
                    title = event.title,
                    reason = reasonForDue(event.startsAt, now, "event"),
                    score = urgencyScore(event.startsAt, now, base = 1.2),
                    dueAt = event.startsAt,
                )
            }

            captureDao.observeTasks().first().filter { !it.done }.take(15).forEach { task ->
                val age = now - task.createdAt
                items += PlanItem(
                    module = LifeModule.TODO,
                    entityId = task.id,
                    title = task.title,
                    reason = if (age > TimeUnit.DAYS.toMillis(3)) "open for ${age / 86_400_000} days" else "open task",
                    score = 0.35 + minOf(0.3, age / TimeUnit.DAYS.toMillis(10).toDouble()),
                    dueAt = task.dueAt,
                )
            }

            packageDao.activePackages().forEach { pkg ->
                val today = pkg.estimatedDeliveryAt?.let { it - now < TimeUnit.DAYS.toMillis(1) } == true
                if (today) {
                    items += PlanItem(
                        module = LifeModule.DHL,
                        entityId = pkg.id,
                        title = "Package arriving: ${pkg.label ?: pkg.trackingNumber}",
                        reason = "estimated delivery today",
                        score = 0.9,
                        dueAt = pkg.estimatedDeliveryAt,
                    )
                }
            }

            financeDao.observeSubscriptions().first().take(3).forEach { sub ->
                val nextCharge = sub.lastChargedAt +
                    if (sub.cadence == "YEARLY") TimeUnit.DAYS.toMillis(365) else TimeUnit.DAYS.toMillis(30)
                if (nextCharge - now < TimeUnit.DAYS.toMillis(3)) {
                    items += PlanItem(
                        module = LifeModule.FINANCE,
                        entityId = sub.id,
                        title = "${sub.merchant} renews soon — still worth it?",
                        reason = "recurring charge in <3 days",
                        score = 0.5,
                        dueAt = nextCharge,
                    )
                }
            }

            items
                .filterNot { "${it.module}-${it.entityId}" in dismissed }
                .sortedByDescending { it.score }
                .take(12)
        }

    /** One-paragraph on-device rationale for the day; empty when no engine. */
    suspend fun rationale(plan: List<PlanItem>): String? {
        if (plan.isEmpty()) return null
        val request = AiRequest(
            system = "In 2 sentences, coach the user through this ranked plan. Direct, warm, no lists.",
            messages = listOf(
                AiMessage(AiRole.USER, plan.take(5).joinToString("\n") { "${it.title} (${it.reason})" }),
            ),
            localOnly = true,
        )
        return when (val result = aiRouter.complete(request)) {
            is LifeResult.Success -> result.value.text.takeIf { it.isNotBlank() }
            is LifeResult.Failure -> null
        }
    }

    companion object {
        /** Urgency rises as the deadline approaches; overdue saturates at the top. */
        fun urgencyScore(dueAt: Long, now: Long, base: Double): Double {
            val hoursLeft = (dueAt - now) / 3_600_000.0
            return when {
                hoursLeft <= 0 -> base + 1.0
                hoursLeft < 1 -> base + 0.9
                hoursLeft < 4 -> base + 0.7
                hoursLeft < 24 -> base + 0.4
                hoursLeft < 72 -> base + 0.15
                else -> base
            }
        }

        fun reasonForDue(dueAt: Long, now: Long, kind: String): String {
            val minutesLeft = (dueAt - now) / 60_000
            return when {
                dueAt <= now -> "$kind overdue"
                minutesLeft < 60 -> "$kind in $minutesLeft min"
                minutesLeft < 60 * 24 -> "$kind in ${minutesLeft / 60}h"
                else -> "$kind in ${minutesLeft / (60 * 24)} days"
            }
        }
    }
}
