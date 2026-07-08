package com.lifeos.app.voice

import com.lifeos.core.ai.AiRouter
import com.lifeos.core.ai.model.AiMessage
import com.lifeos.core.ai.model.AiRequest
import com.lifeos.core.ai.model.AiRole
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.capture.TaskEntity
import com.lifeos.core.database.dhl.PackageDao
import com.lifeos.core.database.todo.TodoDao
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
import com.lifeos.feature.capture.data.CaptureDestination
import com.lifeos.feature.capture.data.SmartCaptureParser
import com.lifeos.feature.planner.data.PlannerEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The assistant's command core (§Module 10): connects the overlay to every
 * module. Deterministic intents run instantly ("5m timer" → reminder,
 * "where is my package" → live tracking state, "what's next" → planner);
 * everything else flows to the AI router (on-device/NAS — never a cloud).
 */
@Singleton
class AssistantBrain @Inject constructor(
    private val aiRouter: AiRouter,
    private val actionDispatcher: LifeActionDispatcher,
    private val packageDao: PackageDao,
    private val todoDao: TodoDao,
    private val plannerEngine: PlannerEngine,
) {

    suspend fun handle(query: String): String {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return "I didn't catch that."
        val lower = trimmed.lowercase()

        // 1. Timers / reminders / events / timed to-dos — instant, on-device.
        SmartCaptureParser.detect(trimmed)?.let { smart ->
            val at = smart.at
            val time = at?.let { CLOCK.format(Date(it)) }
            return when (smart.destination) {
                CaptureDestination.TIMER, CaptureDestination.REMINDER -> {
                    actionDispatcher.dispatch(
                        LifeAction.CreateReminder(
                            title = smart.title.ifBlank { "Timer" },
                            at = at ?: return fallback(trimmed),
                            source = SOURCE,
                        ),
                    )
                    if (smart.destination == CaptureDestination.TIMER) {
                        "Timer set — it rings at $time."
                    } else {
                        "Reminder set for $time: ${smart.title}"
                    }
                }
                CaptureDestination.EVENT -> {
                    val startsAt = at ?: return fallback(trimmed)
                    actionDispatcher.dispatch(
                        LifeAction.CreateCalendarEvent(
                            title = smart.title,
                            startsAt = startsAt,
                            endsAt = startsAt + 3_600_000L,
                            source = SOURCE,
                        ),
                    )
                    "Added \"${smart.title}\" to your calendar at $time."
                }
                else -> {
                    todoDao.insertTask(
                        TaskEntity(
                            title = smart.title,
                            dueAt = at,
                            sourceModule = LifeModule.CAPTURE.name,
                            sourceEntityId = null,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                    "To-do saved${time?.let { " for $it" } ?: ""}: ${smart.title}"
                }
            }
        }

        // 2. Package tracking — live module data.
        if (PACKAGE_WORDS.any { it in lower }) {
            val packages = packageDao.activePackages()
            return when {
                packages.isEmpty() -> "No packages are being tracked right now."
                else -> packages.joinToString("\n") { pkg ->
                    val name = pkg.label ?: pkg.trackingNumber
                    val status = pkg.statusDescription ?: pkg.status.lowercase().replace('_', ' ')
                    val eta = pkg.estimatedDeliveryAt?.let { " — expected ${DAY.format(Date(it))}" } ?: ""
                    "$name: $status$eta"
                }
            }
        }

        // 3. "What's next" — the planner's top pick.
        if (NEXT_WORDS.any { it in lower }) {
            val next = plannerEngine.computePlan().firstOrNull()
                ?: return "Nothing needs you right now. Enjoy the quiet."
            return "Next up: ${next.title} (${next.reason})"
        }

        // 4. Open to-dos.
        if ("todo" in lower || "to-do" in lower || "tasks" in lower) {
            val next = todoDao.nextOpenTask() ?: return "Your to-do list is clear."
            return "Most urgent open task: ${next.title}"
        }

        // 5. Everything else → the AI router (on-device or NAS).
        return fallback(trimmed)
    }

    private suspend fun fallback(query: String): String {
        val result = aiRouter.complete(
            AiRequest(
                system = "You are LifeOS, a private on-device assistant. Answer in at most 3 short sentences.",
                messages = listOf(AiMessage(AiRole.USER, query)),
            ),
        )
        return when (result) {
            is LifeResult.Success -> result.value.text.trim()
            is LifeResult.Failure ->
                "I can set timers, reminders, events and check packages offline — for open questions, " +
                    "install the on-device model or configure the NAS in Assistant settings."
        }
    }

    private companion object {
        val SOURCE = SourceRef(LifeModule.CHAT, "assistant-overlay")
        val CLOCK = SimpleDateFormat("HH:mm", Locale.getDefault())
        val DAY = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        val PACKAGE_WORDS = listOf("package", "paket", "parcel", "delivery", "shipment", "tracking")
        val NEXT_WORDS = listOf("what's next", "whats next", "what now", "my plan", "next up")
    }
}
