package com.lifeos.feature.brick.data

import android.content.Context
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.database.brick.BrickAppLimitEntity
import com.lifeos.core.database.brick.BrickDao
import com.lifeos.core.database.brick.BrickProfileEntity
import com.lifeos.core.database.brick.BrickSessionEntity
import com.lifeos.core.database.brick.BrickUsageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brick's brain (§Module Brick). Owns the running session, decides whether a
 * package may be in the foreground, and tracks per-app daily usage so
 * allowances ("30 min of Instagram") work without Usage Access.
 *
 * The hot path ([decide]) reads an in-memory snapshot so the accessibility
 * service never touches disk on a window change.
 */
@Singleton
class BrickRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val brickDao: BrickDao,
    private val scheduler: BrickScheduler,
) {

    /** Snapshot of the live mode, kept in memory for instant blocking checks. */
    data class ActiveMode(
        val session: BrickSessionEntity,
        val profile: BrickProfileEntity,
        val blockedPackages: Set<String>,
        val limits: Map<String, Int>,
    ) {
        /** The pure view [BrickPolicy] reasons about. */
        val rules: ModeRules = ModeRules(
            profileName = profile.name,
            blockedPackages = blockedPackages,
            limits = limits,
            deactivator = profile.deactivator,
            strict = profile.strict,
            endMinuteOfDay = profile.endMinuteOfDay,
            inverse = profile.inverse,
            unlockMinutes = profile.unlockMinutes,
            unlockAllowance = profile.unlockAllowance,
            unlockUntil = session.unlockUntil,
            unlocksUsed = session.unlocksUsed,
        )
    }

    private val _active = MutableStateFlow<ActiveMode?>(null)
    val active = _active.asStateFlow()

    /** Seconds already spent today per package (in-memory mirror of brick_usage). */
    private val usageToday = mutableMapOf<String, Long>()
    private var usageDate: String = today()

    fun observeProfiles(): Flow<List<BrickProfileEntity>> = brickDao.observeProfiles()
    fun observeSessions(): Flow<List<BrickSessionEntity>> = brickDao.observeRecentSessions()

    /** Reloads the live session from disk (boot, process start, after edits). */
    suspend fun refresh() {
        val session = brickDao.activeSession()
        if (session == null) {
            _active.value = null
        } else {
            val profile = brickDao.profile(session.profileId)
            if (profile == null) {
                // Profile deleted while running — close the orphan session.
                brickDao.endAllSessions(System.currentTimeMillis())
                _active.value = null
            } else {
                _active.value = snapshot(session, profile)
            }
        }
        loadUsage()
        scheduler.rescheduleAll(brickDao.allProfiles())
    }

    private suspend fun snapshot(session: BrickSessionEntity, profile: BrickProfileEntity): ActiveMode {
        val limits = brickDao.limitsFor(profile.id).associate { it.packageName to it.dailyMinutes }
        return ActiveMode(
            session = session,
            profile = profile,
            blockedPackages = profile.blockedPackages.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            limits = limits,
        )
    }

    /** Mirrors today's stored usage for the live mode's limited apps. */
    private suspend fun loadUsage() {
        usageDate = today()
        usageToday.clear()
        val active = _active.value ?: return
        active.limits.keys.forEach { pkg ->
            brickDao.usage(usageDate, pkg)?.let { usageToday[pkg] = it.secondsUsed }
        }
    }

    // ---- session control ---------------------------------------------------

    suspend fun start(profileId: Long, startedBy: String): Boolean {
        val profile = brickDao.profile(profileId) ?: return false
        if (_active.value != null) return false
        val session = BrickSessionEntity(
            profileId = profileId,
            startedAt = System.currentTimeMillis(),
            startedBy = startedBy,
        )
        val id = brickDao.insertSession(session)
        _active.value = snapshot(session.copy(id = id), profile)
        loadUsage()
        LifeLogger.i(TAG, "Brick mode \"${profile.name}\" started by $startedBy")
        return true
    }

    /**
     * Ends the live mode. Returns false when [by] doesn't satisfy the profile's
     * deactivator — that's what makes strict modes actually strict.
     */
    suspend fun stop(by: String): Boolean {
        val active = _active.value ?: return true
        if (!BrickPolicy.canStop(active.rules, by)) return false
        brickDao.endAllSessions(System.currentTimeMillis())
        _active.value = null
        LifeLogger.i(TAG, "Brick mode ended by $by")
        return true
    }

    /** NFC tap: flips the mode bound to [rawTagId]. Returns a message for the UI. */
    suspend fun onTagScanned(rawTagId: String): String {
        // Ids are stored uppercase-hex; compare normalized so a tag paired in
        // one code path always matches a tap arriving through another.
        val tagId = rawTagId.trim().uppercase()
        LifeLogger.i(TAG, "Brick tag scanned: $tagId")
        val running = _active.value
        if (running != null) {
            val runningTag = running.profile.nfcTagId?.trim()?.uppercase()
            // Inverse mode: the tap buys access inside the window instead of
            // ending the mode.
            if (running.profile.inverse && runningTag == tagId) return grantUnlock(running)
            return when {
                runningTag == tagId ->
                    if (stop("NFC")) "\"${running.profile.name}\" unlocked" else "Wrong tag for this mode"
                // A different tag may belong to another mode, but only one mode
                // runs at a time — say so instead of silently doing nothing.
                else -> "\"${running.profile.name}\" is still running; end it with its own tag first"
            }
        }
        val profile = brickDao.profileByTag(tagId)
            ?: return "Tag $tagId is not paired with any mode yet — pair it in Brick"
        if (profile.inverse) {
            // Inverse modes only exist inside their window; outside it nothing
            // is blocked, so there is nothing to unlock.
            val inWindow = withinWindow(profile)
            if (!inWindow) {
                return "\"${profile.name}\" only blocks between " +
                    "${BrickPolicy.formatMinute(profile.startMinuteOfDay)} and " +
                    "${BrickPolicy.formatMinute(profile.endMinuteOfDay)} — nothing to unlock right now"
            }
            // The window is live but no session exists yet (phone was off at the
            // start minute): open it, then spend an unlock.
            if (!start(profile.id, "TIME")) return "Couldn't start the mode"
            val running = _active.value ?: return "Couldn't start the mode"
            return grantUnlock(running)
        }
        return if (start(profile.id, "NFC")) "\"${profile.name}\" is now blocking" else "Couldn't start the mode"
    }

    /** Spends one unlock of an inverse mode and reports what happened. */
    private suspend fun grantUnlock(running: ActiveMode): String {
        val name = running.profile.name
        return when (val decision = BrickPolicy.unlock(running.rules)) {
            is UnlockDecision.AlreadyOpen ->
                "\"$name\" is already open until ${clock(decision.until)}"

            is UnlockDecision.NoneLeft ->
                "No unlocks left for \"$name\" — it opens again at " +
                    BrickPolicy.formatMinute(running.profile.endMinuteOfDay)

            is UnlockDecision.Granted -> {
                brickDao.setUnlock(running.session.id, decision.until, decision.used)
                val session = running.session.copy(unlockUntil = decision.until, unlocksUsed = decision.used)
                _active.value = snapshot(session, running.profile)
                val quota = if (decision.allowance <= 0) {
                    "unlimited unlocks"
                } else {
                    "${decision.used} of ${decision.allowance} used"
                }
                "\"$name\" open for ${running.profile.unlockMinutes} min, " +
                    "until ${clock(decision.until)} ($quota)"
            }
        }
    }

    /** True when [profile]'s schedule window covers the current minute. */
    private fun withinWindow(profile: BrickProfileEntity): Boolean {
        val start = profile.startMinuteOfDay ?: return true
        val end = profile.endMinuteOfDay ?: return true
        val now = minuteOfDayNow()
        // Windows that wrap past midnight (22:00 - 06:00) count both sides.
        return if (start <= end) now in start until end else now >= start || now < end
    }

    /** Remaining unlock time in millis, or null when nothing is open. */
    fun unlockRemaining(): Long? {
        val rules = _active.value?.rules ?: return null
        if (!BrickPolicy.unlockActive(rules)) return null
        return (rules.unlockUntil ?: 0L) - System.currentTimeMillis()
    }

    // ---- the hot path ------------------------------------------------------

    /** Should [packageName] be allowed in the foreground right now? */
    fun decide(packageName: String): BlockDecision {
        val active = _active.value
        return BrickPolicy.decide(
            rules = active?.rules,
            packageName = packageName,
            ownPackage = context.packageName,
            usedSecondsToday = usageToday[packageName] ?: 0L,
        )
    }

    /** Credits [seconds] of foreground time to a limited app. */
    suspend fun addUsage(packageName: String, seconds: Long) {
        if (seconds <= 0) return
        if (usageDate != today()) {
            usageDate = today()
            usageToday.clear()
        }
        val total = (usageToday[packageName] ?: 0L) + seconds
        usageToday[packageName] = total
        brickDao.upsertUsage(BrickUsageEntity(usageDate, packageName, total))
    }

    suspend fun noteBlockedAttempt() {
        _active.value?.let { brickDao.incrementAttempts(it.session.id) }
    }

    // ---- profile CRUD ------------------------------------------------------

    suspend fun saveProfile(profile: BrickProfileEntity, limits: Map<String, Int>): Long {
        val id = if (profile.id == 0L) {
            brickDao.insertProfile(profile)
        } else {
            brickDao.updateProfile(profile)
            profile.id
        }
        // Replace the limit set wholesale — simplest correct behaviour on edit.
        brickDao.limitsFor(id).forEach { brickDao.deleteLimit(id, it.packageName) }
        limits.forEach { (pkg, minutes) -> brickDao.upsertLimit(BrickAppLimitEntity(id, pkg, minutes)) }
        scheduler.rescheduleAll(brickDao.allProfiles())
        // Keep a running session's snapshot in sync with the edit.
        _active.value?.let { current ->
            if (current.profile.id == id) {
                brickDao.profile(id)?.let { _active.value = snapshot(current.session, it) }
            }
        }
        return id
    }

    suspend fun limitsFor(profileId: Long): Map<String, Int> =
        brickDao.limitsFor(profileId).associate { it.packageName to it.dailyMinutes }

    suspend fun deleteProfile(id: Long) {
        if (_active.value?.profile?.id == id) stop(FORCE)
        brickDao.deleteProfile(id)
        scheduler.rescheduleAll(brickDao.allProfiles())
    }

    suspend fun profile(id: Long): BrickProfileEntity? = brickDao.profile(id)

    /** Time-window tick from [BrickScheduleReceiver]: start or end as due. */
    suspend fun onScheduleTick(profileId: Long, starting: Boolean) {
        val profile = brickDao.profile(profileId) ?: return
        if (starting) {
            if (_active.value == null && (profile.activator == "TIME" || profile.inverse)) {
                start(profileId, "TIME")
            }
        } else {
            val running = _active.value
            if (running?.profile?.id == profileId) stop("TIME")
        }
        scheduler.rescheduleAll(brickDao.allProfiles())
    }

    private fun today(): String = DATE.format(Date())

    private fun clock(millis: Long): String = CLOCK.format(Date(millis))

    companion object {
        /** Internal override used when a profile disappears; bypasses strict rules. */
        const val FORCE = BrickPolicy.FORCE
        private const val TAG = "BrickRepository"
        private val DATE = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val CLOCK = SimpleDateFormat("HH:mm", Locale.US)

        fun minuteOfDayNow(): Int = Calendar.getInstance().let {
            it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
        }
    }
}
