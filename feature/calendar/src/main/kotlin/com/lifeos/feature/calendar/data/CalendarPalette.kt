package com.lifeos.feature.calendar.data

/**
 * The colours a calendar can wear (§Module 19). Kept as plain ARGB ints so the
 * repository, the Jarvis bridge and the UI all speak the same language, and any
 * `#RRGGBB` the user types is just as valid as a named one.
 */
object CalendarPalette {

    /** Name to ARGB, in the order the picker shows them. */
    val named: List<Pair<String, Int>> = listOf(
        "Blue" to 0xFF3B82F6.toInt(),
        "Indigo" to 0xFF6366F1.toInt(),
        "Violet" to 0xFF8B5CF6.toInt(),
        "Pink" to 0xFFEC4899.toInt(),
        "Red" to 0xFFEF4444.toInt(),
        "Orange" to 0xFFF97316.toInt(),
        "Amber" to 0xFFF59E0B.toInt(),
        "Lime" to 0xFF84CC16.toInt(),
        "Green" to 0xFF22C55E.toInt(),
        "Teal" to 0xFF14B8A6.toInt(),
        "Cyan" to 0xFF06B6D4.toInt(),
        "Slate" to 0xFF64748B.toInt(),
    )

    val default: Int = named.first().second

    /** Accepts a palette name or a hex literal; falls back to [default]. */
    fun parse(value: String): Int {
        val trimmed = value.trim()
        named.firstOrNull { it.first.equals(trimmed, ignoreCase = true) }?.let { return it.second }
        val hex = trimmed.removePrefix("#")
        return when (hex.length) {
            6 -> hex.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() } ?: default
            8 -> hex.toLongOrNull(16)?.toInt() ?: default
            else -> default
        }
    }

    fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** Closest palette name, for describing a colour back to the user. */
    fun nameOf(argb: Int): String =
        named.firstOrNull { it.second == argb }?.first ?: hex(argb)

    /** Pick a colour for a new calendar that is not already in use. */
    fun nextUnused(used: Collection<Int>): Int =
        named.firstOrNull { it.second !in used }?.second ?: default
}
