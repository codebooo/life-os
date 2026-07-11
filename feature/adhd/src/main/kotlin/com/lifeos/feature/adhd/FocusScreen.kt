package com.lifeos.feature.adhd

import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.feature.adhd.overlay.OverwhelmOverlayService
import com.lifeos.feature.adhd.overlay.TimerOverlayService
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** ADHD tools (§Module 5): visual focus timer, streaks, overwhelm overlay. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusRoute(viewModel: FocusViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Focus") }) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = uiState.tab) {
                listOf("Timer", "Streaks", "Overwhelm").forEachIndexed { index, label ->
                    Tab(
                        selected = uiState.tab == index,
                        onClick = { viewModel.onEvent(FocusUiEvent.SelectTab(index)) },
                        text = { Text(label) },
                    )
                }
            }
            when (uiState.tab) {
                0 -> TimerTab(onFinished = { minutes, completed ->
                    viewModel.onEvent(FocusUiEvent.SessionFinished(minutes, completed))
                })
                1 -> StreaksTab(uiState)
                else -> OverwhelmTab()
            }
        }
    }
}

@Composable
private fun TimerTab(onFinished: (minutes: Int, completed: Boolean) -> Unit) {
    val context = LocalContext.current
    // Total duration in seconds and the running remaining seconds are kept apart
    // so a custom time survives a reset. Default 25 min.
    var totalSeconds by remember { mutableIntStateOf(25 * 60) }
    var remainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var running by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var overlayOn by remember { mutableStateOf(false) }

    // Absolute deadline drives both the on-screen ring AND the OS overlay so they
    // never drift apart while the overlay is up.
    fun startOverlayIfOn() {
        if (overlayOn) {
            TimerOverlayService.show(
                context,
                android.os.SystemClock.elapsedRealtime() + remainingSeconds * 1000L,
                totalSeconds * 1000L,
            )
        }
    }

    LaunchedEffect(running) {
        while (running && remainingSeconds > 0) {
            delay(1_000)
            remainingSeconds -= 1
        }
        if (running && remainingSeconds == 0) {
            running = false
            onFinished(totalSeconds / 60, true)
            TimerOverlayService.hide(context)
            overlayOn = false
            val vibrator = (context.getSystemService(VibratorManager::class.java)).defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 400), -1))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val progress = if (totalSeconds == 0) 0f else remainingSeconds / (totalSeconds.toFloat())
            val track = MaterialTheme.colorScheme.surfaceVariant
            val bar = MaterialTheme.colorScheme.primary
            Canvas(
                modifier = Modifier
                    .size(240.dp)
                    // Tap inside the ring to set a custom time.
                    .pointerInput(Unit) { detectTapGestures { showCustom = true } },
            ) {
                drawArc(track, -90f, 360f, false, style = Stroke(width = 28f, cap = StrokeCap.Round))
                drawArc(bar, -90f, 360f * progress, false, style = Stroke(width = 28f, cap = StrokeCap.Round))
            }
            Text(
                formatClock(remainingSeconds),
                style = MaterialTheme.typography.displayMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 15, 25, 45).forEach { preset ->
                FilterChip(
                    selected = !running && totalSeconds == preset * 60,
                    onClick = {
                        totalSeconds = preset * 60
                        remainingSeconds = preset * 60
                        running = false
                    },
                    label = { Text("${preset}m") },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    running = !running
                    if (running) startOverlayIfOn()
                },
                enabled = remainingSeconds > 0,
            ) { Text(if (running) "Pause" else "Start") }
            OutlinedButton(
                onClick = {
                    if (running || remainingSeconds < totalSeconds) onFinished(totalSeconds / 60, false)
                    running = false
                    remainingSeconds = totalSeconds
                    TimerOverlayService.hide(context)
                    overlayOn = false
                },
            ) { Text("Reset") }
            OutlinedButton(
                onClick = {
                    if (!overlayOn && !Settings.canDrawOverlays(context)) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                        return@OutlinedButton
                    }
                    overlayOn = !overlayOn
                    if (overlayOn) startOverlayIfOn() else TimerOverlayService.hide(context)
                },
            ) { Text(if (overlayOn) "Hide overlay" else "Overlay") }
        }
        Text(
            "Tap inside the ring to set a custom time. Overlay floats the ring over any app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showCustom) {
        CustomTimeDialog(
            initialSeconds = totalSeconds,
            onDismiss = { showCustom = false },
            onConfirm = { seconds ->
                totalSeconds = seconds.coerceAtLeast(1)
                remainingSeconds = totalSeconds
                running = false
                showCustom = false
            },
        )
    }
}

private fun formatClock(seconds: Int): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    else "%02d:%02d".format(seconds / 60, seconds % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomTimeDialog(
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var hours by remember { mutableStateOf((initialSeconds / 3600).toString().takeIf { it != "0" } ?: "") }
    var minutes by remember { mutableStateOf(((initialSeconds % 3600) / 60).toString()) }
    var seconds by remember { mutableStateOf((initialSeconds % 60).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set timer") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TimeField(hours, "HH") { hours = it }
                Text(":")
                TimeField(minutes, "MM") { minutes = it }
                Text(":")
                TimeField(seconds, "SS") { seconds = it }
            }
        },
        confirmButton = {
            Button(onClick = {
                val h = hours.toIntOrNull() ?: 0
                val m = minutes.toIntOrNull() ?: 0
                val s = seconds.toIntOrNull() ?: 0
                onConfirm(h * 3600 + m * 60 + s)
            }) { Text("Set") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TimeField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() }.take(2)) },
        placeholder = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(76.dp),
    )
}

@Composable
private fun StreaksTab(uiState: FocusUiState) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("${uiState.streakDays}", style = MaterialTheme.typography.displaySmall)
                    Text("day streak", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("${uiState.completedCount}", style = MaterialTheme.typography.displaySmall)
                    Text("sessions done", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (uiState.sessions.isEmpty()) {
            EmptyState(title = "No sessions yet", description = "Finish a focus timer to start your streak.")
            return
        }
        LazyColumn {
            items(uiState.sessions, key = { it.id }) { session ->
                ListItem(
                    headlineContent = { Text("${session.minutes} min ${if (session.completed) "· done" else "· abandoned"}") },
                    supportingContent = {
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.startedAt)))
                    },
                )
            }
        }
    }
}

@Composable
private fun OverwhelmTab() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Overwhelm mode floats a single \"What's next?\" card over everything — one task, nothing else. " +
                "It needs the \"Display over other apps\" permission once.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = {
                if (Settings.canDrawOverlays(context)) {
                    OverwhelmOverlayService.show(context)
                } else {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (Settings.canDrawOverlays(context)) "Show what's next" else "Grant overlay permission")
        }
    }
}
