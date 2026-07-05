package com.lifeos.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(CalendarUiEvent.ToggleEditor) }) {
                Icon(Icons.Filled.Add, contentDescription = "New event")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            MonthHeader(uiState.monthStart, onEvent)
            MonthGrid(uiState, onEvent)
            SectionHeader(
                title = "Agenda — " + DateFormat.getDateInstance(DateFormat.MEDIUM)
                    .format(Date(uiState.selectedDay)),
            )
            DayAgenda(uiState, onEvent)
        }
    }

    if (uiState.showEditor) {
        EventEditorSheet(uiState, onEvent)
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
        uiState.monthEvents.map { CalendarViewModel.startOfDay(it.startsAt) }.toSet()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.padding(horizontal = 8.dp),
        userScrollEnabled = false,
    ) {
        items(days) { day ->
            val selected = day != null && day == uiState.selectedDay
            Box(
                modifier = Modifier
                    .aspectRatio(1.1f)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (day != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onEvent(CalendarUiEvent.SelectDay(day)) }
                            .background(
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                shape = CircleShape,
                            )
                            .padding(6.dp),
                    ) {
                        Text(
                            dayOfMonth(day).toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                        if (day in eventDays) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayAgenda(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    val dayEnd = uiState.selectedDay + 86_400_000L
    val dayEvents = uiState.monthEvents.filter { it.startsAt < dayEnd && it.endsAt > uiState.selectedDay }
    if (dayEvents.isEmpty()) {
        Text(
            "Nothing scheduled",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    } else {
        LazyColumn {
            items(dayEvents, key = { it.id }) { event ->
                EventRow(event, onEvent)
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEventEntity, onEvent: (CalendarUiEvent) -> Unit) {
    val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    ListItem(
        headlineContent = { Text(event.title) },
        supportingContent = {
            Text(
                buildString {
                    append(timeFormat.format(Date(event.startsAt)))
                    append(" – ")
                    append(timeFormat.format(Date(event.endsAt)))
                    event.location?.let { append("  ·  $it") }
                    if (event.reminderId != null) append("  ·  🔔")
                },
            )
        },
        trailingContent = {
            IconButton(onClick = { onEvent(CalendarUiEvent.Delete(event.id)) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "New event on " + DateFormat.getDateInstance(DateFormat.MEDIUM)
                    .format(Date(uiState.selectedDay)),
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
                label = { Text("Location (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.editorHour,
                    onValueChange = { onEvent(CalendarUiEvent.EditorHourChanged(it)) },
                    label = { Text("Hour (0–23)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = uiState.editorDurationMinutes,
                    onValueChange = { onEvent(CalendarUiEvent.EditorDurationChanged(it)) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            FilterChip(
                selected = uiState.editorRemind,
                onClick = { onEvent(CalendarUiEvent.EditorRemindToggled) },
                label = { Text("Remind me 30 min before") },
            )
            Button(
                onClick = { onEvent(CalendarUiEvent.Save) },
                enabled = uiState.editorTitle.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save event")
            }
        }
    }
}

/** Cells for a 7-column month grid: leading nulls pad to the first weekday. */
private fun monthDays(monthStart: Long): List<Long?> {
    val calendar = Calendar.getInstance().apply { timeInMillis = monthStart }
    val firstDayOfWeek = calendar.firstDayOfWeek
    val leadingEmpty = (calendar.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    return List(leadingEmpty) { null } + List(daysInMonth) { index ->
        monthStart + index * 86_400_000L
    }
}

private fun dayOfMonth(dayStart: Long): Int =
    Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_MONTH)
