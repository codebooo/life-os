package com.lifeos.feature.clock

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Map-tap → real IANA zone (§Module 4): nearest major city within ~800 km
 * wins, which keeps DST correct (New York → America/New_York → UTC-4 in
 * summer, not the naive longitude/15 = -5). Falls back to the longitude
 * offset only in the open ocean.
 */
object TimeZoneAtlas {

    data class City(val name: String, val lat: Double, val lon: Double, val zoneId: String)

    val CITIES = listOf(
        City("Los Angeles", 34.05, -118.24, "America/Los_Angeles"),
        City("San Francisco", 37.77, -122.42, "America/Los_Angeles"),
        City("Vancouver", 49.28, -123.12, "America/Vancouver"),
        City("Denver", 39.74, -104.99, "America/Denver"),
        City("Phoenix", 33.45, -112.07, "America/Phoenix"),
        City("Mexico City", 19.43, -99.13, "America/Mexico_City"),
        City("Chicago", 41.88, -87.63, "America/Chicago"),
        City("Houston", 29.76, -95.37, "America/Chicago"),
        City("New York", 40.71, -74.01, "America/New_York"),
        City("Toronto", 43.65, -79.38, "America/Toronto"),
        City("Miami", 25.76, -80.19, "America/New_York"),
        City("Lima", -12.05, -77.04, "America/Lima"),
        City("Bogotá", 4.71, -74.07, "America/Bogota"),
        City("Santiago", -33.45, -70.67, "America/Santiago"),
        City("São Paulo", -23.55, -46.63, "America/Sao_Paulo"),
        City("Rio de Janeiro", -22.91, -43.17, "America/Sao_Paulo"),
        City("Buenos Aires", -34.60, -58.38, "America/Argentina/Buenos_Aires"),
        City("Reykjavík", 64.15, -21.94, "Atlantic/Reykjavik"),
        City("Lisbon", 38.72, -9.14, "Europe/Lisbon"),
        City("Dublin", 53.35, -6.26, "Europe/Dublin"),
        City("London", 51.51, -0.13, "Europe/London"),
        City("Madrid", 40.42, -3.70, "Europe/Madrid"),
        City("Paris", 48.86, 2.35, "Europe/Paris"),
        City("Amsterdam", 52.37, 4.90, "Europe/Amsterdam"),
        City("Brussels", 50.85, 4.35, "Europe/Brussels"),
        City("Zurich", 47.38, 8.54, "Europe/Zurich"),
        City("Berlin", 52.52, 13.40, "Europe/Berlin"),
        City("Munich", 48.14, 11.58, "Europe/Berlin"),
        City("Hamburg", 53.55, 9.99, "Europe/Berlin"),
        City("Vienna", 48.21, 16.37, "Europe/Vienna"),
        City("Rome", 41.90, 12.50, "Europe/Rome"),
        City("Prague", 50.08, 14.44, "Europe/Prague"),
        City("Stockholm", 59.33, 18.07, "Europe/Stockholm"),
        City("Oslo", 59.91, 10.75, "Europe/Oslo"),
        City("Copenhagen", 55.68, 12.57, "Europe/Copenhagen"),
        City("Warsaw", 52.23, 21.01, "Europe/Warsaw"),
        City("Helsinki", 60.17, 24.94, "Europe/Helsinki"),
        City("Athens", 37.98, 23.73, "Europe/Athens"),
        City("Bucharest", 44.43, 26.10, "Europe/Bucharest"),
        City("Kyiv", 50.45, 30.52, "Europe/Kyiv"),
        City("Istanbul", 41.01, 28.98, "Europe/Istanbul"),
        City("Moscow", 55.76, 37.62, "Europe/Moscow"),
        City("Cairo", 30.04, 31.24, "Africa/Cairo"),
        City("Lagos", 6.52, 3.38, "Africa/Lagos"),
        City("Nairobi", -1.29, 36.82, "Africa/Nairobi"),
        City("Johannesburg", -26.20, 28.05, "Africa/Johannesburg"),
        City("Casablanca", 33.57, -7.59, "Africa/Casablanca"),
        City("Dubai", 25.20, 55.27, "Asia/Dubai"),
        City("Tehran", 35.69, 51.39, "Asia/Tehran"),
        City("Karachi", 24.86, 67.01, "Asia/Karachi"),
        City("Mumbai", 19.08, 72.88, "Asia/Kolkata"),
        City("Delhi", 28.61, 77.21, "Asia/Kolkata"),
        City("Dhaka", 23.81, 90.41, "Asia/Dhaka"),
        City("Bangkok", 13.76, 100.50, "Asia/Bangkok"),
        City("Jakarta", -6.21, 106.85, "Asia/Jakarta"),
        City("Singapore", 1.35, 103.82, "Asia/Singapore"),
        City("Kuala Lumpur", 3.14, 101.69, "Asia/Kuala_Lumpur"),
        City("Hong Kong", 22.32, 114.17, "Asia/Hong_Kong"),
        City("Shanghai", 31.23, 121.47, "Asia/Shanghai"),
        City("Beijing", 39.90, 116.41, "Asia/Shanghai"),
        City("Taipei", 25.03, 121.57, "Asia/Taipei"),
        City("Manila", 14.60, 120.98, "Asia/Manila"),
        City("Seoul", 37.57, 126.98, "Asia/Seoul"),
        City("Tokyo", 35.68, 139.69, "Asia/Tokyo"),
        City("Osaka", 34.69, 135.50, "Asia/Tokyo"),
        City("Perth", -31.95, 115.86, "Australia/Perth"),
        City("Adelaide", -34.93, 138.60, "Australia/Adelaide"),
        City("Sydney", -33.87, 151.21, "Australia/Sydney"),
        City("Melbourne", -37.81, 144.96, "Australia/Melbourne"),
        City("Brisbane", -27.47, 153.03, "Australia/Brisbane"),
        City("Auckland", -36.85, 174.76, "Pacific/Auckland"),
        City("Honolulu", 21.31, -157.86, "Pacific/Honolulu"),
        City("Anchorage", 61.22, -149.90, "America/Anchorage"),
    )

    /** Nearest city within [maxDegrees] (≈ 8° ≈ 800–900 km), or null. */
    fun nearestCity(lat: Double, lon: Double, maxDegrees: Double = 8.0): City? =
        CITIES.minByOrNull { distanceDeg(lat, lon, it.lat, it.lon) }
            ?.takeIf { distanceDeg(lat, lon, it.lat, it.lon) <= maxDegrees }

    private fun distanceDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Longitude shrinks with latitude — cheap equirectangular metric.
        val dLon = abs(lon1 - lon2) * cos(Math.toRadians((lat1 + lat2) / 2))
        return hypot(lat1 - lat2, dLon)
    }
}
