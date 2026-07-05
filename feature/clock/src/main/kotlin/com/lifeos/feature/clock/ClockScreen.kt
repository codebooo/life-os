package com.lifeos.feature.clock

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Clock module (§Module 4): faces, world clock, stopwatch, timer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockRoute(viewModel: ClockViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(ClockUiEvent.DismissMessage)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Clock") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = uiState.tab) {
                listOf("Faces", "World", "Stopwatch", "Timer").forEachIndexed { index, label ->
                    Tab(
                        selected = uiState.tab == index,
                        onClick = { viewModel.onEvent(ClockUiEvent.SelectTab(index)) },
                        text = { Text(label) },
                    )
                }
            }
            when (uiState.tab) {
                0 -> FacesTab(uiState.face) { viewModel.onEvent(ClockUiEvent.SelectFace(it)) }
                1 -> WorldTab(uiState, viewModel::onEvent)
                2 -> StopwatchTab()
                else -> TimerTab()
            }
        }
    }
}

/** Ticks once a second while visible. */
@Composable
private fun rememberNow(): LocalTime {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }
    return now
}

@Composable
private fun FacesTab(face: Int, onFace: (Int) -> Unit) {
    val now = rememberNow()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Digital", "Analog", "Word").forEachIndexed { index, label ->
                FilterChip(selected = face == index, onClick = { onFace(index) }, label = { Text(label) })
            }
        }
        AnimatedContent(targetState = face, label = "face") { selected ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (selected) {
                    0 -> Text(
                        now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        style = MaterialTheme.typography.displayLarge,
                    )
                    1 -> AnalogFace(now)
                    else -> Text(
                        wordClock(now),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalogFace(now: LocalTime) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val secondColor = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = Modifier.size(260.dp)) {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = outline, radius = radius, style = Stroke(width = 6f))
        repeat(12) { hour ->
            val angle = Math.toRadians(hour * 30.0 - 90)
            val start = center + Offset(
                (cos(angle) * radius * 0.88f).toFloat(),
                (sin(angle) * radius * 0.88f).toFloat(),
            )
            val end = center + Offset(
                (cos(angle) * radius * 0.96f).toFloat(),
                (sin(angle) * radius * 0.96f).toFloat(),
            )
            drawLine(outline, start, end, strokeWidth = 4f)
        }
        fun hand(fraction: Double, length: Float, width: Float, color: Color) {
            val angle = Math.toRadians(fraction * 360 - 90)
            drawLine(
                color = color,
                start = center,
                end = center + Offset(
                    (cos(angle) * radius * length).toFloat(),
                    (sin(angle) * radius * length).toFloat(),
                ),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
        hand((now.hour % 12 + now.minute / 60.0) / 12.0, 0.5f, 12f, primary)
        hand((now.minute + now.second / 60.0) / 60.0, 0.72f, 8f, primary)
        hand(now.second / 60.0, 0.82f, 3f, secondColor)
        drawCircle(color = primary, radius = 10f, center = center)
    }
}

private val NUMBER_WORDS = listOf(
    "twelve", "one", "two", "three", "four", "five", "six",
    "seven", "eight", "nine", "ten", "eleven",
)

internal fun wordClock(now: LocalTime): String {
    val hourWord = NUMBER_WORDS[now.hour % 12]
    val nextHourWord = NUMBER_WORDS[(now.hour + 1) % 12]
    return when (now.minute) {
        in 0..4 -> "$hourWord o'clock"
        in 5..9 -> "five past $hourWord"
        in 10..14 -> "ten past $hourWord"
        in 15..19 -> "quarter past $hourWord"
        in 20..29 -> "twenty past $hourWord"
        in 30..34 -> "half past $hourWord"
        in 35..44 -> "twenty to $nextHourWord"
        in 45..49 -> "quarter to $nextHourWord"
        in 50..54 -> "ten to $nextHourWord"
        else -> "five to $nextHourWord"
    }
}

@Composable
private fun WorldTab(uiState: ClockUiState, onEvent: (ClockUiEvent) -> Unit) {
    rememberNow() // drive per-second recomposition of the zone rows
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.zoneDraft,
                onValueChange = { onEvent(ClockUiEvent.ZoneDraftChanged(it)) },
                label = { Text("City or zone (e.g. Tokyo)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onEvent(ClockUiEvent.AddZone) },
                enabled = uiState.zoneDraft.isNotBlank(),
            ) { Text("Add") }
        }
        if (uiState.worldClocks.isEmpty()) {
            EmptyState(title = "No world clocks", description = "Add a city to compare times at a glance.")
            return
        }
        LazyColumn {
            items(uiState.worldClocks, key = { it }) { zoneId ->
                val time = ZonedDateTime.now(ZoneId.of(zoneId))
                ListItem(
                    headlineContent = { Text(zoneId.substringAfterLast('/').replace('_', ' ')) },
                    supportingContent = { Text(zoneId) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                time.format(DateTimeFormatter.ofPattern("HH:mm")),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            IconButton(onClick = { onEvent(ClockUiEvent.RemoveZone(zoneId)) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove")
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StopwatchTab() {
    var running by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        val startedAt = System.currentTimeMillis() - elapsedMs
        while (running) {
            elapsedMs = System.currentTimeMillis() - startedAt
            delay(50)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val totalSeconds = elapsedMs / 1000
        Text(
            "%02d:%02d.%01d".format(totalSeconds / 60, totalSeconds % 60, elapsedMs % 1000 / 100),
            style = MaterialTheme.typography.displayLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { running = !running }) { Text(if (running) "Pause" else "Start") }
            Button(
                onClick = {
                    running = false
                    elapsedMs = 0
                },
                enabled = elapsedMs > 0,
            ) { Text("Reset") }
        }
    }
}

@Composable
private fun TimerTab() {
    var totalSeconds by remember { mutableLongStateOf(0L) }
    var remaining by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
        if (remaining == 0L) running = false
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            "%02d:%02d".format(remaining / 60, remaining % 60),
            style = MaterialTheme.typography.displayLarge,
        )
        if (remaining == 0L && totalSeconds > 0 && !running) {
            Text("Time's up!", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.tertiary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1L, 5L, 10L, 25L).forEach { minutes ->
                FilterChip(
                    selected = totalSeconds == minutes * 60,
                    onClick = {
                        totalSeconds = minutes * 60
                        remaining = totalSeconds
                        running = false
                    },
                    label = { Text("${minutes}m") },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { running = !running }, enabled = remaining > 0) {
                Text(if (running) "Pause" else "Start")
            }
            Button(
                onClick = {
                    running = false
                    remaining = totalSeconds
                },
                enabled = totalSeconds > 0,
            ) { Text("Reset") }
        }
    }
}
