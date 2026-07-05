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

    /** GET /track/shipments; requires the user's DHL API key (§9.3). */
    suspend fun track(trackingNumber: String, apiKey: String): DhlShipment =
        withContext(dispatchers.io) {
            val request = Request.Builder()
                .url("$BASE_URL/track/shipments?trackingNumber=$trackingNumber")
                .header("DHL-API-Key", apiKey)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> DhlShipment(
                        status = "NOT_FOUND",
                        statusDescription = "Not in DHL's system yet",
                        estimatedDeliveryAt = null,
                        events = emptyList(),
                    )
                    !response.isSuccessful ->
                        throw IOException("DHL API returned HTTP ${response.code}")
                    else -> parse(response.body.string())
                }
            }
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
