package com.lifeos.feature.triggers.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.triggers.TriggerDao
import com.lifeos.core.database.triggers.TriggerFireEntity
import com.lifeos.core.database.triggers.TriggerRuleEntity
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.places.PlaceEngine
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
import com.lifeos.core.service.LifeEvent
import com.lifeos.core.service.LifeEventBus
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The automation engine (§Module Triggers).
 *
 * Rules are evaluated from three sources: the event bus (places, notifications,
 * reminders, NFC), a per-rule alarm for time rules, and a poll for the state-ish
 * triggers (screen time, battery) that have no event. Every fire is written to
 * the audit table, successful or not, so "why did that happen?" always has an
 * answer.
 *
 * Actions go through [LifeActionDispatcher], which means a rule can do anything
 * Jarvis can do, and nothing a module has not explicitly exposed.
 */
@Singleton
class TriggerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val triggerDao: TriggerDao,
    /** Lazily: rule actions are dispatched, and one handler is the rules module. */
    private val dispatcherProvider: Provider<LifeActionDispatcher>,
    private val eventBus: LifeEventBus,
    private val placeEngine: PlaceEngine,
) {

    private val dispatcher: LifeActionDispatcher get() = dispatcherProvider.get()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private var started = false

    /** Starts listening; safe to call more than once. */
    fun start() {
        if (started) return
        started = true
        placeEngine.start()
        scope.launch {
            eventBus.events.collect { event -> onEvent(event) }
        }
        scope.launch { rearmAll() }
    }

    private suspend fun onEvent(event: LifeEvent) {
        when (event) {
            is LifeEvent.PlaceEntered -> fireMatching("PLACE_ENTER") { rule ->
                rule.triggerArg.isBlank() || rule.triggerArg == event.placeId.toString() ||
                    rule.triggerArg.equals(event.name, ignoreCase = true)
            }

            is LifeEvent.PlaceLeft -> fireMatching("PLACE_LEAVE") { rule ->
                rule.triggerArg.isBlank() || rule.triggerArg == event.placeId.toString() ||
                    rule.triggerArg.equals(event.name, ignoreCase = true)
            }

            is LifeEvent.SignalCaptured -> fireMatching("SIGNAL") { rule ->
                val needle = rule.triggerArg.trim()
                needle.isBlank() ||
                    event.appLabel.contains(needle, ignoreCase = true) ||
                    event.appPackage.contains(needle, ignoreCase = true) ||
                    event.title.contains(needle, ignoreCase = true) ||
                    event.text.contains(needle, ignoreCase = true)
            }

            is LifeEvent.ReminderFired -> fireMatching("REMINDER_FIRED") { rule ->
                rule.triggerArg.isBlank() || event.title.contains(rule.triggerArg, ignoreCase = true)
            }

            is LifeEvent.ScreenTimeCrossed -> fireMatching("SCREEN_TIME") { rule ->
                val threshold = rule.triggerArg.filter { it.isDigit() }.toIntOrNull() ?: return@fireMatching false
                event.minutesToday >= threshold
            }

            else -> Unit
        }
    }

    /** Called by the NFC path so a tag can drive rules as well as Brick. */
    fun onTagScanned(tagId: String) {
        scope.launch {
            fireMatching("NFC_TAG") { rule ->
                rule.triggerArg.isBlank() || rule.triggerArg.trim().equals(tagId.trim(), ignoreCase = true)
            }
        }
    }

    /** Wi-Fi and battery have no event of their own; the poller drives them. */
    suspend fun poll() {
        val ssid = placeEngine.currentSsid()
        if (ssid != null) {
            fireMatching("WIFI") { rule -> rule.triggerArg.trim().equals(ssid, ignoreCase = true) }
        }
        val battery = batteryPercent()
        if (battery != null) {
            fireMatching("BATTERY_BELOW") { rule ->
                val threshold = rule.triggerArg.filter { it.isDigit() }.toIntOrNull() ?: return@fireMatching false
                battery <= threshold
            }
        }
    }

    private suspend fun fireMatching(type: String, matches: (TriggerRuleEntity) -> Boolean) {
        val rules = triggerDao.enabledRules().filter { it.triggerType == type && matches(it) }
        rules.forEach { rule ->
            if (!withinSchedule(rule)) return@forEach
            // A rule that already fired inside the cooldown is skipped, which is
            // what stops a chatty notification app from running a macro 40 times.
            val lastFired = rule.lastFiredAt
            if (lastFired != null && System.currentTimeMillis() - lastFired < COOLDOWN_MS) {
                return@forEach
            }
            run(rule, byHand = false)
        }
    }

    /** Runs a rule now. Used by the alarm, the event paths and the Run button. */
    suspend fun run(rule: TriggerRuleEntity, byHand: Boolean): String {
        val action = actionFor(rule)
        if (action == null) {
            record(rule, "SKIPPED", "action \"${rule.actionType}\" needs a value")
            return "\"${rule.name}\" needs a value for its action"
        }
        val result = dispatcher.dispatch(action)
        val outcome = when (result) {
            is LifeResult.Success -> if (byHand) "RAN_BY_HAND" else "FIRED"
            is LifeResult.Failure -> "FAILED"
        }
        val detail = (result as? LifeResult.Failure)?.error?.message.orEmpty()
        record(rule, outcome, detail)
        triggerDao.updateRule(
            rule.copy(lastFiredAt = System.currentTimeMillis(), fireCount = rule.fireCount + 1),
        )
        LifeLogger.i(TAG, "Rule \"${rule.name}\" -> $outcome $detail")
        return if (detail.isBlank()) "\"${rule.name}\" ran" else "\"${rule.name}\" failed: $detail"
    }

    private suspend fun record(rule: TriggerRuleEntity, outcome: String, detail: String) {
        triggerDao.insertFire(
            TriggerFireEntity(
                ruleId = rule.id,
                ruleName = rule.name,
                at = System.currentTimeMillis(),
                outcome = outcome,
                detail = detail.take(140),
            ),
        )
        triggerDao.trimFires(System.currentTimeMillis() - 30L * 86_400_000L)
    }

    /** Maps a stored rule onto the cross-module action contract. */
    private fun actionFor(rule: TriggerRuleEntity): LifeAction? {
        val arg = rule.actionArg.trim()
        val minutes = arg.filter { it.isDigit() }.toIntOrNull()
        return when (rule.actionType) {
            "TASK" -> arg.ifBlank { null }?.let { LifeAction.CreateTask(it.take(100), SOURCE) }
            "NOTE" -> arg.ifBlank { null }?.let {
                val title = it.substringBefore('|').trim()
                val body = it.substringAfter('|', "").trim()
                LifeAction.CreateNote(title.take(60), body.ifBlank { title }, SOURCE)
            }
            "REMINDER" -> arg.ifBlank { null }?.let {
                LifeAction.CreateReminder(it.take(80), System.currentTimeMillis() + 600_000L, SOURCE)
            }
            "TIMER" -> minutes?.let {
                LifeAction.CreateReminder("Timer", System.currentTimeMillis() + it * 60_000L, SOURCE)
            }
            "FOCUS" -> LifeAction.StartFocusTimer(minutes ?: 25, SOURCE)
            "BRICK_ON" -> arg.ifBlank { null }?.let { LifeAction.StartBrickMode(it, SOURCE) }
            "BRICK_OFF" -> LifeAction.StopBrickMode(SOURCE)
            "MACRO" -> arg.ifBlank { null }?.let { LifeAction.RunMacro(it, SOURCE) }
            "PASTE" -> arg.ifBlank { null }?.let {
                val title = it.substringBefore('|').trim()
                val body = it.substringAfter('|', "").trim()
                LifeAction.CreatePaste(title.take(60), body.ifBlank { title }, burner = false, password = "", source = SOURCE)
            }
            "DOWNLOAD" -> arg.ifBlank { null }?.let { LifeAction.StartDownload(it, SOURCE) }
            "SCREEN_TIME_EXPORT" -> LifeAction.ExportScreenTime(arg.ifBlank { "json" }, weekOnly = true, source = SOURCE)
            "SYNC_SCREEN_TIME" -> LifeAction.SyncScreenTime(SOURCE)
            "WATER_PLANT" -> arg.ifBlank { null }?.let { LifeAction.WaterPlant(it, SOURCE) }
            else -> null
        }
    }

    /** Day-of-week list and optional window, both empty by default. */
    private fun withinSchedule(rule: TriggerRuleEntity): Boolean {
        val calendar = Calendar.getInstance()
        if (rule.days.isNotBlank()) {
            // Calendar is Sunday-based; the rule stores 1=Monday.
            val today = ((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
            val allowed = rule.days.split(',').mapNotNull { it.trim().toIntOrNull() }
            if (allowed.isNotEmpty() && today !in allowed) return false
        }
        if (rule.window.isNotBlank()) {
            val parts = rule.window.split('-').mapNotNull { it.trim().toIntOrNull() }
            if (parts.size == 2) {
                val now = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
                val (from, to) = parts
                val inside = if (from <= to) now in from..to else now >= from || now <= to
                if (!inside) return false
            }
        }
        return true
    }

    private fun batteryPercent(): Int? = runCatching {
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
    }.getOrNull()

    // ---- time rules --------------------------------------------------------

    suspend fun rearmAll() {
        val rules = triggerDao.allRules()
        rules.forEach { rule ->
            alarmManager.cancel(pendingIntent(rule.id))
            if (!rule.enabled || rule.triggerType != "TIME") return@forEach
            val minute = rule.triggerArg.filter { it.isDigit() }.toIntOrNull() ?: return@forEach
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextOccurrence(minute),
                    pendingIntent(rule.id),
                )
            }.onFailure { LifeLogger.w(TAG, "Could not arm rule ${rule.id}", it) }
        }
    }

    suspend fun onAlarm(ruleId: Long) {
        val rule = triggerDao.rule(ruleId) ?: return
        if (rule.enabled && withinSchedule(rule)) run(rule, byHand = false)
        rearmAll()
    }

    private fun pendingIntent(ruleId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        (ruleId.toInt() * 31) + 7,
        Intent(context, TriggerAlarmReceiver::class.java)
            .setAction(TriggerAlarmReceiver.ACTION_FIRE)
            .putExtra(TriggerAlarmReceiver.EXTRA_RULE_ID, ruleId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun nextOccurrence(minuteOfDay: Int): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    private companion object {
        const val TAG = "TriggerEngine"
        /** Same rule cannot fire twice inside this window. */
        const val COOLDOWN_MS = 60_000L
        val SOURCE = SourceRef(LifeModule.AGENTIC, "trigger")
    }
}

/** Fires one time rule and rearms it. */
@AndroidEntryPoint
class TriggerAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var engine: TriggerEngine

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1L)
        if (ruleId == -1L) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                engine.onAlarm(ruleId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.lifeos.triggers.FIRE"
        const val EXTRA_RULE_ID = "rule_id"
    }
}
