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

    /** NFC tap: flips the mode bound to [tagId]. Returns a message for the UI. */
    suspend fun onTagScanned(tagId: String): String {
        val running = _active.value
        if (running != null) {
            return if (running.profile.nfcTagId == tagId) {
                if (stop("NFC")) "\"${running.profile.name}\" unlocked" else "Wrong tag for this mode"
            } else {
                "\"${running.profile.name}\" stays locked — that's a different tag"
            }
        }
        val profile = brickDao.profileByTag(tagId)
            ?: return "Unknown tag. Pair it with a mode in Brick first."
        return if (start(profile.id, "NFC")) "\"${profile.name}\" is now blocking" else "Couldn't start the mode"
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
            if (_active.value == null && profile.activator == "TIME") start(profileId, "TIME")
        } else {
            val running = _active.value
            if (running?.profile?.id == profileId) stop("TIME")
        }
        scheduler.rescheduleAll(brickDao.allProfiles())
    }

    private fun today(): String = DATE.format(Date())

    companion object {
        /** Internal override used when a profile disappears; bypasses strict rules. */
        const val FORCE = BrickPolicy.FORCE
        private const val TAG = "BrickRepository"
        private val DATE = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun minuteOfDayNow(): Int = Calendar.getInstance().let {
            it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
        }
    }
}
