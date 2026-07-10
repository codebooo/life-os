package com.lifeos.feature.route

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.database.route.RouteDao
import com.lifeos.core.database.route.SavedPlaceEntity
import com.lifeos.feature.route.data.OsrmClient
import com.lifeos.feature.route.data.RoutePlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import javax.inject.Inject

@HiltViewModel
class RouteViewModel @Inject constructor(
    private val routeDao: RouteDao,
    private val osrm: OsrmClient,
) : ViewModel() {

    val places = routeDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<SavedPlaceEntity>())

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _profile = MutableStateFlow("cycling")
    val profile = _profile.asStateFlow()
    private val _start = MutableStateFlow<Pair<Double, Double>?>(null)
    val start = _start.asStateFlow()
    private val _end = MutableStateFlow<Pair<Double, Double>?>(null)
    val end = _end.asStateFlow()
    private val _plan = MutableStateFlow<RoutePlan?>(null)
    val plan = _plan.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun onName(value: String) { _name.value = value }
    fun onQuery(value: String) { _query.value = value }

    fun setProfile(value: String) {
        _profile.value = value
        if (_start.value != null && _end.value != null) computeRoute()
    }

    /** First tap sets the start, second the destination, third restarts. */
    fun onMapTap(lat: Double, lon: Double) {
        when {
            _start.value == null -> { _start.value = lat to lon; _plan.value = null }
            _end.value == null -> { _end.value = lat to lon; computeRoute() }
            else -> { _start.value = lat to lon; _end.value = null; _plan.value = null }
        }
    }

    fun clearRoute() {
        _start.value = null; _end.value = null; _plan.value = null; _error.value = null
    }

    private fun computeRoute() {
        val from = _start.value ?: return
        val to = _end.value ?: return
        viewModelScope.launch {
            _error.value = null
            runCatching { osrm.route(_profile.value, from.first, from.second, to.first, to.second) }
                .onSuccess { _plan.value = it }
                .onFailure { _error.value = it.message ?: "Routing failed" }
        }
    }

    fun add() {
        val name = _name.value.trim()
        val query = _query.value.trim()
        if (name.isEmpty() || query.isEmpty()) return
        viewModelScope.launch {
            routeDao.insert(SavedPlaceEntity(name = name, query = query, createdAt = System.currentTimeMillis()))
            _name.value = ""
            _query.value = ""
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { routeDao.delete(id) }
    }
}

/**
 * Route planner (§Module 17): tap the map twice (start → destination), pick
 * car/bike/foot, and the route draws with distance + timing via OSRM — the
 * same OpenStreetMap stack as the tiles. Saved places keep one-tap hand-off
 * to the nav app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteRoute(viewModel: RouteViewModel = hiltViewModel()) {
    val places by viewModel.places.collectAsState()
    val name by viewModel.name.collectAsState()
    val query by viewModel.query.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val start by viewModel.start.collectAsState()
    val end by viewModel.end.collectAsState()
    val plan by viewModel.plan.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("Routes") }) }) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = profile == "driving",
                        onClick = { viewModel.setProfile("driving") },
                        label = { Text("Car") },
                        leadingIcon = { Icon(Icons.Filled.DirectionsCar, null) },
                    )
                    FilterChip(
                        selected = profile == "cycling",
                        onClick = { viewModel.setProfile("cycling") },
                        label = { Text("Bike") },
                        leadingIcon = { Icon(Icons.Filled.DirectionsBike, null) },
                    )
                    FilterChip(
                        selected = profile == "walking",
                        onClick = { viewModel.setProfile("walking") },
                        label = { Text("Walk") },
                        leadingIcon = { Icon(Icons.Filled.DirectionsWalk, null) },
                    )
                }
            }
            item {
                RoutingMap(
                    start = start,
                    end = end,
                    plan = plan,
                    onTap = viewModel::onMapTap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clipToBounds(),
                )
            }
            item {
                val hint = when {
                    start == null -> "Tap the map to set the start."
                    end == null -> "Tap again for the destination."
                    else -> null
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    plan?.let { p ->
                        Card {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(formatDistance(p.distanceMeters), style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        "${formatDuration(p.durationSeconds)} by ${profileLabel(profile)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(onClick = viewModel::clearRoute) { Text("Clear") }
                            }
                        }
                    }
                    hint?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = viewModel::onName,
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::onQuery,
                        label = { Text("Address") },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f),
                    )
                    Button(onClick = viewModel::add) { Text("Save") }
                }
            }
            items(places, key = { it.id }) { place ->
                ListItem(
                    headlineContent = { Text(place.name) },
                    supportingContent = { Text(place.query) },
                    leadingContent = {
                        IconButton(onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place.query))),
                            )
                        }) { Icon(Icons.Filled.Navigation, contentDescription = "Navigate") }
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.delete(place.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    },
                )
            }
        }
    }
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) "%.1f km".format(meters / 1000) else "${meters.toInt()} m"

private fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60).toInt()
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

private fun profileLabel(profile: String) = when (profile) {
    "driving" -> "car"
    "walking" -> "foot"
    else -> "bike"
}

/** osmdroid map with start/end markers + the OSRM polyline. Single tap plans. */
@Composable
private fun RoutingMap(
    start: Pair<Double, Double>?,
    end: Pair<Double, Double>?,
    plan: RoutePlan?,
    onTap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Configuration.getInstance().userAgentValue = "LifeOS/0.1 (personal)"
            Configuration.getInstance().osmdroidBasePath = ctx.cacheDir.resolve("osmdroid")
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(6.0)
                controller.setCenter(GeoPoint(51.1657, 10.4515))
                overlays.add(
                    MapEventsOverlay(
                        object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                p ?: return false
                                onTap(p.latitude, p.longitude)
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint?): Boolean {
                                p ?: return false
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("geo:${p.latitude},${p.longitude}?q=${p.latitude},${p.longitude}"),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                                return true
                            }
                        },
                    ),
                )
            }
        },
        update = { map ->
            map.overlays.removeAll { it is Marker || it is Polyline }
            fun marker(point: Pair<Double, Double>, label: String) {
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(point.first, point.second)
                        title = label
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    },
                )
            }
            start?.let { marker(it, "Start") }
            end?.let { marker(it, "Destination") }
            plan?.let { p ->
                map.overlays.add(
                    Polyline(map).apply {
                        setPoints(p.points.map { (lat, lon) -> GeoPoint(lat, lon) })
                        outlinePaint.strokeWidth = 10f
                    },
                )
                if (p.points.isNotEmpty()) {
                    map.zoomToBoundingBox(
                        org.osmdroid.util.BoundingBox.fromGeoPoints(
                            p.points.map { (lat, lon) -> GeoPoint(lat, lon) },
                        ).increaseByScale(1.3f),
                        false,
                    )
                }
            }
            map.invalidate()
        },
        onRelease = { it.onDetach() },
    )
}
