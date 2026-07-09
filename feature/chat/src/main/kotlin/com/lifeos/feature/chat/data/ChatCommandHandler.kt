package com.lifeos.feature.chat.data

import android.content.Context
import com.lifeos.core.database.books.BookDao
import com.lifeos.core.database.calendar.CalendarDao
import com.lifeos.core.database.capture.CaptureDao
import com.lifeos.core.database.capture.TaskEntity
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
 * reads/writes every module reachable from `:core:database` + the dispatcher.
 *
 * Tool order matters: destructive verbs (complete/delete/cancel) are checked
 * first so "close that task" never falls into a create or read branch, then
 * create intents, then search, then reads. Anything that *looks* like a
 * command but can't be parsed gets an honest "couldn't parse" reply instead
 * of falling through to the LLM — the model must never get the chance to
 * claim it performed an action.
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
    private val todoDao: TodoDao,
    private val financeDao: FinanceDao,
    private val bookDao: BookDao,
) {

    suspend fun tryHandle(text: String): String? {
        val raw = text.trim()
        val lower = raw.lowercase()

        // ---- MODIFY tools (destructive verbs win over everything) ------------
        completeTasks(lower)?.let { return it }
        deleteTask(lower)?.let { return it }
        cancelReminder(lower)?.let { return it }

        // ---- CREATE tools -----------------------------------------------------
        createTimer(lower)?.let { return it }
        createReminder(raw, lower)?.let { return it }
        createEvent(raw, lower)?.let { return it }
        createNote(raw, lower)?.let { return it }
        createTask(raw, lower)?.let { return it }

        // ---- SEARCH across modules --------------------------------------------
        if (SEARCH_VERB.containsMatchIn(lower)) {
            searchModules(lower)?.let { return it }
        }

        // ---- READ tools --------------------------------------------------------
        readNote(lower)?.let { return it }
        readTools(lower)?.let { return it }

        // ---- Honest fallback: looked like a command, couldn't parse it. --------
        // Never hand an action request to the LLM — it will happily claim it
        // did the thing while nothing happened.
        if (ACTION_VERB.containsMatchIn(lower) && MODULE_NOUN.containsMatchIn(lower)) {
            return "I couldn't work out exactly what to do there. Things I can run directly:\n" +
                "• \"add buy milk to my to-do list\" · \"close the milk task\" · \"check off all tasks\"\n" +
                "• \"remind me at 6pm to feed the cat\" · \"timer 10m\" · \"cancel the cat reminder\"\n" +
                "• \"add dentist tomorrow 3pm to my calendar\" · \"note that the wifi code is 1234\"\n" +
                "• \"what's on my to-do list / calendar / reminders\" · \"find <anything>\" — " +
                "or rephrase without command words if you were just asking."
        }

        return null
    }

    // ---------------------------------------------------------------- modify --

    private suspend fun completeTasks(lower: String): String? {
        if (!COMPLETE_VERB.containsMatchIn(lower)) return null
        if (!TASK_NOUN.containsMatchIn(lower) && !TASK_REFERENCE.containsMatchIn(lower)) return null
        val open = captureDao.observeTasks().first().filter { !it.done }
        if (open.isEmpty()) return "Nothing to check off — your to-do list is already clear."

        if (ALL_WORDS.containsMatchIn(lower)) {
            open.forEach { captureDao.setTaskDone(it.id, true) }
            return "Done — checked off all ${open.size} open task(s)."
        }

        val byName = matchTasksByTitle(open, lower)
        val target = when {
            byName.size == 1 -> byName.first()
            byName.isEmpty() && open.size == 1 -> open.first() // "close that task"
            else -> null
        }
        if (target != null) {
            captureDao.setTaskDone(target.id, true)
            return "Checked off \"${target.title}\". ${remainingLine(open.size - 1)}"
        }
        return "Which one? Open tasks:\n" + open.take(8).joinToString("\n") { "• ${it.title}" }
    }

    private suspend fun deleteTask(lower: String): String? {
        if (!DELETE_VERB.containsMatchIn(lower) || !TASK_NOUN.containsMatchIn(lower)) return null
        val tasks = captureDao.observeTasks().first()
        if (tasks.isEmpty()) return "Your to-do list is already empty."
        if (ALL_WORDS.containsMatchIn(lower)) {
            tasks.forEach { todoDao.deleteWithChildren(it.id) }
            return "Deleted all ${tasks.size} task(s)."
        }
        val byName = matchTasksByTitle(tasks, lower)
        val target = when {
            byName.size == 1 -> byName.first()
            byName.isEmpty() && tasks.size == 1 -> tasks.first()
            else -> null
        }
        if (target != null) {
            todoDao.deleteWithChildren(target.id)
            return "Deleted \"${target.title}\"."
        }
        return "Which task should I delete?\n" + tasks.take(8).joinToString("\n") { "• ${it.title}" }
    }

    private suspend fun cancelReminder(lower: String): String? {
        if (!DELETE_VERB.containsMatchIn(lower) || REMINDER_WORDS.none { it in lower }) return null
        val now = System.currentTimeMillis()
        val pending = reminderDao.observeAll().first().filter { it.enabled && it.firedAt == null && it.at > now }
        if (pending.isEmpty()) return "There are no pending reminders to cancel."
        val matched = pending.filter { r -> titleMatches(r.title, lower) }
        val target = when {
            matched.size == 1 -> matched.first()
            matched.isEmpty() && pending.size == 1 -> pending.first()
            ALL_WORDS.containsMatchIn(lower) -> {
                pending.forEach { reminderDao.setEnabled(it.id, false) }
                return "Cancelled all ${pending.size} pending reminder(s)."
            }
            else -> null
        }
        if (target != null) {
            // The alarm receiver re-checks the DB before ringing, so disabling
            // here really silences it even though the AlarmManager slot stays.
            reminderDao.setEnabled(target.id, false)
            return "Cancelled the reminder \"${target.title}\" (was ${CLOCK.format(Date(target.at))})."
        }
        return "Which reminder?\n" + pending.take(8).joinToString("\n") { "• ${it.title} — ${CLOCK.format(Date(it.at))}" }
    }

    // ---------------------------------------------------------------- create --

    private suspend fun createTimer(lower: String): String? {
        if ("timer" !in lower) return null
        val ms = compactDuration(lower) ?: return null
        dispatcher.dispatch(LifeAction.CreateReminder("Timer", System.currentTimeMillis() + ms, SOURCE))
        return "Timer set — it rings in ${humanDuration(ms)}."
    }

    private suspend fun createReminder(raw: String, lower: String): String? {
        if (REMINDER_WORDS.none { it in lower } || DELETE_VERB.containsMatchIn(lower)) return null
        val at = parseTime(lower) ?: compactDuration(lower)?.let { System.currentTimeMillis() + it } ?: return null
        val title = cleanTitle(raw, REMINDER_STRIP)
        dispatcher.dispatch(LifeAction.CreateReminder(title, at, SOURCE))
        return "Reminder set for ${CLOCK.format(Date(at))}: $title"
    }

    private suspend fun createEvent(raw: String, lower: String): String? {
        if (!CREATE_VERB.containsMatchIn(lower) || EVENT_WORDS.none { it in lower }) return null
        val at = parseTime(lower) ?: return null
        val title = cleanTitle(raw, EVENT_STRIP)
        dispatcher.dispatch(LifeAction.CreateCalendarEvent(title, at, at + 3_600_000L, SOURCE))
        return "Added \"$title\" to your calendar at ${DAY.format(Date(at))} ${CLOCK.format(Date(at))}."
    }

    private suspend fun createNote(raw: String, lower: String): String? {
        if (!CREATE_VERB.containsMatchIn(lower) || "note" !in lower) return null
        val body = cleanTitle(raw, NOTE_STRIP)
        if (body.isBlank()) return null
        dispatcher.dispatch(LifeAction.CreateNote(body.take(48), body, SOURCE))
        return "Saved a note: \"$body\"."
    }

    private suspend fun createTask(raw: String, lower: String): String? {
        if (!CREATE_VERB.containsMatchIn(lower)) return null
        if (!TASK_NOUN.containsMatchIn(lower) && "list" !in lower) return null
        val title = extractTaskTitle(raw) ?: return null
        dispatcher.dispatch(LifeAction.CreateTask(title, SOURCE))
        return "Added \"$title\" to your to-do list."
    }

    // ------------------------------------------------------------------ read --

    private suspend fun readNote(lower: String): String? {
        val m = NOTE_READ.find(lower) ?: return null
        val query = m.groupValues[1].trim().trim('"', '“', '”')
        if (query.isBlank()) return null
        val note = noteDao.observeAll().first().firstOrNull { titleMatches(it.title, query) || query in it.title.lowercase() }
            ?: return "I don't have a note matching \"$query\"."
        if (note.bodyVaultRef != null) return "\"${note.title}\" is vault-locked — open it in Notes to unlock."
        val body = withContext(Dispatchers.IO) {
            runCatching { File(note.path).takeIf { it.exists() }?.readText() }.getOrNull()
        } ?: return "Found \"${note.title}\" but its file is missing."
        return "📝 ${note.title}\n${body.trim().take(700)}"
    }

    private suspend fun readTools(lower: String): String? {
        val asking = READ_VERB.containsMatchIn(lower)

        // Next task ("what's next", "what should I do").
        if (Regex("\\bwhat('s| is)? next\\b|\\bwhat should i do\\b").containsMatchIn(lower)) {
            val next = todoDao.nextOpenTask() ?: return "Nothing queued — you're all caught up."
            val due = next.dueAt?.let { " (due ${DAY.format(Date(it))} ${CLOCK.format(Date(it))})" } ?: ""
            return "Next up: ${next.title}$due"
        }

        if (asking && TASK_NOUN.containsMatchIn(lower)) {
            val open = captureDao.observeTasks().first().filter { !it.done }
            return if (open.isEmpty()) {
                "Your to-do list is clear."
            } else {
                "You have ${open.size} open task(s):\n" + open.take(10).joinToString("\n") { "• ${it.title}" }
            }
        }

        if (asking && REMINDER_WORDS.any { it in lower }) {
            val now = System.currentTimeMillis()
            val pending = reminderDao.observeAll().first().filter { it.enabled && it.firedAt == null && it.at > now }
            return if (pending.isEmpty()) {
                "No upcoming reminders."
            } else {
                "Upcoming reminders:\n" + pending.take(10).joinToString("\n") { "• ${it.title} — ${DAY.format(Date(it.at))} ${CLOCK.format(Date(it.at))}" }
            }
        }

        if (asking && CALENDAR_WORDS.any { it in lower }) {
            val now = System.currentTimeMillis()
            val horizon = if ("today" in lower) endOfToday() else if ("tomorrow" in lower) endOfToday() + DAY_MS else Long.MAX_VALUE
            val events = calendarDao.observeUpcoming(now).first().filter { it.startsAt <= horizon }
            return if (events.isEmpty()) {
                "Nothing on your calendar in that window."
            } else {
                "Coming up:\n" + events.take(10).joinToString("\n") { "• ${it.title} — ${DAY.format(Date(it.startsAt))} ${CLOCK.format(Date(it.startsAt))}" }
            }
        }

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

        if ("subscription" in lower) {
            val subs = financeDao.observeSubscriptions().first()
            if (subs.isEmpty()) return "No active subscriptions on record."
            val monthly = subs.sumOf { if (it.cadence == "YEARLY") it.amountCents / 12 else it.amountCents }
            return "Active subscriptions (~${money(monthly)}/month):\n" +
                subs.take(12).joinToString("\n") { "• ${it.merchant} — ${money(it.amountCents)}/${if (it.cadence == "YEARLY") "yr" else "mo"}" }
        }

        if (SPEND_WORDS.any { it in lower }) {
            val spent = -financeDao.observeSpentSince(startOfMonth()).first()
            return "You've spent ${money(spent)} so far this month."
        }

        if (asking && ("book" in lower || "reading" in lower)) {
            val books = bookDao.observeAll().first()
            if (books.isEmpty()) return "Your bookshelf is empty."
            val reading = books.filter { it.status == "READING" }
            val read = books.count { it.status == "READ" }
            val want = books.count { it.status == "WANT" }
            val line = if (reading.isEmpty()) "" else "\nCurrently reading:\n" + reading.joinToString("\n") { "• ${it.title} — ${it.author}" }
            return "${books.size} book(s): $read read, ${reading.size} in progress, $want on the wish list.$line"
        }

        if (asking && ("note" in lower || "notes" in lower)) {
            val notes = noteDao.observeAll().first()
            return if (notes.isEmpty()) {
                "No notes yet."
            } else {
                "You have ${notes.size} note(s), newest first:\n" +
                    notes.take(10).joinToString("\n") { "• ${it.title}${if (it.bodyVaultRef != null) " 🔒" else ""}" }
            }
        }

        // Logger counters: "how many pizzas", "pizza count".
        if (COUNT_WORDS.any { it in lower }) {
            val forms = captureDao.observeForms().first()
            val counts = captureDao.observeEntryCounts().first().associate { it.formId to it.n }
            val hit = forms.firstOrNull { f ->
                f.name.lowercase().split(Regex("\\s+")).any { w -> w.length >= 3 && stemIn(lower, w) }
            }
            if (hit != null) return "${hit.name}: ${counts[hit.id] ?: 0} logged."
        }

        if (Regex("\\bwhat time is it\\b|\\bcurrent time\\b").containsMatchIn(lower)) {
            return "It's ${CLOCK.format(Date())} on ${FULL_DAY.format(Date())}."
        }

        if (Regex("\\bwhat can you do\\b|\\bhelp\\b$").containsMatchIn(lower)) {
            return "I can act on your modules directly:\n" +
                "• To-do: add / list / check off / delete tasks\n" +
                "• Reminders & timers: set, list, cancel — \"remind me at 6pm to…\", \"timer 10m\"\n" +
                "• Calendar: add events, show today/tomorrow/upcoming\n" +
                "• Notes: save, list, read (\"read note wifi\")\n" +
                "• Search everything: \"find <anything>\" across notes, memex, captures, tasks, books\n" +
                "• Status: packages, subscriptions, monthly spend, books, next task\n" +
                "Anything else goes to the AI."
        }

        return null
    }

    // ---------------------------------------------------------------- search --

    /**
     * Keyword search across every module with reachable text: plain note files,
     * Memex, captures, task/reminder/calendar titles and books. Terms are
     * matched with naive singular/plural stemming ("codes" finds "code").
     * Vault-encrypted notes are skipped (they need biometric unlock).
     */
    private suspend fun searchModules(lower: String): String? {
        val terms = lower.split(Regex("[^a-z0-9]+"))
            .filter { t -> (t.length >= 4 || (t.length >= 3 && t.all(Char::isDigit))) && t !in STOPWORDS }
        if (terms.isEmpty()) return null

        val hits = mutableListOf<String>()
        fun matched(text: String?) = text != null && terms.any { stemIn(text.lowercase(), it) }

        withContext(Dispatchers.IO) {
            noteDao.observeAll().first().forEach { note ->
                if (note.bodyVaultRef != null) return@forEach
                val body = runCatching { File(note.path).takeIf { it.exists() }?.readText() }
                    .getOrNull().orEmpty()
                val line = body.lineSequence().firstOrNull { l -> terms.any { stemIn(l.lowercase(), it) } }
                when {
                    line != null -> hits += "📝 ${note.title}: ${line.trim().take(160)}"
                    matched(note.title) -> hits += "📝 ${note.title}"
                }
            }
        }

        memexDao.observeAll().first().forEach { item ->
            if (matched(item.body) || matched(item.title)) {
                hits += "🗄 ${item.title}: ${item.body.trim().take(160)}"
            }
        }
        captureDao.observeRecent().first().forEach { cap ->
            if (matched(cap.text)) hits += "⚡ Capture: ${cap.text!!.trim().take(160)}"
        }
        captureDao.observeTasks().first().forEach { t ->
            if (matched(t.title)) hits += "☑ Task: ${t.title}${if (t.done) " (done)" else ""}"
        }
        reminderDao.observeAll().first().forEach { r ->
            if (matched(r.title)) hits += "⏰ Reminder: ${r.title} — ${DAY.format(Date(r.at))}"
        }
        calendarDao.observeUpcoming(System.currentTimeMillis()).first().forEach { e ->
            if (matched(e.title)) hits += "📅 ${e.title} — ${DAY.format(Date(e.startsAt))}"
        }
        bookDao.observeAll().first().forEach { b ->
            if (matched(b.title) || matched(b.author)) hits += "📚 ${b.title} — ${b.author}"
        }

        if (hits.isEmpty()) {
            return "I searched notes, memex, captures, tasks, reminders, calendar and books for " +
                terms.joinToString(", ") + " — nothing matched. (Vault-locked notes aren't searchable.)"
        }
        return "Found this:\n" + hits.take(6).joinToString("\n")
    }

    // --------------------------------------------------------------- helpers --

    /** Fuzzy title match: full containment either way, or most significant words present. */
    private fun titleMatches(title: String, inText: String): Boolean {
        val t = title.lowercase().trim()
        if (t.isBlank()) return false
        if (t in inText) return true
        val words = t.split(Regex("\\s+")).filter { it.length >= 3 }
        if (words.isEmpty()) return false
        val present = words.count { stemIn(inText, it) }
        return present * 2 >= words.size + 1 // strict majority
    }

    private fun matchTasksByTitle(tasks: List<TaskEntity>, lower: String) =
        tasks.filter { titleMatches(it.title, lower) }

    /** `term` appears in `text` allowing a trailing-s either way ("codes"→"code"). */
    private fun stemIn(text: String, term: String): Boolean {
        if (term in text) return true
        if (term.endsWith("s") && term.length > 3 && term.dropLast(1) in text) return true
        return "${term}s" in text
    }

    private fun remainingLine(left: Int) =
        if (left <= 0) "Your list is clear now." else "$left task(s) left."

    private fun extractTaskTitle(raw: String): String? {
        QUOTED.find(raw)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let {
            return it.trim().replaceFirstChar { c -> c.uppercase() }
        }
        // Drop the trailing "…to my <anything> list" destination — tolerant of
        // typos like "tdo to list" — then strip scaffolding verbs/fillers.
        val withoutDest = raw.replace(TRAILING_LIST, " ")
        val cleaned = cleanTitle(withoutDest, TASK_STRIP)
        return cleaned.ifBlank { null }
    }

    /** Strips scaffolding words/leading time so the saved title reads cleanly. */
    private fun cleanTitle(raw: String, strip: Regex): String =
        raw.replace(strip, " ")
            .replace(LEADING_TIME, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('"', '“', '”', ',', '.', '?', '!')
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

    private fun money(cents: Long): String = String.format(Locale.getDefault(), "%.2f €", cents / 100.0)

    private fun endOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
    }.timeInMillis

    private fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        val SOURCE = SourceRef(LifeModule.CHAT, "jarvis")
        val CLOCK = SimpleDateFormat("HH:mm", Locale.getDefault())
        val DAY = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        val FULL_DAY = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        const val DAY_MS = 86_400_000L

        val CREATE_VERB = Regex("(?i)\\b(add|create|new|remember|put|note down|jot|save|schedule|set|append)\\b")
        val READ_VERB = Regex(
            "(?i)\\b(list|show|what('| i|'re| a)?s?|whats|what (is|are)|which|how many|do i have|any|upcoming|see|view|tell me|open (task|to)|left|pending|remaining)\\b",
        )
        val COMPLETE_VERB = Regex("(?i)\\b(close|complete|finish|check off|tick( off)?|mark|done with)\\b")
        val DELETE_VERB = Regex("(?i)\\b(delete|remove|cancel|drop|clear|scrap|stop)\\b")
        val ACTION_VERB = Regex(
            "(?i)\\b(add|create|set|schedule|remind|close|complete|finish|delete|remove|cancel|mark|check off|save|track|append|log)\\b",
        )
        val MODULE_NOUN = Regex(
            "(?i)\\b(tasks?|to[\\s-]?dos?|t[o0]?d[o0]s?|list|reminders?|timers?|alarms?|events?|calendar|notes?|packages?|subscriptions?|books?)\\b",
        )

        val QUOTED = Regex("[\"“](.+?)[\"”]")
        // "to do", "to-do", "todo", "todos", plus fat-finger variants like "tdo".
        val TASK_NOUN = Regex("(?i)\\b(to[\\s-]?dos?|t[o0]?d[o0]s?|tasks?)\\b")
        val TASK_REFERENCE = Regex("(?i)\\b(that|them|it|everything|all of (them|it))\\b")
        val ALL_WORDS = Regex("(?i)\\b(all|everything|every one|them all)\\b")
        val TRAILING_LIST = Regex("(?i)\\s+(?:to|on|onto|in)\\s+(?:my\\s+)?[a-z\\s-]{0,24}?lists?\\s*$")
        val TASK_STRIP = Regex(
            "(?i)\\b(hey jarvis|jarvis|can you|could you|please|add|create|new|remember to|remember|put|jot|save|append|" +
                "called|named|titled|a|an|the|tasks?|to[\\s-]?dos?|t[o0]?d[o0]s?|item|lists?|my)\\b",
        )
        val REMINDER_STRIP = Regex("(?i)\\b(hey jarvis|jarvis|can you|could you|remind me to|remind me|reminder|set|a|please)\\b")
        val EVENT_STRIP = Regex("(?i)\\b(hey jarvis|jarvis|can you|could you|add|create|schedule|new|put|event|meeting|appointment|to (my )?calendar|please|a|an)\\b")
        val NOTE_STRIP = Regex("(?i)\\b(hey jarvis|jarvis|can you|could you|add|create|new|save|note down|note|a|please|that|saying)\\b")
        val NOTE_READ = Regex("(?i)\\b(?:read|open|show)\\s+(?:me\\s+)?(?:my\\s+)?(?:the\\s+)?note\\s+(?:called\\s+|named\\s+|about\\s+)?(.+)")
        val DURATION = Regex("(\\d+)\\s*(h(?:ou)?rs?|min(?:ute)?s?|m\\b|s(?:ec(?:ond)?s?)?|d(?:ays?)?)")
        val TIME = Regex("(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b")
        val LEADING_TIME = Regex("(?i)\\b(at\\s+)?\\d{1,2}(:\\d{2})?\\s*(am|pm)?\\b|\\b(tomorrow|tonight|today)\\b")
        val SEARCH_VERB = Regex("(?i)\\b(find|search|look for|where('| i)?s|where is|locate|secret|do you see|dig up|hidden)\\b")
        // Instruction/filler words only — the actual subject ("secret", "code", …)
        // must survive as a search term.
        val STOPWORDS = setOf(
            "find", "search", "where", "look", "locate", "have", "hidden",
            "somewhere", "modules", "module", "please", "jarvis", "there", "them", "that", "this",
            "with", "your", "mine", "from", "into", "about", "would", "could", "should", "hey",
            "again", "right", "just", "some", "thing", "things", "want",
        )
        val PACKAGE_WORDS = listOf("package", "parcel", "delivery", "shipment", "tracking")
        val REMINDER_WORDS = listOf("remind", "reminder")
        val EVENT_WORDS = listOf("meeting", "appointment", "event", "calendar")
        val CALENDAR_WORDS = listOf("calendar", "event", "schedule", "agenda", "today", "tomorrow")
        val SPEND_WORDS = listOf("spent", "spending", "expenses")
        val COUNT_WORDS = listOf("how many", "count", "how often", "how much have i")
    }
}
