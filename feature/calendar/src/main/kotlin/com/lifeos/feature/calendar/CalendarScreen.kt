package com.lifeos.feature.calendar

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.database.calendar.CalendarEventEntity
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DAY_MS = CalendarViewModel.DAY_MS

@Composable
fun CalendarRoute(viewModel: CalendarViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            if (effect is CalendarUiEffect.ShareIcs) {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType("text/calendar")
                            .putExtra(Intent.EXTRA_SUBJECT, "lifeos-calendar.ics")
                            .putExtra(Intent.EXTRA_TEXT, effect.ics),
                        "Export calendar (.ics)",
                    ),
                )
            }
        }
    }
    CalendarScreen(uiState = uiState, onEvent = viewModel::onEvent)
}

/**
 * Calendar (§Module 19, standalone-app cut): Month grid with event chips +
 * agenda, Week and Day hour timelines with positioned event blocks, tap a slot
 * to create at that hour, swipe or arrows to move between periods, Today jump,
 * overflow for Proton sync / system mirror / ICS export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarScreen(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(CalendarUiEvent.DismissError)
        }
    }

    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) onEvent(CalendarUiEvent.MirrorToSystem)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(periodTitle(uiState.viewMode, uiState.anchor)) },
                actions = {
                    TextButton(onClick = { onEvent(CalendarUiEvent.Today) }) { Text("Today") }
                    IconButton(onClick = { onEvent(CalendarUiEvent.Previous) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                    }
                    IconButton(onClick = { onEvent(CalendarUiEvent.Next) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                    }
                    Box {
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Proton sync & export") },
                                onClick = { onEvent(CalendarUiEvent.ToggleConnections); menu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Mirror to system calendar") },
                                onClick = {
                                    calendarPermission.launch(
                                        arrayOf(
                                            android.Manifest.permission.READ_CALENDAR,
                                            android.Manifest.permission.WRITE_CALENDAR,
                                        ),
                                    )
                                    menu = false
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(CalendarUiEvent.NewEventAt(uiState.selectedDay, 9)) }) {
                Icon(Icons.Filled.Add, contentDescription = "New event")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Horizontal swipe anywhere moves the period, like a real calendar app.
                .pointerInputSwipe(
                    onSwipeLeft = { onEvent(CalendarUiEvent.Next) },
                    onSwipeRight = { onEvent(CalendarUiEvent.Previous) },
                ),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                CalendarViewMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.viewMode == mode,
                        onClick = { onEvent(CalendarUiEvent.SetViewMode(mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index, CalendarViewMode.entries.size),
                    ) {
                        Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }

            when (uiState.viewMode) {
                CalendarViewMode.MONTH -> MonthView(uiState, onEvent)
                CalendarViewMode.WEEK -> WeekView(uiState, onEvent)
                CalendarViewMode.DAY -> DayView(uiState, onEvent)
            }
        }
    }

    if (uiState.showEditor) EventEditorSheet(uiState, onEvent)
    if (uiState.showConnections) ConnectionsSheet(uiState, onEvent)
}

/** Simple horizontal fling detection without a pager. */
private fun Modifier.pointerInputSwipe(onSwipeLeft: () -> Unit, onSwipeRight: () -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(Unit) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    total += amount
                },
                onDragEnd = {
                    when {
                        total < -120f -> onSwipeLeft()
                        total > 120f -> onSwipeRight()
                    }
                },
            )
        },
    )

// -------------------------------------------------------------------- month --

@Composable
private fun MonthView(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    val gridStart = CalendarViewModel.startOfWeek(uiState.anchor)
    val monthOfAnchor = Calendar.getInstance().apply { timeInMillis = uiState.anchor }.get(Calendar.MONTH)
    val today = CalendarViewModel.startOfDay(System.currentTimeMillis())
    val eventsByDay = remember(uiState.events) {
        uiState.events.groupBy { CalendarViewModel.startOfDay(it.startsAt) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                Text(
                    day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        repeat(6) { week ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)) {
                repeat(7) { column ->
                    val day = gridStart + (week * 7 + column) * DAY_MS
                    val inMonth = Calendar.getInstance().apply { timeInMillis = day }
                        .get(Calendar.MONTH) == monthOfAnchor
                    val dayEvents = eventsByDay[day].orEmpty()
                    val selected = day == uiState.selectedDay
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(1.dp)
                            .background(
                                when {
                                    selected -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { onEvent(CalendarUiEvent.SelectDay(day)) }
                            .padding(2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 2.dp, top = 2.dp)
                                .then(
                                    if (day == today) {
                                        Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            Text(
                                Calendar.getInstance().apply { timeInMillis = day }
                                    .get(Calendar.DAY_OF_MONTH).toString(),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = when {
                                    day == today -> MaterialTheme.colorScheme.onPrimary
                                    inMonth -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                },
                            )
                        }
                        dayEvents.take(2).forEach { event ->
                            Text(
                                event.title,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 1.dp)
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 3.dp),
                            )
                        }
                        if (dayEvents.size > 2) {
                            Text(
                                "+${dayEvents.size - 2}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 3.dp),
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        // Agenda for the selected day.
        val selectedEvents = eventsByDay[uiState.selectedDay].orEmpty().sortedBy { it.startsAt }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1.2f)) {
            if (selectedEvents.isEmpty()) {
                item {
                    Text(
                        "Nothing on ${DAY_TITLE.format(Date(uiState.selectedDay))} — tap + to add.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(selectedEvents, key = { it.id }) { event -> EventRow(event, onEvent) }
        }
    }
}

// ----------------------------------------------------------------- timeline --

private val HOUR_HEIGHT = 56.dp

@Composable
private fun WeekView(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    val days = (0 until 7).map { uiState.anchor + it * DAY_MS }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 44.dp, end = 8.dp)) {
            days.forEach { day ->
                val isSelected = day == uiState.selectedDay
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEvent(CalendarUiEvent.SelectDay(day)) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        WEEKDAY.format(Date(day)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        DAY_NUM.format(Date(day)),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Timeline(days = days, events = uiState.events, onEvent = onEvent)
    }
}

@Composable
private fun DayView(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    val allDay = uiState.events.filter { it.allDay && CalendarViewModel.startOfDay(it.startsAt) == uiState.anchor }
    Column(modifier = Modifier.fillMaxSize()) {
        allDay.forEach { event ->
            Surface(
                onClick = { onEvent(CalendarUiEvent.EditEvent(event)) },
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            ) {
                Text(
                    "All day · ${event.title}",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Timeline(days = listOf(uiState.anchor), events = uiState.events, onEvent = onEvent)
    }
}

/** Shared hour grid: 24 rows, tappable slots, positioned event blocks, now-line. */
@Composable
private fun Timeline(
    days: List<Long>,
    events: List<CalendarEventEntity>,
    onEvent: (CalendarUiEvent) -> Unit,
) {
    val scroll = rememberScrollState(initial = with(LocalDensity.current) { (HOUR_HEIGHT * 7).roundToPx() })
    val now = System.currentTimeMillis()
    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        Column(modifier = Modifier.width(44.dp)) {
            repeat(24) { hour ->
                Box(modifier = Modifier.height(HOUR_HEIGHT), contentAlignment = Alignment.TopCenter) {
                    Text(
                        "%02d".format(hour),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        days.forEach { day ->
            val dayEvents = events.filter {
                !it.allDay && it.startsAt < day + DAY_MS && it.endsAt > day
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(HOUR_HEIGHT * 24)
                    .padding(horizontal = 1.dp)
                    .pointerInput(day) {
                        detectTapGestures { offset ->
                            val hour = (offset.y / (HOUR_HEIGHT.toPx())).toInt().coerceIn(0, 23)
                            onEvent(CalendarUiEvent.NewEventAt(day, hour))
                        }
                    },
            ) {
                // Hour grid lines.
                repeat(24) { hour ->
                    HorizontalDivider(
                        modifier = Modifier.offset(y = HOUR_HEIGHT * hour),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    )
                }
                dayEvents.forEach { event ->
                    val startMin = ((maxOf(event.startsAt, day) - day) / 60_000L).toInt()
                    val endMin = ((minOf(event.endsAt, day + DAY_MS) - day) / 60_000L).toInt()
                    val height = ((endMin - startMin).coerceAtLeast(24) / 60f)
                    Surface(
                        onClick = { onEvent(CalendarUiEvent.EditEvent(event)) },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = HOUR_HEIGHT * (startMin / 60f))
                            .height(HOUR_HEIGHT * height),
                    ) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            Text(
                                event.title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                TIME.format(Date(event.startsAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
                // Current-time indicator on today's column.
                if (CalendarViewModel.startOfDay(now) == day) {
                    val nowMin = ((now - day) / 60_000L).toInt()
                    HorizontalDivider(
                        modifier = Modifier.offset(y = HOUR_HEIGHT * (nowMin / 60f)),
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------- shared --

@Composable
private fun EventRow(event: CalendarEventEntity, onEvent: (CalendarUiEvent) -> Unit) {
    val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    ListItem(
        modifier = Modifier.clickable(enabled = event.id > 0) { onEvent(CalendarUiEvent.EditEvent(event)) },
        headlineContent = { Text(event.title) },
        supportingContent = {
            Text(
                buildString {
                    if (event.allDay) {
                        append("All day")
                    } else {
                        append(timeFormat.format(Date(event.startsAt)))
                        append(" – ")
                        append(timeFormat.format(Date(event.endsAt)))
                    }
                    event.location?.let { append("  ·  $it") }
                    if (event.reminderId != null) append("  ·  reminder set")
                },
            )
        },
        trailingContent = {
            // Negative id = a to-do surfaced from Tasks; edit it there, not here.
            if (event.id > 0) {
                IconButton(onClick = { onEvent(CalendarUiEvent.Delete(event.id)) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditorSheet(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onEvent(CalendarUiEvent.ToggleEditor) }) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (uiState.editingEventId != null) "Edit event" else
                    "New event · ${DAY_TITLE.format(Date(uiState.selectedDay))}",
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = uiState.editorTitle,
                onValueChange = { onEvent(CalendarUiEvent.EditorTitleChanged(it)) },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.editorLocation,
                onValueChange = { onEvent(CalendarUiEvent.EditorLocationChanged(it)) },
                label = { Text("Location (feeds leave-by alerts)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.editorNotes,
                onValueChange = { onEvent(CalendarUiEvent.EditorNotesChanged(it)) },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!uiState.editorAllDay) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.editorHour,
                        onValueChange = { onEvent(CalendarUiEvent.EditorHourChanged(it)) },
                        label = { Text("Hour") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.editorMinute,
                        onValueChange = { onEvent(CalendarUiEvent.EditorMinuteChanged(it)) },
                        label = { Text("Min") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.editorDurationMinutes,
                        onValueChange = { onEvent(CalendarUiEvent.EditorDurationChanged(it)) },
                        label = { Text("Duration (min)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.editorAllDay,
                    onClick = { onEvent(CalendarUiEvent.EditorAllDayToggled) },
                    label = { Text("All day") },
                )
                if (uiState.editingEventId == null) {
                    FilterChip(
                        selected = uiState.editorRemind,
                        onClick = { onEvent(CalendarUiEvent.EditorRemindToggled) },
                        label = { Text("Remind 30 min before") },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onEvent(CalendarUiEvent.Save) },
                    enabled = uiState.editorTitle.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (uiState.editingEventId != null) "Save changes" else "Create")
                }
                uiState.editingEventId?.let { id ->
                    OutlinedButton(onClick = { onEvent(CalendarUiEvent.Delete(id)) }) { Text("Delete") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionsSheet(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onEvent(CalendarUiEvent.ToggleConnections) }) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Proton Calendar", style = MaterialTheme.typography.titleLarge)
            Text(
                "One-way import (Proton has no two-way API): in Proton web, share your calendar with a " +
                    "Full view link and paste it here. The link holds the decryption key — it never leaves this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = uiState.protonUrlDraft,
                onValueChange = { onEvent(CalendarUiEvent.ProtonUrlChanged(it)) },
                label = { Text("Proton Full-view ICS link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (uiState.syncing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { onEvent(CalendarUiEvent.SyncProton) },
                enabled = uiState.protonUrlDraft.isNotBlank() && !uiState.syncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sync from Proton now")
            }
            OutlinedButton(
                onClick = { onEvent(CalendarUiEvent.ExportIcs) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export as .ics (add to Proton via \"Add calendar from URL\")")
            }
        }
    }
}

private fun periodTitle(mode: CalendarViewMode, anchor: Long): String = when (mode) {
    CalendarViewMode.MONTH -> MONTH_TITLE.format(Date(anchor))
    CalendarViewMode.WEEK -> {
        val end = anchor + 6 * DAY_MS
        "${DAY_SHORT.format(Date(anchor))} – ${DAY_SHORT.format(Date(end))}"
    }
    CalendarViewMode.DAY -> DAY_TITLE.format(Date(anchor))
}

private val MONTH_TITLE = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
private val DAY_TITLE = SimpleDateFormat("EEE d MMM", Locale.getDefault())
private val DAY_SHORT = SimpleDateFormat("d MMM", Locale.getDefault())
private val WEEKDAY = SimpleDateFormat("EEE", Locale.getDefault())
private val DAY_NUM = SimpleDateFormat("d", Locale.getDefault())
private val TIME = SimpleDateFormat("HH:mm", Locale.getDefault())
