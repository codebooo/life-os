package com.lifeos.feature.adhd

import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.core.designsystem.component.FadeThrough
import com.lifeos.feature.adhd.data.FocusTimerController
import com.lifeos.feature.adhd.overlay.OverwhelmOverlayService
import com.lifeos.feature.adhd.overlay.TimerOverlayService
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

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
            FadeThrough(targetState = uiState.tab, label = "focus-tab") { tab ->
                when (tab) {
                    0 -> TimerTab(viewModel.timerController)
                    1 -> StreaksTab(uiState)
                    else -> OverwhelmTab()
                }
            }
        }
    }
}

/**
 * Focus timer (§Module 5). All state lives in [FocusTimerController], so the
 * countdown keeps running across tabs, Home and app switches. Tap the time in
 * the middle of the ring to edit it right there - no dialog.
 */
@Composable
private fun TimerTab(controller: FocusTimerController) {
    val context = LocalContext.current
    val timer by controller.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun commit() {
        parseClock(draft)?.let { controller.setTotal(it) }
        editing = false
        keyboard?.hide()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val progress = if (timer.totalSeconds == 0) {
                0f
            } else {
                timer.remainingSeconds / timer.totalSeconds.toFloat()
            }
            val track = MaterialTheme.colorScheme.surfaceVariant
            val bar = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.size(240.dp)) {
                drawArc(track, -90f, 360f, false, style = Stroke(width = 28f, cap = StrokeCap.Round))
                drawArc(bar, -90f, 360f * progress, false, style = Stroke(width = 28f, cap = StrokeCap.Round))
            }
            if (editing) {
                // Editing happens in place: the clock in the middle becomes the field.
                BasicTextField(
                    value = draft,
                    onValueChange = { input -> draft = input.filter { it.isDigit() || it == ':' }.take(8) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.displayMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier
                        .width(180.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { if (!it.isFocused && editing) commit() },
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            } else {
                Text(
                    formatClock(timer.remainingSeconds),
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.pointerInput(timer.remainingSeconds) {
                        detectTapGestures {
                            draft = formatClock(timer.remainingSeconds)
                            editing = true
                        }
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 15, 25, 45).forEach { preset ->
                FilterChip(
                    selected = !timer.running && timer.totalSeconds == preset * 60,
                    onClick = { controller.setTotal(preset * 60) },
                    label = { Text("${preset}m") },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { controller.toggle() },
                enabled = timer.remainingSeconds > 0,
            ) { Text(if (timer.running) "Pause" else "Start") }
            OutlinedButton(onClick = { controller.reset() }) { Text("Reset") }
            OutlinedButton(
                onClick = {
                    if (!timer.overlayVisible && !Settings.canDrawOverlays(context)) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                        return@OutlinedButton
                    }
                    controller.setOverlayVisible(!timer.overlayVisible)
                },
            ) { Text(if (timer.overlayVisible) "Hide overlay" else "Overlay") }
        }
        Text(
            "Tap the time to type a new one. The timer keeps running when you leave this tab or the app. " +
                "The overlay floats the ring over anything - drag it around, pinch to resize.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatClock(seconds: Int): String =
    if (seconds >= 3600) {
        "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    } else {
        "%02d:%02d".format(seconds / 60, seconds % 60)
    }

/** Accepts "25", "25:00" and "1:05:00". */
private fun parseClock(text: String): Int? {
    val parts = text.split(':').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val numbers = parts.map { it.toIntOrNull() ?: return null }
    val seconds = when (numbers.size) {
        1 -> numbers[0] * 60
        2 -> numbers[0] * 60 + numbers[1]
        else -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
    }
    return seconds.takeIf { it > 0 }
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
