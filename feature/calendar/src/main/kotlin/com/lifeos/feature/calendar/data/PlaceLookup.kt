package com.lifeos.feature.calendar.data

import com.lifeos.core.common.log.LifeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/** One suggestion from the geocoder. */
data class PlaceSuggestion(
    val label: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Location autocomplete for the event editor, via OpenStreetMap's Nominatim
 * (§Module 19). No key, no account, no tracking beyond the single query — and
 * because Nominatim's policy requires an identifying User-Agent, one is sent.
 *
 * Queries are only issued from the editor, on a debounce, so the public
 * instance's one-request-per-second rule is respected by construction.
 */
@Singleton
class PlaceLookup @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    suspend fun suggest(query: String, limit: Int = 6): List<PlaceSuggestion> =
        withContext(Dispatchers.IO) {
            val needle = query.trim()
            if (needle.length < 3) return@withContext emptyList()
            val url = "https://nominatim.openstreetmap.org/search" +
                "?format=jsonv2&addressdetails=0&limit=$limit&q=${encode(needle)}"
            try {
                val body = okHttpClient.newCall(
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept-Language", java.util.Locale.getDefault().toLanguageTag())
                        .build(),
                ).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    response.body.string()
                }
                val array = JSONArray(body)
                (0 until array.length()).mapNotNull { index ->
                    val row = array.optJSONObject(index) ?: return@mapNotNull null
                    val label = row.optString("display_name").ifBlank { return@mapNotNull null }
                    PlaceSuggestion(
                        label = label,
                        latitude = row.optString("lat").toDoubleOrNull() ?: return@mapNotNull null,
                        longitude = row.optString("lon").toDoubleOrNull() ?: return@mapNotNull null,
                    )
                }
            } catch (t: Throwable) {
                LifeLogger.w(TAG, "Nominatim lookup failed", t)
                emptyList()
            }
        }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val TAG = "PlaceLookup"
        const val USER_AGENT = "LifeOS/1.0 (private personal calendar; offline-first)"
    }
}
