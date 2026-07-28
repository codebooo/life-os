package com.lifeos.feature.clearsky.data

import com.lifeos.core.datastore.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A place the user watches the sky from. */
data class SkyPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
) {
    fun encode(): String = "$name~$latitude~$longitude"

    companion object {
        fun decode(raw: String): SkyPlace? {
            val parts = raw.split('~')
            val lat = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
            val lon = parts.getOrNull(2)?.toDoubleOrNull() ?: return null
            return SkyPlace(parts[0], lat, lon)
        }
    }
}

/**
 * Fetches and caches clearoutside.com forecasts plus the saved places
 * (§Module Clear Sky Map). Geocoding uses OpenStreetMap's Nominatim, so no
 * account or API key is involved anywhere in this module.
 */
@Singleton
class ClearSkyRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun places(): List<SkyPlace> =
        settingsRepository.clearSkyPlaces.first()
            .split('\n')
            .filter { it.isNotBlank() }
            .mapNotNull(SkyPlace::decode)

    suspend fun savePlace(place: SkyPlace) {
        val next = (places().filterNot { it.sameSpot(place) } + place).takeLast(20)
        settingsRepository.setClearSkyPlaces(next.joinToString("\n") { it.encode() })
    }

    suspend fun removePlace(place: SkyPlace) {
        val next = places().filterNot { it.sameSpot(place) }
        settingsRepository.setClearSkyPlaces(next.joinToString("\n") { it.encode() })
    }

    suspend fun lastPlace(): SkyPlace? = SkyPlace.decode(settingsRepository.clearSkyLastPlace.first())

    suspend fun setLastPlace(place: SkyPlace) =
        settingsRepository.setClearSkyLastPlace(place.encode())

    /**
     * @param view "midnight" centres the hour strip on midnight (the clearoutside
     *   default is the current hour); "midday" centres on noon.
     */
    suspend fun forecast(place: SkyPlace, view: String?): Result<SkyForecast> = withContext(Dispatchers.IO) {
        runCatching {
            val lat = trim(place.latitude)
            val lon = trim(place.longitude)
            val url = buildString {
                append("https://clearoutside.com/forecast/")
                append(lat).append('/').append(lon)
                if (!view.isNullOrBlank()) append("?view=").append(view)
            }
            val html = get(url)
            val parsed = ClearOutsideParser.parse(html, place.latitude, place.longitude)
            if (parsed.days.isEmpty()) error("clearoutside.com returned no forecast for this location")
            parsed
        }
    }

    /** Free-text place lookup (Nominatim, keyless). */
    suspend fun search(query: String): Result<List<SkyPlace>> = withContext(Dispatchers.IO) {
        runCatching {
            if (query.isBlank()) return@runCatching emptyList()
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val body = get(
                "https://nominatim.openstreetmap.org/search?format=json&limit=8&q=$encoded",
            )
            val array = JSONArray(body)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val lat = item.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
                val lon = item.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
                SkyPlace(item.optString("display_name").take(80), lat, lon)
            }
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            // Both hosts reject requests without a real UA.
            .header("User-Agent", "LifeOS/1.0 (personal astronomy client)")
            .header("Accept-Language", "en")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code} from ${request.url.host}")
            return body
        }
    }

    /** clearoutside.com expects at most two decimals in the path. */
    private fun trim(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)
}

private fun SkyPlace.sameSpot(other: SkyPlace): Boolean =
    kotlin.math.abs(latitude - other.latitude) < 0.005 && kotlin.math.abs(longitude - other.longitude) < 0.005
