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
import com.lifeos.core.common.storage.LifeOsPublicMirror
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.service.ActionEcho
import com.lifeos.core.service.LifeDataProvider
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
    private val publicMirror: LifeOsPublicMirror,
    /**
     * Every module's own read-side, registered from the feature modules. Kept
     * out of the always-on prompt on purpose: detail is fetched only when the
     * model asks for it, so a chat that needs nothing costs nothing.
     */
    private val providers: Set<@JvmSuppressWildcards LifeDataProvider>,
    private val echo: ActionEcho,
) {

    /**
     * The tool contract the model sees. Kept terse — every char costs latency,
     * and the topic list is generated from whatever modules are installed.
     */
    val toolSpec: String
        get() = """
        To CHANGE LifeOS data, emit commands on their own line, exactly:
        [[add_task: title]] [[done_task: id]] [[delete_task: id]]
        [[timer: 5m]] [[remind: 18:00 | title]] [[remind: +25m | title]] [[cancel_reminder: id]]
        [[event: tomorrow 15:00 | title]] [[note: title | body]]
        [[edit_note: title | new full body]] [[append_note: title | text to add]]
        [[paste: title | text]] [[burner_paste: title | text]] (burner = one-time, encrypted)
        [[download: url]] [[water_plant: name]] [[add_plant: name | species | every N days]]
        [[brick_on: mode name]] [[brick_off:]] [[focus: 25m]] [[focus_stop:]]
        [[sync_screen_time:]] [[export_screen_time: json|csv_days|csv_apps]]
        [[run_macro: name]]
        [[rule: name | TRIGGER arg | ACTION arg]] [[rule_on: name]] [[rule_off: name]] [[run_rule: name]]
        [[backup:]] [[reindex:]] [[plan_day: today|tomorrow]] [[say: text to speak]]
        To READ a module in detail, emit ONE line and stop; the answer comes back to you:
        [[get: topic]] or [[get: topic | query]] — topics: ${topicList()}
        Rules: use [[get:]] only when the answer needs data that is not already in LIVE DATA
        below. For anything else (questions, math, writing, chat) just answer in plain words.
        Never say you will look something up: either emit [[get:]] or answer. Use ids from
        LIVE DATA. Never claim an action you did not emit.
        """.trimIndent()

    private fun topicList(): String =
        (BUILT_IN_TOPICS + providers.map { it.topic }).distinct().sorted().joinToString(", ")

    /** True when the reply asks for data, meaning the turn needs a second pass. */
    fun requestedReads(modelText: String): List<Pair<String, String?>> =
        TOOL_TAG.findAll(repair(modelText))
            .filter { it.groupValues[1].trim().lowercase() == "get" }
            .map { match ->
                val (topic, query) = splitArgs(match.groupValues[2])
                topic.trim().lowercase().replace(' ', '_') to query.takeIf { it.isNotBlank() }
            }
            .toList()

    /** Runs the requested reads and returns a block to feed back to the model. */
    suspend fun fetchReads(reads: List<Pair<String, String?>>, debug: JarvisDebug? = null): String {
        val blocks = mutableListOf<String>()
        reads.take(MAX_READS_PER_TURN).forEach { (topic, query) ->
            val body = runCatching { readTopic(topic, query) }
                .getOrElse { "$topic: could not be read (${it.message})" }
            debug?.add("read", "$topic(${query ?: ""}) -> ${body.take(200)}")
            blocks += body.take(1200)
        }
        return blocks.joinToString("\n\n")
    }

    private suspend fun readTopic(topic: String, query: String?): String {
        providers.firstOrNull { it.topic == topic }?.let { return it.read(query) }
        return when (topic) {
            "tasks" -> captureDao.observeTasks().first().filter { !it.done }
                .joinToString("\n") { "- [${it.id}] ${it.title}" }
                .ifBlank { "No open tasks." }

            "notes" -> noteDao.observeAll().first()
                .joinToString("\n") { "- ${it.title}" }
                .ifBlank { "No notes." }

            "calendar" -> calendarDao.observeUpcoming(System.currentTimeMillis()).first()
                .take(15)
                .joinToString("\n") { "- ${AT.format(Date(it.startsAt))} ${it.title}" }
                .ifBlank { "Calendar is empty." }

            "reminders" -> reminderDao.observeAll().first().filter { it.enabled && it.firedAt == null }
                .joinToString("\n") { "- [${it.id}] ${AT.format(Date(it.at))} ${it.title}" }
                .ifBlank { "No reminders." }

            "packages" -> packageDao.activePackages()
                .joinToString("\n") { "- ${it.label ?: it.trackingNumber}: ${it.statusDescription ?: it.status}" }
                .ifBlank { "No tracked parcels." }

            "finance" -> financeDao.observeSubscriptions().first()
                .joinToString("\n") { "- ${it.merchant} ${it.amountCents / 100.0}€ ${it.cadence}" }
                .ifBlank { "No subscriptions." }

            "books" -> bookDao.observeAll().first()
                .joinToString("\n") { "- ${it.title} — ${it.author} (${it.status})" }
                .ifBlank { "No books shelved." }

            "search" -> search(query.orEmpty())

            else -> "Unknown topic \"$topic\". Known: ${topicList()}"
        }
    }

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

        // Note titles AND short body snippets so questions ("what's my cat's
        // name?") are answerable straight from the data — no search round-trip.
        val notes = noteDao.observeAll().first()
        if (notes.isNotEmpty()) {
            appendLine("Notes:")
            withContext(Dispatchers.IO) {
                notes.take(8).forEach { note ->
                    if (note.bodyVaultRef != null) {
                        appendLine("- ${note.title.take(40)} (locked)")
                    } else {
                        val body = runCatching { File(note.path).takeIf { it.exists() }?.readText() }
                            .getOrNull().orEmpty().replace('\n', ' ').trim().take(160)
                        appendLine("- ${note.title.take(40)}: $body")
                    }
                }
            }
        }

        val captures = captureDao.observeRecent().first().filter { !it.text.isNullOrBlank() }
        if (captures.isNotEmpty()) {
            appendLine("Captures: " + captures.take(6).joinToString("; ") { it.text!!.replace('\n', ' ').take(80) })
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
    }.trim().take(1400)

    /**
     * Finds `[[tool: args]]` tags in the model's reply, executes each, and
     * returns the cleaned text plus one confirmation line per executed tool —
     * the confirmations come from the app, never from the model.
     */
    suspend fun runActions(modelText: String, debug: JarvisDebug? = null): String {
        val results = mutableListOf<String>()
        // Small models often drop one closing bracket ("…body.]"). Repair
        // lines that open a tool tag but only close with a single ']'.
        val repaired = repair(modelText)
        TOOL_TAG.findAll(repaired).forEach { match ->
            val tool = match.groupValues[1].trim().lowercase()
            val args = match.groupValues[2].trim()
            runCatching { execute(tool, args) }
                .onSuccess { it?.let { r -> results.add(r); debug?.add("tool", "$tool($args) -> $r") } }
                .onFailure {
                    results += "- ${tool.replace('_', ' ')} failed: ${it.message}"
                    debug?.add("tool-error", "$tool($args): ${it.message}")
                }
        }
        var cleaned = repaired.replace(TOOL_TAG, "")
        // Small models sometimes narrate intent ("I'll search the notes for…")
        // instead of answering. Drop those stray meta-lines so the reply reads clean.
        cleaned = cleaned.lineSequence()
            .filterNot { NARRATION.containsMatchIn(it) }
            .joinToString("\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        return when {
            results.isEmpty() -> cleaned
            cleaned.isBlank() -> results.joinToString("\n")
            else -> cleaned + "\n\n" + results.joinToString("\n")
        }
    }

    /** Small models often drop one closing bracket ("…body.]"). */
    private fun repair(modelText: String): String = modelText.lineSequence().joinToString("\n") { line ->
        val t = line.trimEnd()
        if (t.startsWith("[[") && !t.endsWith("]]") && t.endsWith("]")) "$t]" else line
    }

    private suspend fun execute(tool: String, args: String): String? = when (tool) {
        "add_task" -> {
            val title = args.trim().trim('"').take(80)
            if (title.isBlank()) null else {
                dispatcher.dispatch(LifeAction.CreateTask(title, SOURCE))
                "Added task: $title"
            }
        }
        "done_task" -> withTask(args) { id, title ->
            captureDao.setTaskDone(id, true); "Checked off: $title"
        }
        "delete_task" -> withTask(args) { id, title ->
            todoDao.deleteWithChildren(id); "Deleted task: $title"
        }
        "timer" -> {
            val ms = parseDuration(args) ?: error("bad duration \"$args\"")
            dispatcher.dispatch(LifeAction.CreateReminder("Timer", System.currentTimeMillis() + ms, SOURCE))
            "Timer set — rings in ${human(ms)}"
        }
        "remind" -> {
            val (whenPart, title) = splitArgs(args)
            val at = parseWhen(whenPart) ?: error("bad time \"$whenPart\"")
            dispatcher.dispatch(LifeAction.CreateReminder(title.ifBlank { "Reminder" }, at, SOURCE))
            "Reminder ${AT.format(Date(at))}: ${title.ifBlank { "Reminder" }}"
        }
        "cancel_reminder" -> {
            val id = args.trim().toLongOrNull() ?: error("bad id")
            val r = reminderDao.getById(id) ?: error("no reminder $id")
            reminderDao.setEnabled(id, false)
            "Cancelled reminder: ${r.title}"
        }
        "event" -> {
            val (whenPart, title) = splitArgs(args)
            val at = parseWhen(whenPart) ?: error("bad time \"$whenPart\"")
            dispatcher.dispatch(LifeAction.CreateCalendarEvent(title.ifBlank { "Event" }, at, at + 3_600_000L, SOURCE))
            "Event ${AT.format(Date(at))}: $title"
        }
        "note" -> {
            val (title, body) = splitArgs(args)
            dispatcher.dispatch(LifeAction.CreateNote(title.take(48), body.ifBlank { title }, SOURCE))
            "Note saved: ${title.take(48)}"
        }
        "edit_note" -> rewriteNote(args, append = false)
        "append_note" -> rewriteNote(args, append = true)
        "search" -> search(args)

        // ---- module actions (dispatched, so chat never imports a feature) ----
        "paste", "burner_paste" -> {
            val (title, body) = splitArgs(args)
            val content = body.ifBlank { title }
            if (content.isBlank()) error("nothing to paste")
            dispatch(
                LifeAction.CreatePaste(
                    title = if (body.isBlank()) "From Jarvis" else title,
                    content = content,
                    burner = tool == "burner_paste",
                    password = "",
                    source = SOURCE,
                ),
            )
            val url = echo.lastUrl
            if (tool == "burner_paste") {
                "One-time encrypted paste: ${url ?: "created"}"
            } else {
                "Paste created: ${url ?: "done"}"
            }
        }

        "download" -> {
            dispatch(LifeAction.StartDownload(args.trim(), SOURCE))
            "Download queued from ${args.trim().take(60)}"
        }

        "water_plant" -> {
            dispatch(LifeAction.WaterPlant(args.trim(), SOURCE))
            "Watered ${args.trim()}"
        }

        "add_plant" -> {
            val parts = args.split('|').map { it.trim() }
            val name = parts.firstOrNull().orEmpty()
            if (name.isBlank()) error("need a plant name")
            val days = parts.getOrNull(2)?.filter { it.isDigit() }?.toIntOrNull() ?: 7
            dispatch(
                LifeAction.AddPlant(
                    plantName = name,
                    species = parts.getOrNull(1).orEmpty(),
                    waterEveryDays = days,
                    source = SOURCE,
                ),
            )
            "Added plant $name, watering every ${days}d"
        }

        "brick_on" -> {
            dispatch(LifeAction.StartBrickMode(args.trim(), SOURCE))
            "Brick mode \"${args.trim()}\" is blocking"
        }

        "brick_off" -> {
            dispatch(LifeAction.StopBrickMode(SOURCE))
            "Brick mode ended"
        }

        "focus" -> {
            val minutes = (parseDuration(args) ?: error("bad duration \"$args\"")) / 60_000L
            dispatch(LifeAction.StartFocusTimer(minutes.toInt().coerceAtLeast(1), SOURCE))
            "Focus timer started for ${minutes.toInt()} min"
        }

        "focus_stop" -> {
            dispatch(LifeAction.StopFocusTimer(SOURCE))
            "Focus timer stopped"
        }

        "sync_screen_time" -> {
            dispatch(LifeAction.SyncScreenTime(SOURCE))
            "Screen time synced"
        }

        "export_screen_time" -> {
            val weekOnly = args.contains("week", ignoreCase = true)
            dispatch(LifeAction.ExportScreenTime(args.ifBlank { "json" }, weekOnly, SOURCE))
            "Screen-time export saved to Downloads: ${echo.lastFileName ?: "done"}"
        }

        "run_macro" -> {
            dispatch(LifeAction.RunMacro(args.trim(), SOURCE))
            "Ran macro \"${args.trim()}\""
        }

        "rule" -> {
            // "name | TRIGGER arg | ACTION arg" - the engine validates the types.
            val parts = args.split('|').map { it.trim() }
            if (parts.size < 3) error("need: name | TRIGGER arg | ACTION arg")
            val trigger = parts[1].split(' ', limit = 2)
            val actionSpec = parts[2].split(' ', limit = 2)
            dispatch(
                LifeAction.CreateTriggerRule(
                    name = parts[0],
                    triggerType = trigger.first(),
                    triggerArg = trigger.getOrNull(1).orEmpty(),
                    actionType = actionSpec.first(),
                    actionArg = actionSpec.getOrNull(1).orEmpty(),
                    days = parts.getOrNull(3).orEmpty(),
                    source = SOURCE,
                ),
            )
            "Rule \"${parts[0]}\" created"
        }

        "rule_on", "rule_off" -> {
            dispatch(LifeAction.SetTriggerRuleEnabled(args.trim(), tool == "rule_on", SOURCE))
            if (tool == "rule_on") "Rule \"${args.trim()}\" is on" else "Rule \"${args.trim()}\" is off"
        }

        "run_rule" -> {
            dispatch(LifeAction.RunTriggerRule(args.trim(), SOURCE))
            "Ran rule \"${args.trim()}\""
        }

        "backup" -> {
            dispatch(LifeAction.BackupNow(SOURCE))
            "Backup written and verified: ${echo.lastFileName ?: "done"}"
        }

        "reindex" -> {
            dispatch(LifeAction.ReindexRecall(SOURCE))
            "Recall index refreshed"
        }

        "plan_day" -> {
            val offset = if (args.contains("tomorrow", ignoreCase = true)) 1 else 0
            dispatch(LifeAction.PlanDay(offset, SOURCE))
            if (offset == 0) "Today is planned" else "Tomorrow is planned"
        }

        "say" -> {
            dispatch(LifeAction.Speak(args.trim(), SOURCE))
            null
        }

        // Reads are handled before this point (they feed a second pass).
        "get" -> null
        else -> null
    }

    /** Dispatches and turns a failure into an error the caller reports. */
    private suspend fun dispatch(action: LifeAction) {
        when (val result = dispatcher.dispatch(action)) {
            is LifeResult.Failure -> error(result.error.message)
            is LifeResult.Success -> Unit
        }
    }

    /** Rewrites (or appends to) a plain note's file by fuzzy title match. */
    private suspend fun rewriteNote(args: String, append: Boolean): String {
        val (titleQuery, newBody) = splitArgs(args)
        if (titleQuery.isBlank() || newBody.isBlank()) error("need: title | body")
        val notes = noteDao.observeAll().first()
        val q = titleQuery.lowercase()
        val note = notes.firstOrNull { it.title.lowercase() == q }
            ?: notes.firstOrNull { q in it.title.lowercase() || it.title.lowercase() in q }
            ?: error("no note titled \"$titleQuery\"")
        if (note.bodyVaultRef != null) error("\"${note.title}\" is vault-locked")
        return withContext(Dispatchers.IO) {
            val file = File(note.path)
            val content = if (append) {
                (file.takeIf { it.exists() }?.readText().orEmpty().trimEnd() + "\n" + newBody)
            } else {
                newBody
            }
            file.writeText(content)
            publicMirror.writeText("Notes", file.name, content)
            noteDao.update(note.copy(updatedAt = System.currentTimeMillis()))
            if (append) "Added to note \"${note.title}\"" else "Note \"${note.title}\" updated"
        }
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
                if (line != null) hits += "Note ${note.title}: ${line.trim().take(140)}"
                else if (hit(note.title)) hits += "Note ${note.title}"
            }
        }
        memexDao.observeAll().first().forEach { if (hit(it.body) || hit(it.title)) hits += "Memex ${it.title}: ${it.body.trim().take(140)}" }
        captureDao.observeRecent().first().forEach { if (hit(it.text)) hits += "Capture ${it.text!!.trim().take(140)}" }
        captureDao.observeTasks().first().forEach { if (hit(it.title)) hits += "Task ${it.title}" }
        bookDao.observeAll().first().forEach { if (hit(it.title) || hit(it.author)) hits += "Book ${it.title} — ${it.author}" }

        return if (hits.isEmpty()) {
            "Searched notes, memex, captures, tasks and books — no match for \"$query\"."
        } else {
            "Found:\n" + hits.take(5).joinToString("\n")
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
        val NARRATION = Regex(
            "(?i)^\\s*(i('| a)?ll?|i will|let me|i'm going to|i am going to)\\s+(search|look|check|find|scan)\\b",
        )
        val AT = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        val STAMP = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())
        val SEARCH_STOP = setOf("the", "and", "for", "with", "search", "find", "look", "please")
        /** Topics served straight from core DAOs, without a feature provider. */
        val BUILT_IN_TOPICS = listOf(
            "tasks", "notes", "calendar", "reminders", "packages", "finance", "books", "search",
        )
        const val MAX_READS_PER_TURN = 2
    }
}
