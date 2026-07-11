package com.lifeos.feature.screentime

import android.content.ContentValues
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState
import kotlinx.coroutines.launch

/**
 * Screen Time (§Module Screen Time): mirrors Android's digital-wellbeing stats
 * into LifeOS and keeps them forever. Weekly bars, weekly average, scroll
 * through past weeks, per-app breakdown, unlocks/notifications, JSON export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTimeRoute(viewModel: ScreenTimeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Screen Time") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sync")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val json = viewModel.exportJson()
                            val resolver = context.contentResolver
                            val values = ContentValues().apply {
                                put(MediaStore.Downloads.DISPLAY_NAME, "lifeos-screentime.json")
                                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                            }
                            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                                resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                            }
                        }
                    }) { Icon(Icons.Filled.Download, contentDescription = "Export JSON") }
                },
            )
        },
    ) { innerPadding ->
        if (!state.hasPermission) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "LifeOS needs Usage Access to read digital-wellbeing data. It stays on-device and " +
                        "is kept forever — even after Samsung deletes its own copy.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                    Text("Grant Usage Access")
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.previousWeek() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
                    }
                    Text(state.weekLabel, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { viewModel.nextWeek() }, enabled = state.weekOffset > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Daily average", formatDuration(state.dailyAverageMs), Modifier.weight(1f))
                    StatCard("Week total", formatDuration(state.weekTotalMs), Modifier.weight(1f))
                }
            }
            item { WeekBars(state.days) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Unlocks", state.days.sumOf { it.unlocks }.toString(), Modifier.weight(1f))
                    StatCard("Notifications", state.days.sumOf { it.notifications }.toString(), Modifier.weight(1f))
                }
            }
            if (state.topApps.isEmpty()) {
                item {
                    EmptyState(
                        title = "No data for this week",
                        description = "Sync pulls the last several weeks. Older weeks you scroll to are kept forever.",
                    )
                }
            } else {
                item { Text("Most used apps", style = MaterialTheme.typography.titleMedium) }
                items(state.topApps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(app.label, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(formatDuration(app.ms), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text(
                    "${state.totalDaysStored} day(s) stored permanently in LifeOS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeekBars(days: List<DayBar>) {
    val max = (days.maxOfOrNull { it.totalMs } ?: 0L).coerceAtLeast(1L)
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().height(180.dp).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEach { day ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(formatShort(day.totalMs), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    val fraction = (day.totalMs.toFloat() / max).coerceIn(0.02f, 1f)
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().height((120 * fraction).dp),
                        ) {}
                    }
                    Text(day.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

private fun formatShort(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60}h" else "${minutes}m"
}
