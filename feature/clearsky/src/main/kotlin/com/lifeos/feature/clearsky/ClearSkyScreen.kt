package com.lifeos.feature.clearsky

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.feature.clearsky.data.SkyDay
import com.lifeos.feature.clearsky.data.SkyForecast
import com.lifeos.feature.clearsky.data.SkyPlace
import com.lifeos.feature.clearsky.data.SkyRating
import com.lifeos.feature.clearsky.data.SkyRow

/**
 * Clear Sky Map (§Module Clear Sky Map): the full clearoutside.com forecast for
 * any spot - hourly go/no-go ratings, cloud layers at three altitudes, seeing
 * conditions, sun and moon ephemeris and the estimated sky quality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearSkyRoute(viewModel: ClearSkyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) context.lastKnownLocation()?.let { viewModel.useDeviceLocation(it.first, it.second) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Clear Sky Map") },
                actions = {
                    IconButton(
                        onClick = {
                            val fix = context.lastKnownLocation()
                            if (fix != null) {
                                viewModel.useDeviceLocation(fix.first, fix.second)
                            } else {
                                locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                    ) { Icon(Icons.Filled.MyLocation, contentDescription = "Use my location") }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Place or \"lat, lon\"") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { if (!viewModel.useTypedCoordinates()) viewModel.search() },
                    ),
                    trailingIcon = {
                        if (state.searching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            TextButton(
                                onClick = { if (!viewModel.useTypedCoordinates()) viewModel.search() },
                            ) { Text("Find") }
                        }
                    },
                )
            }

            if (state.loading && state.forecast != null) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (state.results.isNotEmpty()) {
                items(state.results) { result ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { viewModel.selectPlace(result) },
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                        ) { Text(result.name, textAlign = TextAlign.Start) }
                    }
                }
            }

            if (state.places.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.places) { place ->
                            InputChip(
                                selected = state.place?.name == place.name,
                                onClick = { viewModel.selectPlace(place) },
                                label = { Text(place.name.take(28)) },
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.removePlace(place) }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Remove spot",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkyView.entries.forEach { view ->
                        FilterChip(
                            selected = state.view == view,
                            onClick = { viewModel.selectView(view) },
                            label = { Text(view.label) },
                        )
                    }
                    FilterChip(
                        selected = state.metric,
                        onClick = viewModel::toggleMetric,
                        label = { Text(if (state.metric) "Metric" else "Imperial") },
                    )
                }
            }

            val forecast = state.forecast
            when {
                state.loading && forecast == null -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                forecast == null -> item {
                    EmptyState(
                        title = "Pick an observing spot",
                        description = "Search for a place, paste coordinates, or use your current location. " +
                            "Forecasts come straight from clearoutside.com, no account needed.",
                    )
                }

                else -> {
                    item { QualityCard(forecast, onSave = viewModel::savePlace) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(forecast.days.size) { index ->
                                val day = forecast.days[index]
                                FilterChip(
                                    selected = state.selectedDay == index,
                                    onClick = { viewModel.selectDay(index) },
                                    label = { Text("${day.weekday.take(3)} ${day.dayOfMonth}") },
                                )
                            }
                        }
                    }
                    forecast.days.getOrNull(state.selectedDay)?.let { day ->
                        item { RatingStrip(day) }
                        item { EphemerisCard(day) }
                        item { DetailTable(day, metric = state.metric) }
                    }
                    item {
                        Text(
                            "Generated ${forecast.generated} - ${forecast.range} - timezone ${forecast.timezone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityCard(forecast: SkyForecast, onSave: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(forecast.locationName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${forecast.latitude}, ${forecast.longitude}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (forecast.skyQualityMagnitude.isNotBlank()) {
                Text("Sky quality ${forecast.skyQualityMagnitude} mag - Bortle class ${forecast.bortleClass}")
            }
            if (forecast.brightness.isNotBlank()) {
                Text(
                    "Brightness ${forecast.brightness} mcd/m2 - artificial ${forecast.artificialBrightness} ucd/m2",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssistChip(
                onClick = onSave,
                label = { Text("Save this spot") },
                leadingIcon = { Icon(Icons.Filled.StarOutline, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun RatingStrip(day: SkyDay) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                "Hourly conditions",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                day.hours.forEach { hour ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(CELL_WIDTH),
                    ) {
                        Text(hour.hour, style = MaterialTheme.typography.labelSmall)
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(width = 26.dp, height = 26.dp)
                                .background(hour.rating.color(), RoundedCornerShape(6.dp)),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Legend("Good", SkyRating.GOOD)
                Legend("OK", SkyRating.OK)
                Legend("Bad", SkyRating.BAD)
            }
        }
    }
}

@Composable
private fun Legend(label: String, rating: SkyRating) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(10.dp).background(rating.color(), RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EphemerisCard(day: SkyDay) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${day.weekday} ${day.dayOfMonth}", style = MaterialTheme.typography.titleSmall)
            EphemerisLine("Sun", "rise ${day.sunRise}, set ${day.sunSet}, transit ${day.sunTransit}")
            EphemerisLine("Moon", "${day.moonPhase} ${day.moonIllumination}, rise ${day.moonRise}, set ${day.moonSet}")
            EphemerisLine("Civil dark", day.civilDark)
            EphemerisLine("Nautical dark", day.nauticalDark)
            EphemerisLine("Astro dark", day.astroDark)
        }
    }
}

@Composable
private fun EphemerisLine(label: String, value: String) {
    if (value.isBlank()) return
    Row {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/** Every clearoutside detail row, scrolled sideways in step with the hours. */
@Composable
private fun DetailTable(day: SkyDay, metric: Boolean) {
    val scroll = rememberScrollState()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                "Details",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            day.rows.forEach { row ->
                val converted = row.converted(metric)
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        converted.label,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(scroll).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        converted.values.forEach { value ->
                            Text(
                                value.ifBlank { "-" },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(CELL_WIDTH).padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** clearoutside serves miles and mph; convert when the user wants metric. */
private fun SkyRow.converted(metric: Boolean): SkyRow {
    if (!metric) return this
    return when {
        label.contains("(miles)") -> SkyRow(
            label = label.replace("(miles)", "(km)"),
            values = values.map { it.scaled(1.609344) },
            details = details,
        )

        label.contains("(mph)") -> SkyRow(
            label = label.replace("(mph)", "(km/h)"),
            values = values.map { it.scaled(1.609344) },
            details = details,
        )

        else -> this
    }
}

private fun String.scaled(factor: Double): String {
    val number = trim().toDoubleOrNull() ?: return this
    val result = number * factor
    return if (result >= 10) result.toInt().toString() else String.format(java.util.Locale.US, "%.1f", result)
}

@Composable
private fun SkyRating.color(): Color = when (this) {
    SkyRating.GOOD -> Color(0xFF2E7D32)
    SkyRating.OK -> Color(0xFFF9A825)
    SkyRating.BAD -> Color(0xFFC62828)
    SkyRating.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
}

/** Last known fix from the OS providers - no Google Play services involved. */
private fun Context.lastKnownLocation(): Pair<Double, Double>? {
    val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!granted) return null
    val manager = getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
    return providers.asSequence()
        .mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { it.time }
        ?.let { it.latitude to it.longitude }
}

private val CELL_WIDTH = 34.dp
