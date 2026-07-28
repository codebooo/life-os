package com.lifeos.feature.clearsky.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the HTML scrape against small markup drifts on clearoutside.com. */
class ClearOutsideParserTest {

    private val html = """
        <h1>Forecast for Mitte, Germany (52.52,13.40)</h1>
        <span class="btn btn-primary btn-bortle-8">Est. Sky Quality: <strong>18.19</strong> Magnitude.
        <strong>Class 8</strong> Bortle. <strong>5.75</strong> mcd/m<sup>2</sup> Brightness.
        <strong>5573.88</strong> &mu;cd/m<sup>2</sup> Artificial Brightness.</span>
        <h2>Generated: 28/07/26 11:37:05. Forecast: 28/07/26 to 03/08/26. Timezone: UTC+2.00</h2>
        <div class="fc" id="forecast">
        <div class="fc_day" id="day_0">
        <div class="fc_day_date" title="Show Detailed Forecast"><span>Tuesday</span> 28</div>
        <div class="fc_moon">
        <span class="fc_moon_phase">Waxing Gibbous</span>
        <span class="fc_moon_percentage">100%</span>
        <span class="fc_moon_riseset"><img src="a.png" /> 20:57 <img src="b.png" /> 04:40</span>
        </div>
        <div class="fc_hours fc_hour_ratings">
        <ul><li class="fc_good"><span class="glyphicon glyphicon-time"></span> 11 <span>Good</span></li>
        <li class="fc_ok"><span class="glyphicon glyphicon-time"></span> 12 <span>OK</span></li>
        <li class="fc_bad"><span class="glyphicon glyphicon-time"></span> 13 <span>Bad</span></li></ul>
        <div class="fc_daylight" data-content="<strong>Sunrise:</strong> 05:22 &nbsp; <strong>Sunset:</strong> 21:05 <br /> <strong>Sun Transit:</strong> 13:12 <br /> <strong>Civil Dark:</strong> 21:47 - 04:39 <br /> <strong>Nautical Dark:</strong> 22:47 - 03:40 <br /> <strong>Astro Dark:</strong> 00:28 - 02:05" data-title="Sun Rise/Set Details"><span>Sun</span></div>
        </div>
        <div class="fc_detail hidden-xs">
        <div class="fc_detail_row">
        <span class="fc_detail_label"><span>Total Clouds (% Sky Obscured)</span></span>
        <div class="fc_hours"><ul><li class="fc_cl_90">12</li><li class="fc_cl_80">20</li><li class="fc_cl_55">43</li></ul></div>
        </div>
        <div class="fc_detail_row fc_detail_row_tall">
        <span class="fc_detail_label"><span>Wind Speed/Direction (mph)</span></span>
        <div class="fc_hours"><ul><li title="9mph from the West (271&deg;)" class="fc_wind west fc_good"><span>9</span></li>
        <li title="10mph from the West (273&deg;)" class="fc_wind west fc_good"><span>10</span></li>
        <li title="11mph from the West (273&deg;)" class="fc_wind west fc_ok"><span>11</span></li></ul></div>
        </div>
        <div class="fc_detail_row">
        <span class="fc_detail_label"><span>Temperature (&deg;C)</span></span>
        <div class="fc_hours"><ul><li class="fc_good">22</li><li class="fc_good">23</li><li class="fc_good">21</li></ul></div>
        </div>
        </div>
        </div>
        </div>
    """.trimIndent()

    @Test
    fun `parses header and sky quality`() {
        val forecast = ClearOutsideParser.parse(html, 0.0, 0.0)
        assertEquals("Mitte, Germany", forecast.locationName)
        assertEquals(52.52, forecast.latitude, 0.001)
        assertEquals(13.40, forecast.longitude, 0.001)
        assertEquals("18.19", forecast.skyQualityMagnitude)
        assertEquals("8", forecast.bortleClass)
        assertEquals("UTC+2.00", forecast.timezone)
    }

    @Test
    fun `parses day ephemeris and ratings`() {
        val day = ClearOutsideParser.parse(html, 0.0, 0.0).days.single()
        assertEquals("Tuesday", day.weekday)
        assertEquals("28", day.dayOfMonth)
        assertEquals("Waxing Gibbous", day.moonPhase)
        assertEquals("100%", day.moonIllumination)
        assertEquals("20:57", day.moonRise)
        assertEquals("04:40", day.moonSet)
        assertEquals("05:22", day.sunRise)
        assertEquals("21:05", day.sunSet)
        assertEquals("13:12", day.sunTransit)
        assertEquals("00:28 - 02:05", day.astroDark)
        assertEquals(listOf("11", "12", "13"), day.hours.map { it.hour })
        assertEquals(
            listOf(SkyRating.GOOD, SkyRating.OK, SkyRating.BAD),
            day.hours.map { it.rating },
        )
    }

    @Test
    fun `parses every detail row with hourly values`() {
        val day = ClearOutsideParser.parse(html, 0.0, 0.0).days.single()
        assertEquals(3, day.rows.size)
        assertEquals("Total Clouds (% Sky Obscured)", day.rows[0].label)
        assertEquals(listOf("12", "20", "43"), day.rows[0].values)
        assertEquals(listOf("9", "10", "11"), day.rows[1].values)
        assertTrue(day.rows[1].details[0].contains("from the West"))
        assertEquals("Temperature (°C)", day.rows[2].label)
    }
}
