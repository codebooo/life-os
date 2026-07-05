package com.lifeos.feature.dhl.data

import com.lifeos.core.common.coroutines.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Parsed shipment state from the DHL Unified Tracking API (§Module 15). */
data class DhlShipment(
    val status: String,
    val statusDescription: String?,
    val estimatedDeliveryAt: Long?,
    val events: List<DhlEvent>,
)

data class DhlEvent(
    val status: String,
    val description: String?,
    val location: String?,
    val at: Long,
)

@Singleton
class DhlApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider,
) {

    /**
     * GET /track/shipments. Unified Tracking authenticates with the API key
     * header alone; when a secret is present a Basic-auth retry covers the
     * Parcel DE API family. Errors carry DHL's own detail text so the UI can
     * show real feedback (fresh keys take up to a few hours to activate).
     */
    suspend fun track(trackingNumber: String, apiKey: String, apiSecret: String = ""): DhlShipment =
        withContext(dispatchers.io) {
            val url = "$BASE_URL/track/shipments?trackingNumber=$trackingNumber"
            var response = call(
                Request.Builder().url(url).header("DHL-API-Key", apiKey).build(),
            )
            if (response.first == 401 && apiSecret.isNotBlank()) {
                response = call(
                    Request.Builder()
                        .url(url)
                        .header("DHL-API-Key", apiKey)
                        .header("Authorization", okhttp3.Credentials.basic(apiKey, apiSecret))
                        .build(),
                )
            }
            val (code, body) = response
            when {
                code == 404 -> DhlShipment(
                    status = "NOT_FOUND",
                    statusDescription = "Not in DHL's system yet",
                    estimatedDeliveryAt = null,
                    events = emptyList(),
                )
                code == 401 -> throw IOException(
                    "DHL rejected the API key (401). New keys take up to a few hours to " +
                        "activate — check the app's 'Shipment Tracking - Unified' subscription on developer.dhl.com",
                )
                code == 429 -> throw IOException("DHL rate limit hit (429) — try again in a minute")
                code !in 200..299 -> throw IOException(
                    "DHL API HTTP $code: ${errorDetail(body) ?: "unexpected response"}",
                )
                else -> parse(body)
            }
        }

    private fun call(request: Request): Pair<Int, String> =
        okHttpClient.newCall(request).execute().use { it.code to it.body.string() }

    private fun errorDetail(body: String): String? = try {
        json.decodeFromString<ErrorDto>(body).detail
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val BASE_URL = "https://api-eu.dhl.com"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String): DhlShipment {
            val response = json.decodeFromString<TrackResponse>(body)
            val shipment = response.shipments.firstOrNull()
                ?: return DhlShipment("UNKNOWN", null, null, emptyList())
            return DhlShipment(
                status = shipment.status?.statusCode?.uppercase() ?: "UNKNOWN",
                statusDescription = shipment.status?.description ?: shipment.status?.status,
                estimatedDeliveryAt = shipment.estimatedTimeOfDelivery?.let(::parseTimestamp),
                events = shipment.events.mapNotNull { event ->
                    val at = event.timestamp?.let(::parseTimestamp) ?: return@mapNotNull null
                    DhlEvent(
                        status = event.statusCode?.uppercase() ?: "EVENT",
                        description = event.description ?: event.status,
                        location = event.location?.address?.addressLocality,
                        at = at,
                    )
                },
            )
        }

        fun parseTimestamp(value: String): Long? {
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd",
            )
            formats.forEach { pattern ->
                try {
                    return SimpleDateFormat(pattern, Locale.US).parse(value)?.time
                } catch (ignored: Exception) {
                }
            }
            return null
        }
    }

    @Serializable
    internal data class ErrorDto(val detail: String? = null)

    @Serializable
    internal data class TrackResponse(val shipments: List<ShipmentDto> = emptyList())

    @Serializable
    internal data class ShipmentDto(
        val status: StatusDto? = null,
        val estimatedTimeOfDelivery: String? = null,
        val events: List<EventDto> = emptyList(),
    )

    @Serializable
    internal data class StatusDto(
        val statusCode: String? = null,
        val status: String? = null,
        val description: String? = null,
    )

    @Serializable
    internal data class EventDto(
        val timestamp: String? = null,
        val statusCode: String? = null,
        val status: String? = null,
        val description: String? = null,
        val location: LocationDto? = null,
    )

    @Serializable
    internal data class LocationDto(val address: AddressDto? = null)

    @Serializable
    internal data class AddressDto(val addressLocality: String? = null)
}
