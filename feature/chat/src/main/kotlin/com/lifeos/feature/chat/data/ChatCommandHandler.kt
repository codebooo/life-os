package com.lifeos.feature.chat.data

import android.content.Context
import com.lifeos.core.database.calendar.CalendarDao
import com.lifeos.core.database.capture.CaptureDao
import com.lifeos.core.database.dhl.PackageDao
import com.lifeos.core.database.memex.MemexDao
import com.lifeos.core.database.notes.NoteDao
import com.lifeos.core.database.reminders.ReminderDao
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jarvis's module toolbox (§Module 9). Runs before the LLM and actually
 * reads/writes every module it can via the dispatcher + DAOs. Creation intents
 * are checked before read intents (so "add X to my to-do **list**" isn't
 * mistaken for "show my list"). Returns the confirmation/answer, or null to
 * let the model handle an open question.
 */
@Singleton
class ChatCommandHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcher: LifeActionDispatcher,
    private val captureDao: CaptureDao,
    private val packageDao: PackageDao,
    private val reminderDao: ReminderDao,
    private val calendarDao: CalendarDao,
    private val noteDao: NoteDao,
    private val memexDao: MemexDao,
) {

    suspend fun tryHandle(text: String): String? {
        val raw = text.trim()
        val lower = raw.lowercase()
        val source = SourceRef(LifeModule.CHAT, "jarvis")
        val hasCreateVerb = CREATE_VERB.containsMatchIn(lower)

        // ---- CREATE intents first (they carry an explicit verb) --------------

        // Timer.
        if ("timer" in lower) {
            compactDuration(lower)?.let { ms ->
                dispatcher.dispatch(LifeAction.CreateReminder("Timer", System.currentTimeMillis() + ms, source))
                return "Timer set — it rings in ${humanDuration(ms)}."
            }
        }

        // Reminder.
        if (REMINDER_WORDS.any { it in lower }) {
            val at = parseTime(lower) ?: compactDuration(lower)?.let { System.currentTimeMillis() + it }
            if (at != null) {
                val title = cleanTitle(raw, REMINDER_STRIP)
                dispatcher.dispatch(LifeAction.CreateReminder(title, at, source))
                return "Reminder set for ${CLOCK.format(Date(at))}: $title"
            }
        }

        // Calendar event.
        if (hasCreateVerb && EVENT_WORDS.any { it in lower }) {
            val at = parseTime(lower)
            if (at != null) {
                val title = cleanTitle(raw, EVENT_STRIP)
                dispatcher.dispatch(LifeAction.CreateCalendarEvent(title, at, at + 3_600_000L, source))
                return "Added \"$title\" to your calendar at ${CLOCK.format(Date(at))}."
            }
        }

        // Note.
        if (hasCreateVerb && "note" in lower) {
            val body = cleanTitle(raw, NOTE_STRIP)
            if (body.isNotBlank()) {
                dispatcher.dispatch(LifeAction.CreateNote(body.take(48), body, source))
                return "Saved a note: \"$body\"."
            }
        }

        // Task / to-do.
        if (hasCreateVerb && TASK_WORDS.any { it in lower }) {
            val title = extractTaskTitle(raw)
            if (title != null) {
                dispatcher.dispatch(LifeAction.CreateTask(title, source))
                return "Added \"$title\" to your to-do list."
            }
        }

        // ---- SEARCH across modules -------------------------------------------
        if (SEARCH_VERB.containsMatchIn(lower)) {
            searchModules(lower)?.let { return it }
        }

        // ---- READ intents ----------------------------------------------------

        if (PACKAGE_WORDS.any { it in lower }) {
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

        if (READ_VERB.containsMatchIn(lower) && TASK_WORDS.any { it in lower }) {
            val open = captureDao.observeTasks().first().filter { !it.done }
            return if (open.isEmpty()) {
                "Your to-do list is clear."
            } else {
                "You have ${open.size} open task(s):\n" + open.take(10).joinToString("\n") { "• ${it.title}" }
            }
        }

        if (READ_VERB.containsMatchIn(lower) && REMINDER_WORDS.any { it in lower }) {
            val pending = reminderDao.observeAll().first().filter { it.enabled && it.at > System.currentTimeMillis() }
            return if (pending.isEmpty()) {
                "No upcoming reminders."
            } else {
                "Upcoming reminders:\n" + pending.take(10).joinToString("\n") { "• ${it.title} — ${CLOCK.format(Date(it.at))}" }
            }
        }

        if (READ_VERB.containsMatchIn(lower) && (CALENDAR_WORDS.any { it in lower })) {
            val now = System.currentTimeMillis()
            val events = calendarDao.observeUpcoming(now).first()
            return if (events.isEmpty()) {
                "Nothing on your calendar coming up."
            } else {
                "Coming up:\n" + events.take(10).joinToString("\n") { "• ${it.title} — ${DAY.format(Date(it.startsAt))} ${CLOCK.format(Date(it.startsAt))}" }
            }
        }

        return null
    }

    /**
     * Keyword search across the modules whose text is reachable: plain (non-vault)
     * note bodies on disk, the Memex archive, and captures. Vault-encrypted notes
     * are skipped (they need biometric unlock). Returns matching snippets or null.
     */
    private suspend fun searchModules(lower: String): String? {
        val terms = lower.split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 && it !in STOPWORDS }
        if (terms.isEmpty()) return null

        val hits = mutableListOf<String>()

        // Notes (plain .md files only).
        withContext(Dispatchers.IO) {
            noteDao.observeAll().first().forEach { note ->
                if (note.bodyVaultRef != null) return@forEach
                val body = runCatching { File(note.path).takeIf { it.exists() }?.readText() }
                    .getOrNull().orEmpty()
                val line = body.lineSequence().firstOrNull { l ->
                    terms.any { l.lowercase().contains(it) }
                }
                if (line != null) hits += "📝 ${note.title}: ${line.trim().take(160)}"
            }
        }

        // Memex archive (DB bodies).
        memexDao.observeAll().first().forEach { item ->
            if (terms.any { item.body.lowercase().contains(it) || item.title.lowercase().contains(it) }) {
                hits += "🗄 ${item.title}: ${item.body.trim().take(160)}"
            }
        }

        if (hits.isEmpty()) {
            return "I searched your notes, memex and captures but found nothing matching " +
                terms.joinToString(", ") + ". (Vault-locked notes aren't searchable.)"
        }
        return "Found this:\n" + hits.take(5).joinToString("\n")
    }

    private fun extractTaskTitle(raw: String): String? {
        QUOTED.find(raw)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        // "add <X> to (my) to-do list" → capture X exactly.
        ADD_TO_LIST.find(raw)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
            return it.replaceFirstChar { c -> c.uppercase() }
        }
        val cleaned = cleanTitle(raw, TASK_STRIP)
        return cleaned.ifBlank { null }
    }

    /** Strips scaffolding words/leading time so the saved title reads cleanly. */
    private fun cleanTitle(raw: String, strip: Regex): String =
        raw.replace(strip, " ")
            .replace(LEADING_TIME, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('"', '“', '”', ',', '.')
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
            minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
            minutes >= 1 -> "$minutes min"
            else -> "${ms / 1000}s"
        }
    }

    private companion object {
        val CLOCK = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        val DAY = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        val CREATE_VERB = Regex("(?i)\\b(add|create|new|remember|put|note down|jot|save|schedule|set)\\b")
        val READ_VERB = Regex("(?i)\\b(list|show|what('| i)?s|whats|what is|how many|do i have|any|upcoming|see|view)\\b")
        val QUOTED = Regex("[\"“](.+?)[\"”]")
        val ADD_TO_LIST = Regex(
            "(?i)(?:add|put|save)\\s+(.+?)\\s+to\\s+(?:my\\s+)?(?:todo|to-?do|to do|task)s?(?:\\s+list)?",
        )
        val TASK_STRIP = Regex("(?i)\\b(add|create|new|remember to|remember|put|jot|save|please|a|an|the|task|todo|to-do|to do|item|list|my)\\b")
        val REMINDER_STRIP = Regex("(?i)\\b(remind me to|remind me|reminder|set|a|please)\\b")
        val EVENT_STRIP = Regex("(?i)\\b(add|create|schedule|new|put|event|meeting|appointment|to (my )?calendar|please|a|an)\\b")
        val NOTE_STRIP = Regex("(?i)\\b(add|create|new|save|note down|note|a|please|that|saying)\\b")
        val DURATION = Regex("(\\d+)\\s*(h(?:ou)?rs?|min(?:ute)?s?|m\\b|s(?:ec(?:ond)?s?)?|d(?:ays?)?)")
        val TIME = Regex("(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b")
        val LEADING_TIME = Regex("(?i)\\b(at\\s+)?\\d{1,2}(:\\d{2})?\\s*(am|pm)?\\b|\\b(tomorrow|tonight|today)\\b")
        val SEARCH_VERB = Regex("(?i)\\b(find|search|look for|where('| i)?s|where is|locate|secret|do you see|dig up)\\b")
        // Instruction/filler words only — the actual subject ("secret", "code", …)
        // must survive as a search term.
        val STOPWORDS = setOf(
            "find", "search", "where", "look", "locate", "have", "hidden",
            "somewhere", "modules", "module", "please", "jarvis", "there", "them", "that", "this",
            "with", "your", "mine", "from", "into", "about", "would", "could", "should", "hey",
            "again", "right", "just", "some", "thing", "things", "want",
        )
        val PACKAGE_WORDS = listOf("package", "parcel", "delivery", "shipment", "tracking")
        val TASK_WORDS = listOf("todo", "to-do", "to do", "task")
        val REMINDER_WORDS = listOf("remind", "reminder")
        val EVENT_WORDS = listOf("meeting", "appointment", "event", "calendar")
        val CALENDAR_WORDS = listOf("calendar", "event", "schedule", "agenda")
    }
}
