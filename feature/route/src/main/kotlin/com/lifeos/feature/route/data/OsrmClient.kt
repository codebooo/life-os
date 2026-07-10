package com.lifeos.feature.route.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class RoutePlan(
    val distanceMeters: Double,
    val durationSeconds: Double,
    /** lat/lon pairs of the route geometry, ready for a map polyline. */
    val points: List<Pair<Double, Double>>,
)

/**
 * Routing via the public OSRM demo server (§Module 17) — the same
 * OpenStreetMap infrastructure the map tiles come from; no key, no Google.
 * Profiles: car / bike / foot.
 */
@Singleton
class OsrmClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun route(
        profile: String, // "driving" | "cycling" | "walking"
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): RoutePlan = withContext(Dispatchers.IO) {
        val url = "https://router.project-osrm.org/route/v1/$profile/" +
            "$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=geojson&steps=false"
        client.newCall(
            Request.Builder().url(url).header("User-Agent", "LifeOS/0.1 (personal)").build(),
        ).execute().use { response ->
            check(response.isSuccessful) { "Routing failed: HTTP ${response.code}" }
            val json = JSONObject(response.body.string())
            check(json.optString("code") == "Ok") { "Routing failed: ${json.optString("code")}" }
            val route = json.getJSONArray("routes").getJSONObject(0)
            val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
            val points = buildList {
                for (i in 0 until coords.length()) {
                    val pair = coords.getJSONArray(i)
                    add(pair.getDouble(1) to pair.getDouble(0)) // GeoJSON is lon,lat.
                }
            }
            RoutePlan(
                distanceMeters = route.getDouble("distance"),
                durationSeconds = route.getDouble("duration"),
                points = points,
            )
        }
    }
}
