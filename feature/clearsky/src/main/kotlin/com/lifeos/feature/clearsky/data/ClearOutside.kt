package com.lifeos.feature.clearsky.data

/**
 * Parsed clearoutside.com forecast (§Module Clear Sky Map).
 *
 * clearoutside.com has no public JSON API - the referenced ClearOutsideAPY
 * project scrapes the HTML forecast page, and so does this. No key is needed.
 */
data class SkyForecast(
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val skyQualityMagnitude: String,
    val bortleClass: String,
    val brightness: String,
    val artificialBrightness: String,
    val generated: String,
    val range: String,
    val timezone: String,
    val days: List<SkyDay>,
)

/** One forecast day column: ratings, ephemeris and every detail row. */
data class SkyDay(
    val weekday: String,
    val dayOfMonth: String,
    val moonPhase: String,
    val moonIllumination: String,
    val moonRise: String,
    val moonSet: String,
    val sunRise: String,
    val sunSet: String,
    val sunTransit: String,
    val civilDark: String,
    val nauticalDark: String,
    val astroDark: String,
    val hours: List<SkyHour>,
    val rows: List<SkyRow>,
)

/** Hourly "is it clear out?" verdict as shown in the top strip. */
data class SkyHour(val hour: String, val rating: SkyRating)

enum class SkyRating { GOOD, OK, BAD, UNKNOWN }

/** One detail row (clouds, wind, temperature, ...) aligned to [SkyDay.hours]. */
data class SkyRow(
    val label: String,
    val values: List<String>,
    val details: List<String> = emptyList(),
)

internal object ClearOutsideParser {

    fun parse(html: String, fallbackLat: Double, fallbackLon: Double): SkyForecast {
        val header = Regex("""<h1>Forecast for (.*?)\s*\(([-0-9.]+),\s*([-0-9.]+)\)</h1>""")
            .find(html)
        val quality = Regex(
            """Est\. Sky Quality:.*?<strong>([^<]*)</strong>\s*Magnitude.*?<strong>Class ([^<]*)</strong>""" +
                """\s*Bortle.*?<strong>([^<]*)</strong>\s*mcd.*?<strong>([^<]*)</strong>\s*&mu;cd""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(html) ?: Regex(
            """Est\. Sky Quality:.*?<strong>([^<]*)</strong>\s*Magnitude.*?<strong>Class ([^<]*)</strong>\s*Bortle""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(html)
        val generated = Regex("""<h2>Generated:\s*(.*?)\.\s*Forecast:\s*(.*?)\.\s*Timezone:\s*(.*?)</h2>""")
            .find(html)

        return SkyForecast(
            locationName = header?.groupValues?.get(1)?.let(::unescape).orEmpty()
                .ifBlank { "$fallbackLat, $fallbackLon" },
            latitude = header?.groupValues?.get(2)?.toDoubleOrNull() ?: fallbackLat,
            longitude = header?.groupValues?.get(3)?.toDoubleOrNull() ?: fallbackLon,
            skyQualityMagnitude = quality?.groupValues?.getOrNull(1)?.trim().orEmpty(),
            bortleClass = quality?.groupValues?.getOrNull(2)?.trim().orEmpty(),
            brightness = quality?.groupValues?.getOrNull(3)?.trim().orEmpty(),
            artificialBrightness = quality?.groupValues?.getOrNull(4)?.trim().orEmpty(),
            generated = generated?.groupValues?.getOrNull(1)?.trim().orEmpty(),
            range = generated?.groupValues?.getOrNull(2)?.trim().orEmpty(),
            timezone = generated?.groupValues?.getOrNull(3)?.trim().orEmpty(),
            days = dayBlocks(html).map(::parseDay),
        )
    }

    /** Splits the forecast container into one chunk per `<div class="fc_day" id="day_N">`. */
    private fun dayBlocks(html: String): List<String> {
        val starts = Regex("""<div class="fc_day" id="day_\d+">""").findAll(html).map { it.range.first }.toList()
        if (starts.isEmpty()) return emptyList()
        return starts.mapIndexed { index, start ->
            val end = starts.getOrNull(index + 1) ?: html.length
            html.substring(start, end)
        }
    }

    private fun parseDay(block: String): SkyDay {
        val date = Regex("""class="fc_day_date"[^>]*><span>([^<]*)</span>\s*([0-9]+)""").find(block)
        val moonRiseSet = Regex("""class="fc_moon_riseset">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
            .find(block)?.groupValues?.get(1).orEmpty()
        val moonTimes = Regex("""\d{2}:\d{2}""").findAll(moonRiseSet).map { it.value }.toList()
        val daylight = Regex("""class="fc_daylight"[^>]*data-content="(.*?)"""", RegexOption.DOT_MATCHES_ALL)
            .find(block)?.groupValues?.get(1)?.let(::unescape).orEmpty()

        return SkyDay(
            weekday = date?.groupValues?.get(1).orEmpty(),
            dayOfMonth = date?.groupValues?.get(2).orEmpty(),
            moonPhase = Regex("""class="fc_moon_phase">([^<]*)<""").find(block)?.groupValues?.get(1)?.trim().orEmpty(),
            moonIllumination = Regex("""class="fc_moon_percentage">([^<]*)<""")
                .find(block)?.groupValues?.get(1)?.trim().orEmpty(),
            moonRise = moonTimes.getOrNull(0).orEmpty(),
            moonSet = moonTimes.getOrNull(1).orEmpty(),
            sunRise = field(daylight, "Sunrise"),
            sunSet = field(daylight, "Sunset"),
            sunTransit = field(daylight, "Sun Transit"),
            civilDark = field(daylight, "Civil Dark"),
            nauticalDark = field(daylight, "Nautical Dark"),
            astroDark = field(daylight, "Astro Dark"),
            hours = parseHours(block),
            rows = parseRows(block),
        )
    }

    private fun parseHours(block: String): List<SkyHour> {
        val strip = Regex("""fc_hour_ratings.*?</ul>""", RegexOption.DOT_MATCHES_ALL).find(block)?.value.orEmpty()
        return Regex("""<li class="fc_(good|ok|bad|none)">.*?</span>\s*([0-9]{1,2})\s*<span>""")
            .findAll(strip)
            .map { match ->
                SkyHour(
                    hour = match.groupValues[2].padStart(2, '0'),
                    rating = when (match.groupValues[1]) {
                        "good" -> SkyRating.GOOD
                        "ok" -> SkyRating.OK
                        "bad" -> SkyRating.BAD
                        else -> SkyRating.UNKNOWN
                    },
                )
            }
            .toList()
    }

    private fun parseRows(block: String): List<SkyRow> {
        val detail = Regex("""<div class="fc_detail[^"]*">(.*)""", RegexOption.DOT_MATCHES_ALL)
            .find(block)?.groupValues?.get(1).orEmpty()
        val rowRegex = Regex(
            """<span class="fc_detail_label"><span>(.*?)</span></span>(.*?)</ul>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        return rowRegex.findAll(detail).map { match ->
            val label = unescape(match.groupValues[1]).trim()
            val body = match.groupValues[2]
            val items = Regex("""<li([^>]*)>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL).findAll(body).toList()
            SkyRow(
                label = label,
                values = items.map { unescape(stripTags(it.groupValues[2])).trim() },
                details = items.map {
                    Regex("""title="([^"]*)"""").find(it.groupValues[1])?.groupValues?.get(1)
                        ?.let(::unescape).orEmpty()
                },
            )
        }.filter { it.values.isNotEmpty() }.toList()
    }

    /** Pulls `<strong>Label:</strong> value` out of the popover text. */
    private fun field(text: String, label: String): String =
        Regex("""$label:</strong>\s*([^<]*)""").find(text)?.groupValues?.get(1)?.trim()?.trim('.')
            ?.replace(Regex("""\s{2,}"""), " ")
            .orEmpty()

    private fun stripTags(value: String): String = value.replace(Regex("""<[^>]*>"""), " ")

    private fun unescape(value: String): String = value
        .replace("&deg;", "°")
        .replace("&mu;", "µ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("""\s{2,}"""), " ")
}
