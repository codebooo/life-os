package com.lifeos.feature.reminders.data

import java.util.Calendar

/**
 * Deterministic natural-language time parsing for the common cases
 * ("in 30 minutes", "tomorrow at 9", "monday 18:30"). Works offline; the
 * AI-assisted parse (R3) layers on top for anything fancier.
 */
object NaturalTimeParser {

    fun parse(text: String, now: Long = System.currentTimeMillis()): Long? {
        val lower = text.lowercase().trim()

        RELATIVE.find(lower)?.let { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: return@let
            val unitMillis = when {
                match.groupValues[2].startsWith("min") -> 60_000L
                match.groupValues[2].startsWith("h") -> 3_600_000L
                match.groupValues[2].startsWith("day") -> 86_400_000L
                else -> return@let
            }
            return now + amount * unitMillis
        }

        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val time = TIME.find(lower)
        val hour = time?.groupValues?.get(1)?.toIntOrNull()
        val minute = time?.groupValues?.get(2)?.toIntOrNull() ?: 0
        val isPm = time?.groupValues?.get(3) == "pm"

        fun applyTime(base: Calendar): Calendar = base.apply {
            set(Calendar.HOUR_OF_DAY, hour?.let { if (isPm && it < 12) it + 12 else it } ?: 9)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when {
            lower.contains("tomorrow") -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                return applyTime(calendar).timeInMillis
            }
            lower.contains("tonight") -> {
                calendar.set(Calendar.HOUR_OF_DAY, hour ?: 20)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return calendar.timeInMillis.takeIf { it > now }
            }
            lower.contains("today") && hour != null ->
                return applyTime(calendar).timeInMillis.takeIf { it > now }
        }

        WEEKDAYS.entries.firstOrNull { lower.contains(it.key) }?.let { (_, dayOfWeek) ->
            while (calendar.get(Calendar.DAY_OF_WEEK) != dayOfWeek ||
                calendar.timeInMillis <= now
            ) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                applyTime(calendar)
            }
            return applyTime(calendar).timeInMillis
        }

        if (hour != null) {
            val candidate = applyTime(calendar).timeInMillis
            return if (candidate > now) candidate else candidate + 86_400_000L
        }

        return null
    }

    private val RELATIVE = Regex("in\\s+(\\d+)\\s*(min(?:ute)?s?|h(?:ou)?rs?|days?)")
    private val TIME = Regex("(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b")
    private val WEEKDAYS = mapOf(
        "monday" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY,
        "sunday" to Calendar.SUNDAY,
    )
}
