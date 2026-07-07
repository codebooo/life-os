package com.lifeos.feature.capture.data

import java.util.Calendar

/** A deterministic reading of a capture: destination + an optional epoch time. */
data class SmartCapture(
    val destination: CaptureDestination,
    val title: String,
    /** Absolute fire/start time for REMINDER/EVENT/TIMER or a timed TASK. */
    val at: Long? = null,
)

/**
 * Time-aware capture routing (§Module 20, [src 16]) — runs before the AI/keyword
 * classifier because time parsing is far more reliable done deterministically.
 * Detects timers ("timer 6m"), reminders, calendar events, and time-stamped
 * to-dos ("6pm feed cat") entirely on-device.
 */
object SmartCaptureParser {

    fun detect(text: String, now: Long = System.currentTimeMillis()): SmartCapture? {
        val trimmed = text.trim()
        val lower = trimmed.lowercase()

        // 1. Timer: "timer 6m", "6 min timer", "set a timer for 10 minutes".
        timerDuration(lower)?.let { durationMs ->
            return SmartCapture(
                destination = CaptureDestination.TIMER,
                title = trimmed,
                at = now + durationMs,
            )
        }

        val at = parseTime(lower, now)

        // 2. Explicit reminder request.
        if (REMINDER_SIGNALS.any { lower.contains(it) } && at != null) {
            return SmartCapture(CaptureDestination.REMINDER, cleanTitle(trimmed), at)
        }

        // 3. Calendar event: an event-ish noun with a time.
        if (at != null && EVENT_SIGNALS.any { lower.contains(it) }) {
            return SmartCapture(CaptureDestination.EVENT, cleanTitle(trimmed), at)
        }

        // 4. Time-stamped to-do: any imperative/task with a concrete time ("6pm feed cat").
        if (at != null) {
            return SmartCapture(CaptureDestination.TASK, cleanTitle(trimmed), at)
        }

        return null
    }

    /** Returns a duration in millis when the text reads as a timer, else null. */
    private fun timerDuration(lower: String): Long? {
        if (!lower.contains("timer")) return null
        val match = DURATION.find(lower) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val unit = match.groupValues[2]
        val unitMs = when {
            unit.startsWith("h") -> 3_600_000L
            unit.startsWith("s") -> 1_000L
            else -> 60_000L // minutes default
        }
        return amount * unitMs
    }

    /**
     * Absolute time for a phrase, or null. Requires a *concrete* time signal
     * (am/pm, HH:MM, "in N", tomorrow/tonight/today, or a weekday) so bare
     * numbers like "buy 2 apples" are not misread as times.
     */
    private fun parseTime(lower: String, now: Long): Long? {
        RELATIVE.find(lower)?.let { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: return@let
            val unitMs = when (match.groupValues[2].first()) {
                'm' -> 60_000L
                'h' -> 3_600_000L
                'd' -> 86_400_000L
                else -> return@let
            }
            return now + amount * unitMs
        }

        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val time = TIME.find(lower)
        val hour = time?.groupValues?.get(1)?.toIntOrNull()
        val minute = time?.groupValues?.get(2)?.toIntOrNull() ?: 0
        val meridiem = time?.groupValues?.get(3)
        val hasClock = time != null && (meridiem!!.isNotEmpty() || time.groupValues[2].isNotEmpty())

        fun applyTime(base: Calendar): Calendar = base.apply {
            val h = hour?.let {
                when {
                    meridiem == "pm" && it < 12 -> it + 12
                    meridiem == "am" && it == 12 -> 0
                    else -> it
                }
            } ?: 9
            set(Calendar.HOUR_OF_DAY, h)
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
                calendar.set(Calendar.HOUR_OF_DAY, hour?.let { if (it < 12) it + 12 else it } ?: 20)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return calendar.timeInMillis.takeIf { it > now }
            }
            lower.contains("today") && hasClock ->
                return applyTime(calendar).timeInMillis.takeIf { it > now }
        }

        WEEKDAYS.entries.firstOrNull { lower.contains(it.key) }?.let { (_, dayOfWeek) ->
            do {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            } while (calendar.get(Calendar.DAY_OF_WEEK) != dayOfWeek)
            return applyTime(calendar).timeInMillis
        }

        // A bare clock time ("6pm", "18:30") — only when there's a real clock signal.
        if (hour != null && hasClock) {
            val candidate = applyTime(calendar).timeInMillis
            return if (candidate > now) candidate else candidate + 86_400_000L
        }

        return null
    }

    /** Strips leading time words so the saved title reads cleanly. */
    private fun cleanTitle(text: String): String =
        text.replace(LEADING_TIME, "").trim().ifBlank { text }.replaceFirstChar { it.uppercase() }

    private val DURATION = Regex("(\\d+)\\s*(h(?:ou)?rs?|min(?:ute)?s?|m\\b|s(?:ec(?:ond)?s?)?)")
    // "in 30 minutes" plus compact "1m"/"5min"/"2h"/"1d"; unit must not run into
    // another letter so "6pm"/"5 mangoes" are untouched.
    private val RELATIVE =
        Regex("(?:in\\s+)?\\b(\\d+)\\s*(m(?:in(?:ute)?s?)?|h(?:(?:ou)?rs?)?|d(?:ays?)?)(?![a-z])")
    private val TIME = Regex("(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b")
    private val LEADING_TIME = Regex(
        "^(at\\s+)?\\d{1,2}(:\\d{2})?\\s*(am|pm)?\\s+|^(tomorrow|tonight|today)\\s+",
        RegexOption.IGNORE_CASE,
    )
    private val REMINDER_SIGNALS = listOf("remind", "reminder", "don't forget", "dont forget")
    private val EVENT_SIGNALS = listOf(
        "meeting", "appointment", "call with", "lunch", "dinner", "birthday",
        "party", "flight", "interview", "meet ", "dentist", "doctor",
    )
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
