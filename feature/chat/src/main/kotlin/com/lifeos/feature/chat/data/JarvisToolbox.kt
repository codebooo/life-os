package com.lifeos.feature.chat.data

import com.lifeos.core.database.books.BookDao
import com.lifeos.core.database.calendar.CalendarDao
import com.lifeos.core.database.capture.CaptureDao
import com.lifeos.core.database.dhl.PackageDao
import com.lifeos.core.database.finance.FinanceDao
import com.lifeos.core.database.memex.MemexDao
import com.lifeos.core.database.notes.NoteDao
import com.lifeos.core.database.reminders.ReminderDao
import com.lifeos.core.database.todo.TodoDao
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
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
 * Jarvis's module infrastructure (§Module 9, alpha.14): every module exposes
 * its live content to the model through a compact [snapshot] injected into the
 * system prompt, and the model acts by emitting `[[tool: args]]` lines that
 * [runActions] parses and executes after generation. No pre-canned answers —
 * the LLM always writes the reply; the app performs (and confirms) the tools.
 */
@Singleton
class JarvisToolbox @Inject constructor(
    private val dispatcher: LifeActionDispatcher,
    private val captureDao: CaptureDao,
    private val todoDao: TodoDao,
    private val reminderDao: ReminderDao,
    private val calendarDao: CalendarDao,
    private val noteDao: NoteDao,
    private val memexDao: MemexDao,
    private val packageDao: PackageDao,
    private val financeDao: FinanceDao,
    private val bookDao: BookDao,
) {

    /** The tool contract the model sees. Kept terse — every char costs latency. */
    val toolSpec: String =
        """
        To act, emit commands on their own lines, exactly:
        [[add_task: title]] [[done_task: id]] [[delete_task: id]]
        [[timer: 5m]] [[remind: 18:00 | title]] [[remind: +25m | title]] [[cancel_reminder: id]]
        [[event: tomorrow 15:00 | title]] [[note: title | body]] [[search: words]]
        Use ids from LIVE DATA. Only claim an action you emitted. If no tool fits, just answer.
        """.trimIndent()

    /** Live cross-module state, compact, with stable ids the model can act on. */
    suspend fun snapshot(): String = buildString {
        val now = System.currentTimeMillis()
        appendLine("LIVE DATA (${STAMP.format(Date(now))}):")

        val open = captureDao.observeTasks().first().filter { !it.done }
        appendLine(
            if (open.isEmpty()) "Tasks: none" else
                "Tasks: " + open.take(8).joinToString("; ") { "[${it.id}] ${it.title.take(40)}" },
        )

        val reminders = reminderDao.observeAll().first()
            .filter { it.enabled && it.firedAt == null && it.at > now }
        appendLine(
            if (reminders.isEmpty()) "Reminders: none" else
                "Reminders: " + reminders.take(6).joinToString("; ") {
                    "[${it.id}] ${it.title.take(30)} ${AT.format(Date(it.at))}"
                },
        )

        val events = calendarDao.observeUpcoming(now).first()
        appendLine(
            if (events.isEmpty()) "Calendar: empty" else
                "Calendar: " + events.take(5).joinToString("; ") { "${it.title.take(30)} ${AT.format(Date(it.startsAt))}" },
        )

        val notes = noteDao.observeAll().first()
        if (notes.isNotEmpty()) {
            appendLine("Notes: " + notes.take(8).joinToString("; ") { it.title.take(30) })
        }

        val packages = packageDao.activePackages()
        if (packages.isNotEmpty()) {
            appendLine(
                "Packages: " + packages.take(4).joinToString("; ") {
                    "${(it.label ?: it.trackingNumber).take(24)}: ${(it.statusDescription ?: it.status).take(30)}"
                },
            )
        }

        val subs = financeDao.observeSubscriptions().first()
        if (subs.isNotEmpty()) {
            val monthly = subs.sumOf { if (it.cadence == "YEARLY") it.amountCents / 12 else it.amountCents }
            appendLine("Subscriptions: ${subs.size} active, ~${monthly / 100.0}€/mo")
        }

        val books = bookDao.observeAll().first()
        if (books.isNotEmpty()) {
            appendLine("Books: ${books.size} shelved, ${books.count { it.status == "READING" }} reading")
        }
    }.trim().take(900)

    /**
     * Finds `[[tool: args]]` tags in the model's reply, executes each, and
     * returns the cleaned text plus one confirmation line per executed tool —
     * the confirmations come from the app, never from the model.
     */
    suspend fun runActions(modelText: String): String {
        val results = mutableListOf<String>()
        TOOL_TAG.findAll(modelText).forEach { match ->
            val tool = match.groupValues[1].trim().lowercase()
            val args = match.groupValues[2].trim()
            runCatching { execute(tool, args) }
                .onSuccess { it?.let(results::add) }
                .onFailure { results += "✗ ${tool.replace('_', ' ')} failed: ${it.message}" }
        }
        val cleaned = modelText.replace(TOOL_TAG, "").replace(Regex("\\n{3,}"), "\n\n").trim()
        return when {
            results.isEmpty() -> cleaned
            cleaned.isBlank() -> results.joinToString("\n")
            else -> cleaned + "\n\n" + results.joinToString("\n")
        }
    }

    private suspend fun execute(tool: String, args: String): String? = when (tool) {
        "add_task" -> {
            val title = args.trim().trim('"').take(80)
            if (title.isBlank()) null else {
                dispatcher.dispatch(LifeAction.CreateTask(title, SOURCE))
                "✓ Added task: $title"
            }
        }
        "done_task" -> withTask(args) { id, title ->
            captureDao.setTaskDone(id, true); "✓ Checked off: $title"
        }
        "delete_task" -> withTask(args) { id, title ->
            todoDao.deleteWithChildren(id); "✓ Deleted task: $title"
        }
        "timer" -> {
            val ms = parseDuration(args) ?: error("bad duration \"$args\"")
            dispatcher.dispatch(LifeAction.CreateReminder("Timer", System.currentTimeMillis() + ms, SOURCE))
            "✓ Timer set — rings in ${human(ms)}"
        }
        "remind" -> {
            val (whenPart, title) = splitArgs(args)
            val at = parseWhen(whenPart) ?: error("bad time \"$whenPart\"")
            dispatcher.dispatch(LifeAction.CreateReminder(title.ifBlank { "Reminder" }, at, SOURCE))
            "✓ Reminder ${AT.format(Date(at))}: ${title.ifBlank { "Reminder" }}"
        }
        "cancel_reminder" -> {
            val id = args.trim().toLongOrNull() ?: error("bad id")
            val r = reminderDao.getById(id) ?: error("no reminder $id")
            reminderDao.setEnabled(id, false)
            "✓ Cancelled reminder: ${r.title}"
        }
        "event" -> {
            val (whenPart, title) = splitArgs(args)
            val at = parseWhen(whenPart) ?: error("bad time \"$whenPart\"")
            dispatcher.dispatch(LifeAction.CreateCalendarEvent(title.ifBlank { "Event" }, at, at + 3_600_000L, SOURCE))
            "✓ Event ${AT.format(Date(at))}: $title"
        }
        "note" -> {
            val (title, body) = splitArgs(args)
            dispatcher.dispatch(LifeAction.CreateNote(title.take(48), body.ifBlank { title }, SOURCE))
            "✓ Note saved: ${title.take(48)}"
        }
        "search" -> search(args)
        else -> null
    }

    private suspend fun withTask(args: String, act: suspend (Long, String) -> String): String {
        val id = args.trim().toLongOrNull() ?: error("bad id \"$args\"")
        val task = captureDao.observeTasks().first().firstOrNull { it.id == id } ?: error("no task $id")
        return act(id, task.title)
    }

    private suspend fun search(query: String): String {
        val terms = query.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in SEARCH_STOP }
        if (terms.isEmpty()) return "✗ Nothing to search for."
        fun hit(text: String?): Boolean {
            val t = text?.lowercase() ?: return false
            return terms.any { term ->
                term in t || (term.endsWith("s") && term.dropLast(1) in t) || "${term}s" in t
            }
        }

        val hits = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            noteDao.observeAll().first().forEach { note ->
                if (note.bodyVaultRef != null) return@forEach
                val body = runCatching { File(note.path).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()
                val line = body.lineSequence().firstOrNull(::hit)
                if (line != null) hits += "📝 ${note.title}: ${line.trim().take(140)}"
                else if (hit(note.title)) hits += "📝 ${note.title}"
            }
        }
        memexDao.observeAll().first().forEach { if (hit(it.body) || hit(it.title)) hits += "🗄 ${it.title}: ${it.body.trim().take(140)}" }
        captureDao.observeRecent().first().forEach { if (hit(it.text)) hits += "⚡ ${it.text!!.trim().take(140)}" }
        captureDao.observeTasks().first().forEach { if (hit(it.title)) hits += "☑ ${it.title}" }
        bookDao.observeAll().first().forEach { if (hit(it.title) || hit(it.author)) hits += "📚 ${it.title} — ${it.author}" }

        return if (hits.isEmpty()) {
            "✗ Searched notes, memex, captures, tasks and books — no match for \"$query\"."
        } else {
            "🔍 Found:\n" + hits.take(5).joinToString("\n")
        }
    }

    // ---- tiny parsers --------------------------------------------------------

    private fun splitArgs(args: String): Pair<String, String> {
        val idx = args.indexOf('|')
        return if (idx < 0) args.trim() to "" else args.take(idx).trim() to args.substring(idx + 1).trim()
    }

    private fun parseDuration(text: String): Long? {
        val m = Regex("(\\d+)\\s*(h|hr|hour|m|min|minute|s|sec|second|d|day)?[a-z]*", RegexOption.IGNORE_CASE)
            .find(text.trim()) ?: return null
        val n = m.groupValues[1].toLongOrNull() ?: return null
        return when (m.groupValues[2].lowercase().firstOrNull()) {
            'h' -> n * 3_600_000L
            's' -> n * 1_000L
            'd' -> n * 86_400_000L
            else -> n * 60_000L
        }
    }

    /** "+25m", "18:00", "tomorrow 15:00", "today 9pm". */
    private fun parseWhen(text: String, now: Long = System.currentTimeMillis()): Long? {
        val t = text.trim().lowercase()
        if (t.startsWith("+")) return parseDuration(t.drop(1))?.let { now + it }
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(t) ?: return null
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        if (m.groupValues[3] == "pm" && hour < 12) hour += 12
        if (m.groupValues[3] == "am" && hour == 12) hour = 0
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if ("tomorrow" in t) cal.add(Calendar.DAY_OF_YEAR, 1)
        else if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun human(ms: Long): String {
        val min = ms / 60_000
        return when {
            min >= 60 -> "${min / 60}h ${min % 60}m"
            min >= 1 -> "$min min"
            else -> "${ms / 1000}s"
        }
    }

    private companion object {
        val SOURCE = SourceRef(LifeModule.CHAT, "jarvis")
        val TOOL_TAG = Regex("\\[\\[\\s*([a-z_]+)\\s*:\\s*([^\\]]*?)\\s*]]", RegexOption.IGNORE_CASE)
        val AT = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        val STAMP = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())
        val SEARCH_STOP = setOf("the", "and", "for", "with", "search", "find", "look", "please")
    }
}
