package com.lifeos.feature.finance.data

import com.lifeos.core.database.finance.TransactionEntity

/**
 * Recurring-charge detection ([src 26], R9): same merchant, similar amount,
 * regular cadence. Pure and unit-tested.
 */
object SubscriptionDetector {

    data class Detection(val cadence: String, val amountCents: Long, val lastChargedAt: Long)

    /** Needs ≥2 prior similar charges; cadence from median gap. */
    fun detect(history: List<TransactionEntity>): Detection? {
        val charges = history.filter { it.amountCents < 0 }.sortedBy { it.at }
        if (charges.size < 3) return null
        val gaps = charges.zipWithNext { a, b -> b.at - a.at }.sorted()
        val median = gaps[gaps.size / 2]
        val days = median / 86_400_000L
        val cadence = when (days) {
            in 25..35 -> "MONTHLY"
            in 350..380 -> "YEARLY"
            else -> return null
        }
        val last = charges.last()
        return Detection(cadence, last.amountCents, last.at)
    }
}
