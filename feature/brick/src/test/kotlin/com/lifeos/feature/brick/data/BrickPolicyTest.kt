package com.lifeos.feature.brick.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules that must never be wrong: what gets blocked, and what unlocks it. */
class BrickPolicyTest {

    private val socials = ModeRules(
        profileName = "No socials",
        blockedPackages = setOf("com.instagram.android", "com.zhiliaoapp.musically"),
        limits = emptyMap(),
        deactivator = "NFC",
        strict = false,
        endMinuteOfDay = null,
    )

    @Test
    fun `no running mode allows everything`() {
        assertEquals(
            BlockDecision.Allow,
            BrickPolicy.decide(null, "com.instagram.android", OWN, usedSecondsToday = 0),
        )
    }

    @Test
    fun `blocked package is blocked while the mode runs`() {
        val decision = BrickPolicy.decide(socials, "com.instagram.android", OWN, 0)
        assertTrue(decision is BlockDecision.Blocked)
        assertEquals("No socials", (decision as BlockDecision.Blocked).profileName)
        assertEquals("Scan your Brick tag to unlock", decision.unlockHint)
    }

    @Test
    fun `unlisted package stays allowed`() {
        assertEquals(BlockDecision.Allow, BrickPolicy.decide(socials, "com.android.deskclock", OWN, 0))
    }

    @Test
    fun `lifeos itself is never blocked so unlocking stays possible`() {
        val rules = socials.copy(blockedPackages = socials.blockedPackages + OWN)
        assertEquals(BlockDecision.Allow, BrickPolicy.decide(rules, OWN, OWN, 0))
    }

    @Test
    fun `allowance lets the app through until the minutes are spent`() {
        val rules = socials.copy(limits = mapOf("com.instagram.android" to 30))
        // 29 minutes in: still fine.
        assertEquals(
            BlockDecision.Allow,
            BrickPolicy.decide(rules, "com.instagram.android", OWN, usedSecondsToday = 29 * 60L),
        )
        // Exactly at the limit: done for the day.
        val spent = BrickPolicy.decide(rules, "com.instagram.android", OWN, usedSecondsToday = 30 * 60L)
        assertTrue(spent is BlockDecision.LimitReached)
        assertEquals(30, (spent as BlockDecision.LimitReached).dailyMinutes)
    }

    @Test
    fun `a zero minute allowance is a hard block not an open door`() {
        val rules = socials.copy(limits = mapOf("com.instagram.android" to 0))
        assertTrue(BrickPolicy.decide(rules, "com.instagram.android", OWN, 0) is BlockDecision.Blocked)
    }

    @Test
    fun `an allowance on one app does not leak to another blocked app`() {
        val rules = socials.copy(limits = mapOf("com.instagram.android" to 30))
        assertTrue(
            BrickPolicy.decide(rules, "com.zhiliaoapp.musically", OWN, usedSecondsToday = 0)
                is BlockDecision.Blocked,
        )
    }

    // ---- exit rules --------------------------------------------------------

    @Test
    fun `nfc mode only ends on an nfc tap`() {
        assertTrue(BrickPolicy.canStop(socials, "NFC"))
        assertFalse(BrickPolicy.canStop(socials, "MANUAL"))
        assertFalse(BrickPolicy.canStop(socials, "TIME"))
    }

    @Test
    fun `manual mode ends by hand`() {
        val manual = socials.copy(deactivator = "MANUAL")
        assertTrue(BrickPolicy.canStop(manual, "MANUAL"))
    }

    @Test
    fun `strict manual mode cannot be ended by any other trigger`() {
        val strictTime = socials.copy(deactivator = "TIME", strict = true, endMinuteOfDay = 20 * 60)
        assertFalse(BrickPolicy.canStop(strictTime, "MANUAL"))
        assertFalse(BrickPolicy.canStop(strictTime, "NFC"))
        assertTrue(BrickPolicy.canStop(strictTime, "TIME"))
    }

    @Test
    fun `force always wins so a deleted profile cannot wedge the phone`() {
        val strictNfc = socials.copy(strict = true)
        assertTrue(BrickPolicy.canStop(strictNfc, BrickPolicy.FORCE))
    }

    @Test
    fun `time mode tells the user when it opens up again`() {
        val rules = socials.copy(deactivator = "TIME", endMinuteOfDay = 20 * 60 + 5)
        val decision = BrickPolicy.decide(rules, "com.instagram.android", OWN, 0)
        assertEquals("Unlocks at 20:05", (decision as BlockDecision.Blocked).unlockHint)
    }

    private companion object {
        const val OWN = "com.lifeos"
    }
}
