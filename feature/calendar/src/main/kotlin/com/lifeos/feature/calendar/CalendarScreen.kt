package com.lifeos.feature.calendar

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.database.calendar.CalendarEventEntity
import com.lifeos.core.designsystem.component.SectionHeader
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
                title = { Text("Calendar") },
                actions = {
                    IconButton(onClick = { onEvent(CalendarUiEvent.ToggleConnections) }) {
                        Icon(Icons.Filled.CloudSync, contentDescription = "Proton sync + export")
                    }
                    IconButton(onClick = {
                        calendarPermission.launch(
                            arrayOf(
                                android.Manifest.permission.READ_CALENDAR,
                                android.Manifest.permission.WRITE_CALENDAR,
                            ),
                        )
                    }) {
                        Icon(Icons.Filled.Sync, contentDescription = "Mirror to system calendar")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(CalendarUiEvent.ToggleEditor) }) {
                Icon(Icons.Filled.Add, contentDescription = "New event")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                CalendarViewMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.viewMode == mode,
                        onClick = { onEvent(CalendarUiEvent.SetViewMode(mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index, CalendarViewMode.entries.size),
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            when (uiState.viewMode) {
                CalendarViewMode.MONTH -> {
                    MonthHeader(uiState.monthStart, onEvent)
                    MonthGrid(uiState, onEvent)
                    SectionHeader(
                        title = "Agenda — " + DateFormat.getDateInstance(DateFormat.MEDIUM)
                            .format(Date(uiState.selectedDay)),
                    )
                    DayAgenda(uiState, onEvent)
                }
                CalendarViewMode.WEEK -> WeekView(uiState, onEvent)
                CalendarViewMode.DAY -> {
                    WeekStrip(uiState, onEvent)
                    DayTimeline(uiState, onEvent)
                }
            }
        }
    }

    if (uiState.showEditor) {
        EventEditorSheet(uiState, onEvent)
    }
    if (uiState.showConnections) {
        ConnectionsSheet(uiState, onEvent)
    }
}

@Composable
private fun MonthHeader(monthStart: Long, onEvent: (CalendarUiEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onEvent(CalendarUiEvent.PreviousMonth) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Date(monthStart)),
            style = MaterialTheme.typography.titleLarge,
        )
        IconButton(onClick = { onEvent(CalendarUiEvent.NextMonth) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun MonthGrid(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    val days = remember(uiState.monthStart) { monthDays(uiState.monthStart) }
    val eventDays = remember(uiState.monthEvents) {
        uiState.monthEvents.groupBy { CalendarViewModel.startOfDay(it.startsAt) }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.padding(horizontal = 8.dp),
        userScrollEnabled = false,
    ) {
        items(days) { day ->
            val selected = day != null && day == uiState.selectedDay
            val count = day?.let { eventDays[it]?.size } ?: 0
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(3.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable(enabled = day != null) { day?.let { onEvent(CalendarUiEvent.SelectDay(it)) } },
                contentAlignment = Alignment.Center,
            ) {
                if (day != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            SimpleDateFormat("d", Locale.getDefault()).format(Date(day)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(minOf(count, 3)) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayAgenda(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    val dayEvents = uiState.monthEvents.filter {
        CalendarViewModel.startOfDay(it.startsAt) == uiState.selectedDay
    }
    if (dayEvents.isEmpty()) {
        Text(
            "Free day. Guard it.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn {
        items(dayEvents, key = { it.id }) { event ->
            EventRow(event, onEvent)
        }
    }
}

/** Week view: seven day columns, events listed beneath each. */
@Composable
private fun WeekView(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    WeekStrip(uiState, onEvent)
    SectionHeader(
        title = DateFormat.getDateInstance(DateFormat.FULL).format(Date(uiState.selectedDay)),
    )
    DayAgenda(uiState, onEvent)
}

/** Horizontal 7-day selector for the week containing the selected day. */
@Composable
private fun WeekStrip(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    val weekDays = remember(uiState.selectedDay) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = uiState.selectedDay
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        }
        (0..6).map { offset ->
            Calendar.getInstance().apply {
                timeInMillis = calendar.timeInMillis
                add(Calendar.DAY_OF_YEAR, offset)
            }.timeInMillis
        }
    }
    val eventDays = remember(uiState.monthEvents) {
        uiState.monthEvents.groupBy { CalendarViewModel.startOfDay(it.startsAt) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        weekDays.forEach { day ->
            val selected = day == uiState.selectedDay
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onEvent(CalendarUiEvent.SelectDay(day)) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    SimpleDateFormat("EEE", Locale.getDefault()).format(Date(day)),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    SimpleDateFormat("d", Locale.getDefault()).format(Date(day)),
                    style = MaterialTheme.typography.titleMedium,
                )
                if ((eventDays[day]?.size ?: 0) > 0) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
        }
    }
}

/** Day view: an hour-by-hour timeline with events pinned to their start hour. */
@Composable
private fun DayTimeline(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    val dayEvents = uiState.monthEvents
        .filter { CalendarViewModel.startOfDay(it.startsAt) == uiState.selectedDay }
        .groupBy { Calendar.getInstance().apply { timeInMillis = it.startsAt }.get(Calendar.HOUR_OF_DAY) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        (0..23).forEach { hour ->
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                Text(
                    "%02d:00".format(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    val slot = dayEvents[hour].orEmpty()
                    if (slot.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    } else {
                        slot.forEach { event ->
                            Card(
                                onClick = { onEvent(CalendarUiEvent.EditEvent(event)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        event.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
                                    Text(
                                        "${fmt.format(Date(event.startsAt))} – ${fmt.format(Date(event.endsAt))}" +
                                            (event.location?.let { " · $it" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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
                    if (event.reminderId != null) append("  ·  🔔")
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
                if (uiState.editingEventId != null) "Edit event" else "New event",
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
            Button(
                onClick = { onEvent(CalendarUiEvent.Save) },
                enabled = uiState.editorTitle.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.editingEventId != null) "Save changes" else "Create")
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

/** Leading nulls pad the grid so day 1 lands on the right weekday column. */
private fun monthDays(monthStart: Long): List<Long?> {
    val calendar = Calendar.getInstance().apply { timeInMillis = monthStart }
    val firstWeekday = calendar.get(Calendar.DAY_OF_WEEK)
    val offset = (firstWeekday - calendar.firstDayOfWeek + 7) % 7
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    return List(offset) { null } + (0 until daysInMonth).map { day ->
        Calendar.getInstance().apply {
            timeInMillis = monthStart
            add(Calendar.DAY_OF_YEAR, day)
        }.timeInMillis
    }
}
