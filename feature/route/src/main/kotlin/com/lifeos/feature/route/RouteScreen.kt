package com.lifeos.feature.route

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.database.route.RouteDao
import com.lifeos.core.database.route.SavedPlaceEntity
import com.lifeos.core.designsystem.component.EmptyState
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
import javax.inject.Inject

@HiltViewModel
class RouteViewModel @Inject constructor(
    private val routeDao: RouteDao,
) : ViewModel() {

    val places = routeDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<SavedPlaceEntity>())

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    fun onName(value: String) { _name.value = value }
    fun onQuery(value: String) { _query.value = value }

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

/** Route planner (§Module 17): saved places, one-tap hand-off to the nav app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteRoute(viewModel: RouteViewModel = hiltViewModel()) {
    val places by viewModel.places.collectAsState()
    val name by viewModel.name.collectAsState()
    val query by viewModel.query.collectAsState()
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("Routes") }) }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
            OsmMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Text(
                "Long-press the map to navigate to that point.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (places.isEmpty()) {
                EmptyState(
                    title = "No saved places",
                    description = "One tap hands navigation to your maps app (OsmAnd, Organic Maps, …).",
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(places, key = { it.id }) { place ->
                        ListItem(
                            headlineContent = { Text(place.name) },
                            supportingContent = { Text(place.query) },
                            leadingContent = {
                                IconButton(onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("geo:0,0?q=" + Uri.encode(place.query)),
                                        ),
                                    )
                                }) {
                                    Icon(Icons.Filled.Navigation, contentDescription = "Navigate")
                                }
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
    }
}

/**
 * Native OpenStreetMap via osmdroid (§Module 17) — no WebView, no CDN, no
 * Google. Pan/zoom; long-press drops a marker and hands the point to the
 * user's navigation app through a plain geo: intent.
 */
@Composable
private fun OsmMap(modifier: Modifier = Modifier) {
    val context = LocalContext.current
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
                            override fun singleTapConfirmedHelper(p: GeoPoint?) = false

                            override fun longPressHelper(p: GeoPoint?): Boolean {
                                p ?: return false
                                overlays.removeAll { it is Marker }
                                overlays.add(
                                    Marker(this@apply).apply {
                                        position = p
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    },
                                )
                                invalidate()
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
        onRelease = { it.onDetach() },
    )
}
