package com.lifeos.feature.brick.data

/** Why an app may open, or not. */
sealed interface BlockDecision {
    data object Allow : BlockDecision
    /** Fully blocked by the running mode. */
    data class Blocked(val profileName: String, val unlockHint: String) : BlockDecision
    /** Had a daily allowance, now spent. */
    data class LimitReached(val profileName: String, val dailyMinutes: Int) : BlockDecision
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
     */
    fun decide(
        rules: ModeRules?,
        packageName: String,
        ownPackage: String,
        usedSecondsToday: Long,
    ): BlockDecision {
        if (rules == null) return BlockDecision.Allow
        // LifeOS itself is never blocked — otherwise you could not unlock.
        if (packageName == ownPackage) return BlockDecision.Allow
        if (packageName !in rules.blockedPackages) return BlockDecision.Allow

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

    /** Whether an end request coming from [by] satisfies the mode's exit rule. */
    fun canStop(rules: ModeRules, by: String): Boolean = when {
        by == FORCE -> true
        // Strict modes accept nothing but their configured condition.
        rules.strict -> by == rules.deactivator
        rules.deactivator == "MANUAL" -> true
        else -> by == rules.deactivator
    }

    fun unlockHint(rules: ModeRules): String = when (rules.deactivator) {
        "NFC" -> "Scan your Brick tag to unlock"
        "TIME" -> "Unlocks at ${formatMinute(rules.endMinuteOfDay)}"
        else -> if (rules.strict) "Strict mode — wait it out" else "Stop the mode in LifeOS to unlock"
    }

    fun formatMinute(minuteOfDay: Int?): String =
        minuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "the set time"
}
