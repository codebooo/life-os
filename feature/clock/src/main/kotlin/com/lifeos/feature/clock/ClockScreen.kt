package com.lifeos.feature.clock

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            PrimaryScrollableTabRow(selectedTabIndex = uiState.tab, edgePadding = 12.dp) {
                listOf("Faces", "World", "Map", "Convert", "Stopwatch", "Timer").forEachIndexed { index, label ->
                    Tab(
                        selected = uiState.tab == index,
                        onClick = { viewModel.onEvent(ClockUiEvent.SelectTab(index)) },
                        text = { Text(label, maxLines = 1) },
                    )
                }
            }
            when (uiState.tab) {
                0 -> FacesTab(uiState.face) { viewModel.onEvent(ClockUiEvent.SelectFace(it)) }
                1 -> WorldTab(uiState, viewModel::onEvent)
                2 -> TimeZoneMapTab(viewModel::onEvent)
                3 -> ConvertTab(uiState)
                4 -> StopwatchTab()
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
                        textAlign = TextAlign.Center,
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
                    headlineContent = { Text(friendlyZone(zoneId)) },
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

/**
 * Time-zone picker map (§Module 4): tap anywhere to add that longitude's
 * UTC-offset zone to the world-clock list. Native osmdroid — no Google.
 */
@Composable
private fun TimeZoneMapTab(onEvent: (ClockUiEvent) -> Unit) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Tap a place on the map — its time zone joins your world clocks.",
            style = MaterialTheme.typography.bodyMedium,
        )
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // osmdroid draws outside its bounds while scrolling — clip it.
                .clipToBounds(),
            factory = { ctx ->
                org.osmdroid.config.Configuration.getInstance().userAgentValue = "LifeOS/0.1 (personal)"
                org.osmdroid.config.Configuration.getInstance().osmdroidBasePath =
                    ctx.cacheDir.resolve("osmdroid")
                org.osmdroid.views.MapView(ctx).apply {
                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(2.5)
                    controller.setCenter(org.osmdroid.util.GeoPoint(20.0, 0.0))
                    overlays.add(
                        org.osmdroid.views.overlay.MapEventsOverlay(
                            object : org.osmdroid.events.MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: org.osmdroid.util.GeoPoint?): Boolean {
                                    p ?: return false
                                    onEvent(ClockUiEvent.AddZoneFromMap(p.latitude, p.longitude))
                                    return true
                                }

                                override fun longPressHelper(p: org.osmdroid.util.GeoPoint?) = false
                            },
                        ),
                    )
                }
            },
            onRelease = { it.onDetach() },
        )
    }
}

/** Time-zone converter (§Module 4): convert an HH:MM between any two zones. */
@Composable
private fun ConvertTab(uiState: ClockUiState) {
    val deviceZone = ZoneId.systemDefault().id
    val zones = remember(uiState.worldClocks) {
        (listOf(deviceZone, "UTC") + uiState.worldClocks).distinct()
    }
    var fromZone by remember { mutableStateOf(deviceZone) }
    var toZone by remember { mutableStateOf(zones.getOrElse(1) { "UTC" }) }
    var hourText by remember { mutableStateOf("12") }
    var minuteText by remember { mutableStateOf("00") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("From", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            zones.forEach { zone ->
                FilterChip(
                    selected = fromZone == zone,
                    onClick = { fromZone = zone },
                    label = { Text(friendlyZone(zone), maxLines = 1) },
                )
            }
        }
        Text("To", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            zones.forEach { zone ->
                FilterChip(
                    selected = toZone == zone,
                    onClick = { toZone = zone },
                    label = { Text(friendlyZone(zone), maxLines = 1) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = hourText,
                onValueChange = { hourText = it },
                label = { Text("Hour") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = minuteText,
                onValueChange = { minuteText = it },
                label = { Text("Min") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        val hour = hourText.toIntOrNull()?.coerceIn(0, 23)
        val minute = minuteText.toIntOrNull()?.coerceIn(0, 59)
        if (hour != null && minute != null) {
            val converted = java.time.LocalDate.now(ZoneId.of(fromZone))
                .atTime(hour, minute)
                .atZone(ZoneId.of(fromZone))
                .withZoneSameInstant(ZoneId.of(toZone))
            Text(
                "%02d:%02d in %s".format(hour, minute, friendlyZone(fromZone)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                converted.format(DateTimeFormatter.ofPattern("HH:mm")) +
                    " in ${friendlyZone(toZone)}" +
                    if (converted.toLocalDate() != java.time.LocalDate.now(ZoneId.of(fromZone))) " (next/prev day)" else "",
                style = MaterialTheme.typography.displaySmall,
            )
        }
    }
}

/** "Etc/GMT-2" reads as UTC+2 (the ids are sign-inverted); cities keep their name. */
internal fun friendlyZone(zoneId: String): String = when {
    zoneId == "Etc/GMT" || zoneId == "UTC" -> "UTC"
    zoneId.startsWith("Etc/GMT-") -> "UTC+${zoneId.removePrefix("Etc/GMT-")}"
    zoneId.startsWith("Etc/GMT+") -> "UTC-${zoneId.removePrefix("Etc/GMT+")}"
    else -> zoneId.substringAfterLast('/').replace('_', ' ')
}

@Composable
private fun StopwatchTab() {
    var running by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    val laps = remember { mutableStateOf(listOf<Long>()) }
    LaunchedEffect(running) {
        val startedAt = System.currentTimeMillis() - elapsedMs
        while (running) {
            elapsedMs = System.currentTimeMillis() - startedAt
            delay(37)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(formatStopwatch(elapsedMs), style = MaterialTheme.typography.displayMedium, fontFamily = FontFamily.Monospace)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { running = !running }) { Text(if (running) "Pause" else "Start") }
            OutlinedButton(
                onClick = {
                    // Running → record a lap; paused → reset everything.
                    if (running) {
                        laps.value = laps.value + elapsedMs
                    } else {
                        elapsedMs = 0
                        laps.value = emptyList()
                    }
                },
                enabled = running || elapsedMs > 0,
            ) { Text(if (running) "Lap" else "Reset") }
        }
        if (laps.value.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                val entries = laps.value.mapIndexed { i, total ->
                    val split = if (i == 0) total else total - laps.value[i - 1]
                    Triple(laps.value.size - i, split, total)
                }.reversed()
                items(entries) { (number, split, total) ->
                    ListItem(
                        headlineContent = { Text("Lap $number") },
                        trailingContent = {
                            Text(
                                "+${formatStopwatch(split)}   ${formatStopwatch(total)}",
                                fontFamily = FontFamily.Monospace,
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun formatStopwatch(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%02d:%02d.%02d".format(totalSeconds / 60, totalSeconds % 60, ms % 1000 / 10)
}

@Composable
private fun TimerTab() {
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(5) }
    var seconds by remember { mutableIntStateOf(0) }
    var remainingSeconds by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    var showAsSeconds by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && remainingSeconds > 0) {
            delay(1_000)
            remainingSeconds -= 1
        }
        if (remainingSeconds == 0L) running = false
    }

    val configured = hours * 3600L + minutes * 60L + seconds
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (running || remainingSeconds > 0) {
            // Centered time; the display toggle sits below so nothing skews.
            Text(
                if (showAsSeconds) "${remainingSeconds}s" else formatCountdown(remainingSeconds),
                style = MaterialTheme.typography.displayLarge,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { showAsSeconds = !showAsSeconds }) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                Text(if (showAsSeconds) "  Show mm:ss" else "  Show seconds")
            }
        } else {
            // Samsung-style three infinite wheels.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WheelPicker(range = 0..99, value = hours, onValue = { hours = it })
                WheelLabel("h")
                WheelPicker(range = 0..59, value = minutes, onValue = { minutes = it })
                WheelLabel("m")
                WheelPicker(range = 0..59, value = seconds, onValue = { seconds = it })
                WheelLabel("s")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1L, 5L, 10L, 25L).forEach { m ->
                FilterChip(
                    selected = !running && remainingSeconds == 0L && configured == m * 60,
                    onClick = {
                        hours = 0; minutes = m.toInt(); seconds = 0
                        remainingSeconds = 0
                        running = false
                    },
                    label = { Text("${m}m") },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    if (running) {
                        running = false
                    } else {
                        if (remainingSeconds == 0L) remainingSeconds = configured
                        if (remainingSeconds > 0) running = true
                    }
                },
                enabled = running || remainingSeconds > 0 || configured > 0,
            ) { Text(if (running) "Pause" else "Start") }
            OutlinedButton(
                onClick = {
                    running = false
                    remainingSeconds = 0
                },
                enabled = running || remainingSeconds > 0,
            ) { Text("Reset") }
        }
    }
}

private fun formatCountdown(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun WheelLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * A Samsung-style infinite scroll wheel: a virtually-unbounded list snapped so
 * the centered row is the selected value. Reports the value on settle.
 */
@Composable
private fun WheelPicker(
    range: IntRange,
    value: Int,
    onValue: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = range.count()
    val itemHeight = 48.dp
    // Center a huge virtual list so any direction can scroll "forever".
    val base = (Int.MAX_VALUE / 2) / count * count
    val startIndex = base + (value - range.first) - 1
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex.coerceAtLeast(0))
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                // Snapped: the centered row is firstVisibleItemIndex + 1.
                val centered = state.firstVisibleItemIndex + 1
                onValue(range.first + (centered % count))
            }
        }
    }

    Box(
        modifier = modifier
            .width(64.dp)
            .height(itemHeight * 3),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(10.dp),
                ),
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(Int.MAX_VALUE) { i ->
                val v = range.first + (i % count)
                val centered = i == state.firstVisibleItemIndex + 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "%02d".format(v),
                        fontSize = if (centered) 26.sp else 18.sp,
                        color = if (centered) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
