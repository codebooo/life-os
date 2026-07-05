package com.lifeos.feature.email.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailSignalsTest {

    @Test
    fun `invoice needs keyword and amount`() {
        assertTrue(EmailSignals.hasInvoiceSignal("Ihre Rechnung", "Betrag: 49,99 EUR fällig"))
        assertFalse(EmailSignals.hasInvoiceSignal("Rechnung", "kein Betrag hier"))
        assertFalse(EmailSignals.hasInvoiceSignal("Newsletter", "nur 9,99 heute!"))
    }

    @Test
    fun `subscription keyword detected`() {
        assertTrue(EmailSignals.hasSubscriptionSignal("Your subscription renewal", ""))
        assertFalse(EmailSignals.hasSubscriptionSignal("Lunch tomorrow?", "12:30 ok?"))
    }

    @Test
    fun `vevent parsed with summary and utc start`() {
        val invite = EmailSignals.parseInvite(
            "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Team sync\nDTSTART:20260810T090000Z\nEND:VEVENT",
        )
        assertNotNull(invite)
        assertEquals("Team sync", invite!!.title)
    }

    @Test
    fun `no vevent no invite`() {
        assertNull(EmailSignals.parseInvite("plain text mail"))
    }

    @Test
    fun `date-only dtstart parses`() {
        assertNotNull(EmailSignals.parseIcsTimestamp("20261224"))
        assertNull(EmailSignals.parseIcsTimestamp("not-a-date"))
    }
}
