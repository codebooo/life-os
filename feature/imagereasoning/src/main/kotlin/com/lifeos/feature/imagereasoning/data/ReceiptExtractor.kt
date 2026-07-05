package com.lifeos.feature.imagereasoning.data

import kotlinx.serialization.Serializable

/** Structured fields pulled from a receipt's OCR text ([src 25]). */
@Serializable
data class ReceiptExtraction(
    val merchant: String?,
    val totalCents: Long?,
    val dateText: String?,
    val warrantyMonths: Int?,
)

/**
 * Deterministic receipt parsing over OCR text (§Module 11). The Gemma-vision
 * pass refines these fields when a model is present; this baseline is
 * offline, instant, and unit-testable.
 */
object ReceiptExtractor {

    fun extract(ocrText: String): ReceiptExtraction {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return ReceiptExtraction(
            merchant = merchantOf(lines),
            totalCents = totalOf(lines),
            dateText = dateOf(ocrText),
            warrantyMonths = warrantyOf(ocrText),
        )
    }

    /** The merchant is almost always the first non-numeric prominent line. */
    private fun merchantOf(lines: List<String>): String? =
        lines.firstOrNull { line ->
            line.length in 3..40 &&
                line.count { it.isDigit() } <= line.length / 4 &&
                !line.contains(Regex("(?i)rechnung|receipt|invoice|kassenbon|beleg"))
        }

    /**
     * Picks the amount on a total-ish line, falling back to the largest
     * amount on the receipt (subtotals never exceed the total).
     */
    private fun totalOf(lines: List<String>): Long? {
        val totalLines = lines.filter { it.contains(TOTAL_HINT) }
        val candidates = (totalLines.ifEmpty { lines })
            .flatMap { line -> AMOUNT.findAll(line).map { parseAmount(it.value) } }
            .filterNotNull()
        return candidates.maxOrNull()
    }

    private fun dateOf(text: String): String? = DATE.find(text)?.value

    private fun warrantyOf(text: String): Int? {
        WARRANTY_YEARS_BEFORE.find(text)?.let { return it.groupValues[1].toIntOrNull()?.times(12) }
        WARRANTY_YEARS_AFTER.find(text)?.let { return it.groupValues[1].toIntOrNull()?.times(12) }
        WARRANTY_MONTHS.find(text)?.let { return it.groupValues[1].toIntOrNull() }
        return null
    }

    internal fun parseAmount(raw: String): Long? {
        val normalized = raw.replace(Regex("[^0-9.,]"), "")
        // Last separator is the decimal point; everything else is grouping.
        val lastSeparator = normalized.lastIndexOfAny(charArrayOf('.', ','))
        if (lastSeparator == -1) return normalized.toLongOrNull()?.times(100)
        val integerPart = normalized.substring(0, lastSeparator).replace(Regex("[.,]"), "")
        val fractionPart = normalized.substring(lastSeparator + 1)
        if (fractionPart.length != 2) return null
        val euros = integerPart.toLongOrNull() ?: return null
        val cents = fractionPart.toLongOrNull() ?: return null
        return euros * 100 + cents
    }

    private val TOTAL_HINT = Regex("(?i)\\b(summe|gesamt|total|zu zahlen|betrag|amount due)\\b")
    private val AMOUNT = Regex("\\d{1,3}(?:[.,]\\d{3})*[.,]\\d{2}\\b|\\b\\d+[.,]\\d{2}\\b")
    private val DATE = Regex("\\b\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}\\b|\\b\\d{4}-\\d{2}-\\d{2}\\b")
    private val WARRANTY_YEARS_BEFORE =
        Regex("(?i)(\\d+)\\s*(?:jahre?|years?)\\s*(?:garantie|warranty)")
    private val WARRANTY_YEARS_AFTER =
        Regex("(?i)(?:garantie|warranty)\\D{0,10}(\\d+)\\s*(?:jahre?|years?)")
    private val WARRANTY_MONTHS =
        Regex("(?i)(\\d+)\\s*(?:monate?|months?)\\s*(?:garantie|warranty)")
}
