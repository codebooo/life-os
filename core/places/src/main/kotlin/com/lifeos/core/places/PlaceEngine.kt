package com.lifeos.core.places

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.database.places.PlaceDao
import com.lifeos.core.database.places.PlaceEntity
import com.lifeos.core.service.LifeEvent
import com.lifeos.core.service.LifeEventBus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the phone is, in terms the user named (§Module Places).
 *
 * Android gives no geofence API outside Play Services, so this polls cheaply
 * instead: the Wi-Fi network it is joined to (free, instant, reliable indoors)
 * plus the last known location from the OS providers (no active GPS fix is ever
 * requested). Enter and leave are published on [LifeEventBus], which is what the
 * Triggers engine listens to.
 */
@Singleton
class PlaceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val placeDao: PlaceDao,
    private val eventBus: LifeEventBus,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _current = MutableStateFlow<PlaceEntity?>(null)
    val current = _current.asStateFlow()

    private var started = false

    /** Starts the poll loop once; safe to call from every process entry point. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                runCatching { evaluate() }
                    .onFailure { LifeLogger.w(TAG, "Place evaluation failed", it) }
                delay(POLL_MS)
            }
        }
    }

    /** Re-checks immediately; used after saving a place and by the rules engine. */
    suspend fun refresh() = evaluate()

    private suspend fun evaluate() {
        val places = placeDao.all()
        if (places.isEmpty()) {
            transitionTo(null)
            return
        }
        val ssid = currentSsid()
        val byWifi = ssid?.let { network ->
            places.firstOrNull { it.wifiSsid?.trim()?.equals(network, ignoreCase = true) == true }
        }
        if (byWifi != null) {
            transitionTo(byWifi)
            return
        }
        val fix = lastKnownLocation()
        if (fix == null) {
            // No signal either way: keep the last verdict rather than inventing
            // a "left" event the user would feel as a false trigger.
            return
        }
        val byDistance = places
            .filter { it.latitude != null && it.longitude != null }
            .map { place -> place to distanceMeters(fix, place) }
            .filter { (place, distance) -> distance <= place.radiusMeters }
            .minByOrNull { it.second }
            ?.first
        transitionTo(byDistance)
    }

    private suspend fun transitionTo(place: PlaceEntity?) {
        val previous = _current.value
        if (previous?.id == place?.id) return
        _current.value = place
        previous?.let { eventBus.publish(LifeEvent.PlaceLeft(it.id, it.name)) }
        place?.let { eventBus.publish(LifeEvent.PlaceEntered(it.id, it.name)) }
    }

    private fun distanceMeters(fix: Location, place: PlaceEntity): Float {
        val result = FloatArray(1)
        Location.distanceBetween(
            fix.latitude,
            fix.longitude,
            place.latitude ?: return Float.MAX_VALUE,
            place.longitude ?: return Float.MAX_VALUE,
            result,
        )
        return result[0]
    }

    /** Joined network name, or null when Wi-Fi is off or the name is hidden. */
    fun currentSsid(): String? {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            return null
        }
        val manager = context.getSystemService(WifiManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        val raw = runCatching { manager.connectionInfo?.ssid }.getOrNull() ?: return null
        val cleaned = raw.trim('"')
        return cleaned.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

    fun lastKnownLocation(): Location? {
        if (!granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    fun hasLocationPermission(): Boolean = granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "PlaceEngine"
        /** Two minutes: coarse enough to be invisible on battery, quick enough to feel live. */
        const val POLL_MS = 120_000L
    }
}
