package com.lifeos.feature.chat.data

import com.lifeos.core.database.capture.CaptureDao
import com.lifeos.core.database.dhl.PackageDao
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns plain-language chat into real module actions (§Module 9): the AI in
 * the chat used to only *say* it added a task. This runs first — deterministic
 * intents (add task, reminder, timer, event, read packages/tasks) execute via
 * the dispatcher/DAOs and return a confirmation; anything else returns null so
 * the message flows to the LLM.
 */
@Singleton
class ChatCommandHandler @Inject constructor(
    private val dispatcher: LifeActionDispatcher,
    private val captureDao: CaptureDao,
    private val packageDao: PackageDao,
) {

    suspend fun tryHandle(text: String): String? {
        val raw = text.trim()
        val lower = raw.lowercase()

        // --- Read: packages ---------------------------------------------------
        if (PACKAGE_WORDS.any { it in lower } && QUESTION_WORDS.any { it in lower }) {
            val packages = packageDao.activePackages()
            return if (packages.isEmpty()) {
                "No packages are being tracked right now."
            } else {
                packages.joinToString("\n") { pkg ->
                    val name = pkg.label ?: pkg.trackingNumber
                    val status = pkg.statusDescription ?: pkg.status.lowercase().replace('_', ' ')
                    val eta = pkg.estimatedDeliveryAt?.let { " — expected ${DAY.format(Date(it))}" } ?: ""
                    "$name: $status$eta"
                }
            }
        }

        // --- Read: tasks ------------------------------------------------------
        if (LIST_VERB.containsMatchIn(lower) && TASK_WORDS.any { it in lower }) {
            val open = captureDao.observeTasks().first().filter { !it.done }
            return if (open.isEmpty()) {
                "Your to-do list is clear."
            } else {
                "You have ${open.size} open task(s):\n" + open.take(10).joinToString("\n") { "• ${it.title}" }
            }
        }

        val source = SourceRef(LifeModule.CHAT, "chat-command")

        // --- Timer ("set a timer for 5 minutes", "5m timer") -----------------
        if ("timer" in lower) {
            val durationMs = compactDuration(lower)
            if (durationMs != null) {
                dispatcher.dispatch(
                    LifeAction.CreateReminder("Timer", System.currentTimeMillis() + durationMs, source),
                )
                return "Timer set — it rings in ${humanDuration(durationMs)}."
            }
        }

        // --- Reminder ("remind me at 3pm to call mum") -----------------------
        if (REMINDER_WORDS.any { it in lower }) {
            val at = parseTime(lower) ?: compactDuration(lower)?.let { System.currentTimeMillis() + it }
            if (at != null) {
                val title = cleanReminderTitle(raw)
                dispatcher.dispatch(LifeAction.CreateReminder(title, at, source))
                return "Reminder set for ${CLOCK.format(Date(at))}: $title"
            }
        }

        // --- Calendar event --------------------------------------------------
        if (EVENT_WORDS.any { it in lower }) {
            val at = parseTime(lower)
            if (at != null) {
                val title = cleanReminderTitle(raw)
                dispatcher.dispatch(
                    LifeAction.CreateCalendarEvent(title, at, at + 3_600_000L, source),
                )
                return "Added \"$title\" to your calendar at ${CLOCK.format(Date(at))}."
            }
        }

        // --- Add task ("add X to my todo list", "create task X") -------------
        val taskTitle = extractTaskTitle(raw, lower)
        if (taskTitle != null) {
            dispatcher.dispatch(LifeAction.CreateTask(taskTitle, source))
            return "Added \"$taskTitle\" to your to-do list."
        }

        return null
    }

    /** Pulls the task text out of an add-task phrasing, or null if not one. */
    private fun extractTaskTitle(raw: String, lower: String): String? {
        val isAdd = ADD_VERB.containsMatchIn(lower)
        val mentionsList = TASK_WORDS.any { it in lower }
        if (!isAdd || !mentionsList) return null
        // Prefer quoted text.
        QUOTED.find(raw)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        // Else strip the scaffolding words.
        var t = raw
        t = t.replace(ADD_VERB, " ")
        t = t.replace(TO_MY_LIST, " ")
        t = t.replace(Regex("(?i)\\b(a|an|the|task|todo|to-do|to do|item|reminder)\\b"), " ")
        val cleaned = t.replace(Regex("\\s+"), " ").trim().trim('"', '“', '”')
        return cleaned.ifBlank { null }?.replaceFirstChar { it.uppercase() }
    }

    private fun cleanReminderTitle(raw: String): String =
        raw.replace(Regex("(?i)\\bremind me( to)?\\b"), " ")
            .replace(LEADING_TIME, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { raw }
            .replaceFirstChar { it.uppercase() }

    private fun compactDuration(lower: String): Long? {
        val m = DURATION.find(lower) ?: return null
        val amount = m.groupValues[1].toLongOrNull() ?: return null
        return when (m.groupValues[2].first()) {
            's' -> amount * 1_000L
            'h' -> amount * 3_600_000L
            'd' -> amount * 86_400_000L
            else -> amount * 60_000L
        }
    }

    private fun parseTime(lower: String, now: Long = System.currentTimeMillis()): Long? {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val t = TIME.find(lower) ?: return null
        val hour = t.groupValues[1].toIntOrNull() ?: return null
        val minute = t.groupValues[2].toIntOrNull() ?: 0
        val meridiem = t.groupValues[3]
        val hasClock = meridiem.isNotEmpty() || t.groupValues[2].isNotEmpty()
        if (!hasClock && !lower.contains("tomorrow") && !lower.contains("tonight")) return null
        val h = when {
            meridiem == "pm" && hour < 12 -> hour + 12
            meridiem == "am" && hour == 12 -> 0
            lower.contains("tonight") && hour < 12 -> hour + 12
            else -> hour
        }
        cal.set(Calendar.HOUR_OF_DAY, h)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (lower.contains("tomorrow")) cal.add(Calendar.DAY_OF_YEAR, 1)
        else if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun humanDuration(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m".removeSuffix(" 0m")
            minutes >= 1 -> "$minutes min"
            else -> "${ms / 1000}s"
        }
    }

    private companion object {
        val CLOCK = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        val DAY = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        val ADD_VERB = Regex("(?i)\\b(add|create|new|remember|put|note down|jot)\\b")
        val LIST_VERB = Regex("(?i)\\b(list|show|what('| i)?s|whats|how many|do i have)\\b")
        val TO_MY_LIST = Regex("(?i)\\bto (my )?(todo|task|to-do|to do)s?( list)?\\b")
        val QUOTED = Regex("[\"“](.+?)[\"”]")
        val DURATION = Regex("(\\d+)\\s*(h(?:ou)?rs?|min(?:ute)?s?|m\\b|s(?:ec(?:ond)?s?)?|d(?:ays?)?)")
        val TIME = Regex("(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b")
        val LEADING_TIME = Regex("(?i)\\b(at\\s+)?\\d{1,2}(:\\d{2})?\\s*(am|pm)?\\b|\\b(tomorrow|tonight|today)\\b")
        val PACKAGE_WORDS = listOf("package", "parcel", "delivery", "shipment", "tracking")
        val QUESTION_WORDS = listOf("where", "when", "status", "arrive", "how", "?")
        val TASK_WORDS = listOf("todo", "to-do", "to do", "task")
        val REMINDER_WORDS = listOf("remind", "reminder", "don't forget", "dont forget")
        val EVENT_WORDS = listOf("meeting", "appointment", "event", "calendar", "schedule", "dentist", "call with", "lunch", "dinner")
    }
}
