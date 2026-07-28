package com.lifeos.feature.brick.data

/** Why an app may open, or not. */
sealed interface BlockDecision {
    data object Allow : BlockDecision
    /** Fully blocked by the running mode. */
    data class Blocked(val profileName: String, val unlockHint: String) : BlockDecision
    /** Had a daily allowance, now spent. */
    data class LimitReached(val profileName: String, val dailyMinutes: Int) : BlockDecision
}

/** Why a tag tap did or did not buy access in inverse mode. */
sealed interface UnlockDecision {
    /** Granted: access is open until [until]; [used] of [allowance] spent (0 = unlimited). */
    data class Granted(val until: Long, val used: Int, val allowance: Int) : UnlockDecision
    /** Already unlocked; nothing spent. */
    data class AlreadyOpen(val until: Long) : UnlockDecision
    /** Every unlock for this window is gone. */
    data class NoneLeft(val allowance: Int) : UnlockDecision
}

/** What a mode looks like to the policy — no Android types, so it is unit-testable. */
data class ModeRules(
    val profileName: String,
    val blockedPackages: Set<String>,
    /** package -> daily allowance in minutes; absent = hard block. */
    val limits: Map<String, Int>,
    val deactivator: String,
    val strict: Boolean,
    val endMinuteOfDay: Int?,
    /** Inverse mode: blocked through the window, tag taps buy access. */
    val inverse: Boolean = false,
    /** Minutes one tag tap opens up. */
    val unlockMinutes: Int = 60,
    /** Unlocks allowed per window run; 0 = unlimited. */
    val unlockAllowance: Int = 1,
    /** Epoch millis the current unlock runs until; null = nothing open. */
    val unlockUntil: Long? = null,
    /** Unlocks already spent in this window run. */
    val unlocksUsed: Int = 0,
)

/**
 * The rules that decide blocking and unlocking (§Module Brick), kept pure on
 * purpose: this is the part that must never be wrong, so it is covered by unit
 * tests instead of only by tapping a phone.
 */
object BrickPolicy {

    /** Internal override used when a profile disappears; bypasses strict rules. */
    const val FORCE = "__force__"

    /**
     * Should [packageName] be allowed in the foreground?
     * [usedSecondsToday] only matters for packages that carry an allowance.
     * [nowMillis] decides whether an inverse-mode unlock is still running.
     */
    fun decide(
        rules: ModeRules?,
        packageName: String,
        ownPackage: String,
        usedSecondsToday: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): BlockDecision {
        if (rules == null) return BlockDecision.Allow
        // LifeOS itself is never blocked — otherwise you could not unlock.
        if (packageName == ownPackage) return BlockDecision.Allow
        if (packageName !in rules.blockedPackages) return BlockDecision.Allow

        // Inverse mode: a live unlock opens everything for its duration.
        if (rules.inverse && unlockActive(rules, nowMillis)) return BlockDecision.Allow

        val limit = rules.limits[packageName]
        if (limit != null && limit > 0) {
            return if (usedSecondsToday < limit * 60L) {
                BlockDecision.Allow
            } else {
                BlockDecision.LimitReached(rules.profileName, limit)
            }
        }
        return BlockDecision.Blocked(rules.profileName, unlockHint(rules))
    }

    /** Whether an unlock is running at [nowMillis]. */
    fun unlockActive(rules: ModeRules, nowMillis: Long = System.currentTimeMillis()): Boolean =
        rules.inverse && (rules.unlockUntil ?: 0L) > nowMillis

    /** Unlocks still available in this window run; null when unlimited. */
    fun unlocksLeft(rules: ModeRules): Int? =
        if (rules.unlockAllowance <= 0) null else (rules.unlockAllowance - rules.unlocksUsed).coerceAtLeast(0)

    /** What a tag tap does in inverse mode. */
    fun unlock(rules: ModeRules, nowMillis: Long = System.currentTimeMillis()): UnlockDecision {
        if (unlockActive(rules, nowMillis)) {
            return UnlockDecision.AlreadyOpen(rules.unlockUntil ?: nowMillis)
        }
        val left = unlocksLeft(rules)
        if (left != null && left <= 0) return UnlockDecision.NoneLeft(rules.unlockAllowance)
        val minutes = rules.unlockMinutes.coerceAtLeast(1)
        return UnlockDecision.Granted(
            until = nowMillis + minutes * 60_000L,
            used = rules.unlocksUsed + 1,
            allowance = rules.unlockAllowance,
        )
    }

    /** Whether an end request coming from [by] satisfies the mode's exit rule. */
    fun canStop(rules: ModeRules, by: String): Boolean = when {
        by == FORCE -> true
        // Inverse modes are ended by their schedule, never by the tag: the tag
        // buys access inside the window instead of ending it.
        rules.inverse -> by == "TIME"
        // Strict modes accept nothing but their configured condition.
        rules.strict -> by == rules.deactivator
        rules.deactivator == "MANUAL" -> true
        else -> by == rules.deactivator
    }

    fun unlockHint(rules: ModeRules): String = when {
        rules.inverse -> {
            val left = unlocksLeft(rules)
            when {
                left == null -> "Scan your Brick tag for ${rules.unlockMinutes} min of access"
                left > 0 -> "Scan your Brick tag for ${rules.unlockMinutes} min " +
                    "($left of ${rules.unlockAllowance} unlocks left)"
                else -> "No unlocks left — opens again at ${formatMinute(rules.endMinuteOfDay)}"
            }
        }

        rules.deactivator == "NFC" -> "Scan your Brick tag to unlock"
        rules.deactivator == "TIME" -> "Unlocks at ${formatMinute(rules.endMinuteOfDay)}"
        else -> if (rules.strict) "Strict mode — wait it out" else "Stop the mode in LifeOS to unlock"
    }

    fun formatMinute(minuteOfDay: Int?): String =
        minuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "the set time"
}
