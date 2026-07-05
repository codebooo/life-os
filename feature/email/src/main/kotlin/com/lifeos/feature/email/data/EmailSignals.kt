package com.lifeos.feature.email.data

/** Deterministic mail classification signals (§Module 1). Pure + tested. */
object EmailSignals {

    fun hasInvoiceSignal(subject: String, body: String): Boolean {
        val text = "$subject $body".lowercase()
        return INVOICE_KEYWORDS.any { text.contains(it) } &&
            AMOUNT.containsMatchIn(text)
    }

    fun hasSubscriptionSignal(subject: String, body: String): Boolean {
        val text = "$subject $body".lowercase()
        return SUBSCRIPTION_KEYWORDS.any { text.contains(it) }
    }

    /** Minimal VEVENT parse: SUMMARY + DTSTART (basic UTC/local formats). */
    fun parseInvite(body: String): Invite? {
        if (!body.contains("BEGIN:VEVENT")) return null
        val summary = Regex("SUMMARY(?:;[^:]*)?:(.+)").find(body)?.groupValues?.get(1)?.trim()
        val dtstart = Regex("DTSTART(?:;[^:]*)?:([0-9TZ]+)").find(body)?.groupValues?.get(1)?.trim()
            ?: return null
        val startsAt = parseIcsTimestamp(dtstart) ?: return null
        return Invite(title = summary ?: "Invitation", startsAt = startsAt)
    }

    fun parseIcsTimestamp(value: String): Long? {
        val pattern = when {
            value.matches(Regex("\\d{8}T\\d{6}Z")) -> "yyyyMMdd'T'HHmmss'Z'"
            value.matches(Regex("\\d{8}T\\d{6}")) -> "yyyyMMdd'T'HHmmss"
            value.matches(Regex("\\d{8}")) -> "yyyyMMdd"
            else -> return null
        }
        return try {
            java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                if (value.endsWith("Z")) timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(value)?.time
        } catch (e: Exception) {
            null
        }
    }

    data class Invite(val title: String, val startsAt: Long)

    private val INVOICE_KEYWORDS = listOf(
        "invoice", "rechnung", "zahlung fällig", "payment due", "amount due", "bill",
    )
    private val SUBSCRIPTION_KEYWORDS = listOf(
        "subscription", "abo", "renewal", "verlängert sich", "your plan", "membership",
    )
    private val AMOUNT = Regex("\\d+[.,]\\d{2}")
}
