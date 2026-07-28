package com.lifeos.feature.screentime

import android.content.ContentValues
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.core.designsystem.component.FadeThrough
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

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.exportMessage) {
        state.exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissExportMessage()
        }
    }

    if (state.showExportDialog) {
        ExportDialog(
            onDismiss = viewModel::dismissExportDialog,
            onExport = { format, weekOnly ->
                scope.launch {
                    val saved = runCatching {
                        val (name, body) = viewModel.buildExport(format, weekOnly)
                        val resolver = context.contentResolver
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, name)
                            put(
                                MediaStore.Downloads.MIME_TYPE,
                                if (name.endsWith(".json")) "application/json" else "text/csv",
                            )
                        }
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: error("MediaStore rejected the file")
                        resolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
                            ?: error("Could not open the file")
                        name
                    }.getOrNull()
                    viewModel.onExported(saved)
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Screen Time") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sync")
                    }
                    IconButton(onClick = { viewModel.showExportDialog() }) {
                        Icon(Icons.Filled.Download, contentDescription = "Export data")
                    }
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
            val selected = state.selectedDate?.let { date -> state.days.firstOrNull { it.date == date } }
            item {
                // Week totals and the tapped day's numbers cross-fade into each other.
                FadeThrough(targetState = state.selectedDate, label = "screentime-stats") { date ->
                    val day = date?.let { value -> state.days.firstOrNull { it.date == value } }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (day != null) {
                            StatCard("Screen time", formatDuration(day.totalMs), Modifier.weight(1f))
                            StatCard("Unlocks", day.unlocks.toString(), Modifier.weight(1f))
                        } else {
                            StatCard("Daily average", formatDuration(state.dailyAverageMs), Modifier.weight(1f))
                            StatCard("Week total", formatDuration(state.weekTotalMs), Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                WeekBars(
                    days = state.days,
                    selectedDate = state.selectedDate,
                    onDayClick = { date -> viewModel.selectDay(date) },
                )
            }
            item {
                FadeThrough(targetState = selected != null, label = "screentime-day-header") { hasSelection ->
                if (hasSelection) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(state.selectedLabel, style = MaterialTheme.typography.titleMedium)
                        androidx.compose.material3.TextButton(onClick = { viewModel.clearSelection() }) {
                            Text("Show week")
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Unlocks", state.days.sumOf { it.unlocks }.toString(), Modifier.weight(1f))
                        StatCard("Notifications", state.days.sumOf { it.notifications }.toString(), Modifier.weight(1f))
                    }
                }
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
                item {
                    Text(
                        if (state.selectedDate != null) "Apps used that day" else "Most used apps",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
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
                    "Tap a bar to focus that day · ${state.totalDaysStored} day(s) stored permanently in LifeOS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Export options (item 5): a dialog, not a slide-up, with format + range. */
@Composable
private fun ExportDialog(onDismiss: () -> Unit, onExport: (ExportFormat, Boolean) -> Unit) {
    var format by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ExportFormat.JSON) }
    var weekOnly by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export screen time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Format", style = MaterialTheme.typography.labelLarge)
                listOf(
                    ExportFormat.JSON to "JSON — everything, per day and per app",
                    ExportFormat.CSV_DAYS to "CSV — one row per day",
                    ExportFormat.CSV_APPS to "CSV — one row per app per day",
                ).forEach { (option, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { format = option },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = format == option,
                            onClick = { format = option },
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                androidx.compose.material3.HorizontalDivider()
                Text("Range", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { weekOnly = false },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(selected = !weekOnly, onClick = { weekOnly = false })
                    Text("Everything stored", style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { weekOnly = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(selected = weekOnly, onClick = { weekOnly = true })
                    Text("Shown week only", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "Saved to your Downloads folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = { onExport(format, weekOnly) }) { Text("Export") } },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
private fun WeekBars(days: List<DayBar>, selectedDate: String?, onDayClick: (String) -> Unit) {
    val max = (days.maxOfOrNull { it.totalMs } ?: 0L).coerceAtLeast(1L)
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().height(180.dp).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEach { day ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onDayClick(day.date) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(formatShort(day.totalMs), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    val fraction = (day.totalMs.toFloat() / max).coerceIn(0.02f, 1f)
                    // iOS-style focus: the picked bar keeps full colour, the rest fade.
                    val dimmed = selectedDate != null && selectedDate != day.date
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                        Surface(
                            color = if (dimmed) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().height((120 * fraction).dp),
                        ) {}
                    }
                    Text(
                        day.label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = if (selectedDate == day.date) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
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
