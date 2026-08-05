package com.lifeos.feature.calendar

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.database.calendar.CalendarEventEntity
import com.lifeos.core.designsystem.component.LifeMotion
import com.lifeos.feature.calendar.data.CalendarPalette
import com.lifeos.feature.calendar.data.DefaultCalendarRepository
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

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
 * Calendar (§Module 19): coloured calendars, a month grid, week and day hour
 * timelines with real pinch zoom and overlap-aware blocks, an agenda list,
 * animated swipes between periods, Proton-style multi-alert reminders,
 * OpenStreetMap location suggestions and live ICS subscriptions.
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
                title = {
                    // Tapping the period name opens a date picker to jump anywhere.
                    Text(
                        periodTitle(uiState.viewMode, uiState.anchor),
                        modifier = Modifier.clickable { onEvent(CalendarUiEvent.ToggleDatePicker) },
                    )
                },
                actions = {
                    IconButton(onClick = { onEvent(CalendarUiEvent.ToggleSearch) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search events")
                    }
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
                                text = { Text("Manage calendars") },
                                onClick = { onEvent(CalendarUiEvent.ToggleCalendarManager); menu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh subscriptions") },
                                onClick = { onEvent(CalendarUiEvent.SyncSubscriptions); menu = false },
                            )
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
                .padding(innerPadding),
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
                        Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }, maxLines = 1)
                    }
                }
            }

            if (uiState.calendars.size > 1) CalendarLegend(uiState, onEvent)

            // Swiping and the arrows both land here, and the period slides in from
            // the side it came from.
            AnimatedContent(
                targetState = uiState.anchor,
                transitionSpec = {
                    val forward = uiState.direction >= 0
                    val enter = slideInHorizontally(
                        animationSpec = tween(LifeMotion.ENTER_MS),
                        initialOffsetX = { width -> if (forward) width else -width },
                    ) + fadeIn(tween(LifeMotion.ENTER_MS))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(LifeMotion.EXIT_MS),
                        targetOffsetX = { width -> if (forward) -width else width },
                    ) + fadeOut(tween(LifeMotion.EXIT_MS))
                    enter togetherWith exit
                },
                label = "calendar-period",
                modifier = Modifier
                    .fillMaxSize()
                    .periodSwipe(
                        onSwipeLeft = { onEvent(CalendarUiEvent.Next) },
                        onSwipeRight = { onEvent(CalendarUiEvent.Previous) },
                    ),
            ) { anchor ->
                val page = uiState.copy(anchor = anchor)
                when (uiState.viewMode) {
                    CalendarViewMode.MONTH -> MonthView(page, onEvent)
                    CalendarViewMode.WEEK -> WeekView(page, onEvent)
                    CalendarViewMode.DAY -> DayView(page, onEvent)
                    CalendarViewMode.AGENDA -> AgendaView(page, onEvent)
                }
            }
        }
    }

    if (uiState.showEditor) EventEditorSheet(uiState, onEvent)
    if (uiState.showConnections) ConnectionsSheet(uiState, onEvent)
    if (uiState.showCalendarManager) CalendarManagerSheet(uiState, onEvent)
    if (uiState.showSearch) SearchSheet(uiState, onEvent)
    if (uiState.showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = uiState.selectedDay)
        DatePickerDialog(
            onDismissRequest = { onEvent(CalendarUiEvent.ToggleDatePicker) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            onEvent(CalendarUiEvent.JumpToDate(it))
                        } ?: onEvent(CalendarUiEvent.ToggleDatePicker)
                    },
                ) { Text("Jump") }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(CalendarUiEvent.ToggleDatePicker) }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Colour key across the top; tapping one hides or shows that calendar. */
@Composable
private fun CalendarLegend(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        uiState.calendars.forEach { calendar ->
            val colour = Color(calendar.colorArgb)
            Surface(
                onClick = { onEvent(CalendarUiEvent.ToggleCalendarVisible(calendar.id)) },
                shape = RoundedCornerShape(50),
                color = if (calendar.visible) colour.copy(alpha = 0.20f) else Color.Transparent,
                border = BorderStroke(1.dp, colour.copy(alpha = 0.6f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(modifier = Modifier.size(8.dp).background(colour, CircleShape))
                    Text(
                        calendar.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        color = if (calendar.visible) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/**
 * Horizontal fling that moves the period. Only fires on a gesture that is
 * clearly sideways, so the timeline's vertical scroll and the pinch zoom keep
 * working underneath it.
 */
private fun Modifier.periodSwipe(onSwipeLeft: () -> Unit, onSwipeRight: () -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(Unit) {
            var total = 0f
            val tracker = VelocityTracker()
            detectHorizontalDragGestures(
                onDragStart = { total = 0f; tracker.resetTracking() },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    total += amount
                    tracker.addPosition(change.uptimeMillis, change.position)
                },
                onDragEnd = {
                    val velocity = tracker.calculateVelocity().x
                    val moved = abs(total) > 110f || abs(velocity) > 700f
                    if (moved) {
                        if (total < 0) onSwipeLeft() else onSwipeRight()
                    }
                },
            )
        },
    )

/**
 * Pinch-to-zoom that actually fires inside a scrollable timeline.
 *
 * The earlier `detectTransformGestures` never worked here for two reasons: the
 * vertical scroll and the day columns' own tap/long-press handlers consumed the
 * pointers first, and the gesture closure captured the hour height from its first
 * composition so every pinch scaled the same stale value. This watches the
 * Initial pass — before children see anything — only claims the gesture once a
 * second finger is down, and reads the live height through a state holder.
 */
@Composable
private fun Modifier.pinchZoom(currentHeightDp: Float, onHeight: (Float) -> Unit): Modifier {
    val height = rememberUpdatedState(currentHeightDp)
    val sink = rememberUpdatedState(onHeight)
    return this.then(
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                var lastDistance = 0f
                while (true) {
                    val pointerEvent = awaitPointerEvent(PointerEventPass.Initial)
                    val down = pointerEvent.changes.filter { it.pressed }
                    if (down.size >= 2) {
                        val distance = (down[0].position - down[1].position).getDistance()
                        if (lastDistance > 1f && distance > 1f) {
                            val factor = distance / lastDistance
                            sink.value((height.value * factor).coerceIn(24f, 260f))
                        }
                        lastDistance = distance
                        // Claim it, so neither the scroll nor the columns react.
                        pointerEvent.changes.forEach { it.consume() }
                    } else {
                        lastDistance = 0f
                    }
                }
            }
        },
    )
}

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
                            val colour = Color(uiState.colorOf(event))
                            Text(
                                event.title,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                style = MaterialTheme.typography.labelSmall,
                                color = onColour(colour),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 1.dp)
                                    .background(colour.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
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
            items(selectedEvents, key = { it.id }) { event -> EventRow(event, uiState, onEvent) }
        }
    }
}

// ------------------------------------------------------------------- agenda --

/** A flat, scrollable "what is coming" list — the fastest way to read a month. */
@Composable
private fun AgendaView(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    val from = uiState.anchor
    val to = from + 62L * DAY_MS
    val grouped = remember(uiState.events, from) {
        uiState.events
            .filter { it.startsAt in from..to }
            .sortedBy { it.startsAt }
            .groupBy { CalendarViewModel.startOfDay(it.startsAt) }
            .toSortedMap()
    }
    if (grouped.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nothing scheduled from ${DAY_TITLE.format(Date(from))}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        grouped.forEach { (day, events) ->
            item(key = "head-$day") {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        DAY_FULL.format(Date(day)),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            items(events, key = { it.id }) { event -> EventRow(event, uiState, onEvent) }
        }
    }
}

// ----------------------------------------------------------------- timeline --

@Composable
private fun WeekView(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    val days = (0 until 7).map { uiState.anchor + it * DAY_MS }
    val today = CalendarViewModel.startOfDay(System.currentTimeMillis())
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
                        color = when {
                            day == today -> MaterialTheme.colorScheme.primary
                            isSelected -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
        AllDayStrip(days, uiState, onEvent)
        Timeline(days = days, uiState = uiState, onEvent = onEvent)
    }
}

@Composable
private fun DayView(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        AllDayStrip(listOf(uiState.anchor), uiState, onEvent)
        Timeline(days = listOf(uiState.anchor), uiState = uiState, onEvent = onEvent)
    }
}

/** All-day events sit above the hour grid, where they cannot distort it. */
@Composable
private fun AllDayStrip(
    days: List<Long>,
    uiState: CalendarUiState,
    onEvent: (CalendarUiEvent) -> Unit,
) {
    val allDay = uiState.events.filter { event ->
        event.allDay && days.any { CalendarViewModel.startOfDay(event.startsAt) == it }
    }
    if (allDay.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        allDay.forEach { event ->
            val colour = Color(uiState.colorOf(event))
            Surface(
                onClick = { onEvent(CalendarUiEvent.EditEvent(event)) },
                color = colour.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
            ) {
                Text(
                    "All day · ${event.title}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = onColour(colour),
                )
            }
        }
    }
}

/**
 * Shared hour grid for Week and Day.
 *
 * Pinch anywhere to zoom: the hour height scales and the ladder snaps through
 * 60/30/15/10/5-minute steps, down to a compressed overview where a whole day
 * fits. Long-press then drag inside a column sweeps out a range and opens the
 * editor pre-filled. Overlapping events share the column width instead of hiding
 * each other, and the current time is a live red line.
 */
@Composable
private fun Timeline(
    days: List<Long>,
    uiState: CalendarUiState,
    onEvent: (CalendarUiEvent) -> Unit,
) {
    val density = LocalDensity.current
    val hourHeight = uiState.hourHeightDp.dp
    val minutesPerStep = uiState.minutesPerStep
    val now = System.currentTimeMillis()
    val hourHeightPx = with(density) { hourHeight.toPx() }

    // Open on the current hour when today is in view, on the working day otherwise.
    val focusMinute = remember(days.first()) {
        if (days.any { CalendarViewModel.startOfDay(now) == it }) {
            ((now - CalendarViewModel.startOfDay(now)) / 60_000L).toInt() - 90
        } else {
            7 * 60
        }.coerceAtLeast(0)
    }
    val scroll = rememberScrollState(
        initial = with(density) { (hourHeight * (focusMinute / 60f)).roundToPx() },
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .pinchZoom(uiState.hourHeightDp) { next ->
                onEvent(CalendarUiEvent.SetZoom(stepForHeight(next), next))
            }
            .verticalScroll(scroll),
    ) {
        Column(modifier = Modifier.width(44.dp)) {
            repeat(24) { hour ->
                Box(modifier = Modifier.height(hourHeight), contentAlignment = Alignment.TopCenter) {
                    Text(
                        "%02d".format(hour),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        days.forEach { day ->
            val dayEvents = uiState.events.filter {
                !it.allDay && it.startsAt < day + DAY_MS && it.endsAt > day
            }
            val laid = remember(dayEvents, day) { layOut(dayEvents, day) }
            // Live drag selection for this column, in minutes from midnight.
            var dragFrom by remember(day) { mutableStateOf<Int?>(null) }
            var dragTo by remember(day) { mutableStateOf<Int?>(null) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(hourHeight * 24)
                    .padding(horizontal = 1.dp)
                    .pointerInput(day, minutesPerStep, hourHeightPx) {
                        detectTapGestures { offset ->
                            val minute = snapMinutes(offset.y / hourHeightPx * 60f, minutesPerStep)
                            onEvent(
                                CalendarUiEvent.NewEventForRange(
                                    dayStart = day,
                                    startMinuteOfDay = minute.coerceIn(0, 24 * 60 - minutesPerStep),
                                    durationMinutes = maxOf(minutesPerStep, 30),
                                ),
                            )
                        }
                    }
                    .pointerInput(day, minutesPerStep, hourHeightPx) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val minute = snapMinutes(offset.y / hourHeightPx * 60f, minutesPerStep)
                                dragFrom = minute
                                dragTo = minute + minutesPerStep
                            },
                            onDrag = { change, _ ->
                                val start = dragFrom ?: return@detectDragGesturesAfterLongPress
                                val minute = snapMinutes(change.position.y / hourHeightPx * 60f, minutesPerStep)
                                // Always keep at least one step selected.
                                dragTo = if (minute <= start) start + minutesPerStep else minute
                            },
                            onDragEnd = {
                                val start = dragFrom
                                val end = dragTo
                                if (start != null && end != null) {
                                    onEvent(
                                        CalendarUiEvent.NewEventForRange(
                                            dayStart = day,
                                            startMinuteOfDay = start.coerceIn(0, 24 * 60 - minutesPerStep),
                                            durationMinutes = (end - start).coerceAtLeast(minutesPerStep),
                                        ),
                                    )
                                }
                                dragFrom = null
                                dragTo = null
                            },
                            onDragCancel = { dragFrom = null; dragTo = null },
                        )
                    },
            ) {
                // Ladder lines: every step, with the hour lines drawn stronger.
                val stepsPerDay = (24 * 60) / minutesPerStep
                repeat(stepsPerDay + 1) { index ->
                    val minute = index * minutesPerStep
                    val onHour = minute % 60 == 0
                    HorizontalDivider(
                        modifier = Modifier.offset(y = hourHeight * (minute / 60f)),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (onHour) 0.7f else 0.3f),
                    )
                }
                // The sweep being dragged right now.
                val from = dragFrom
                val to = dragTo
                if (from != null && to != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = hourHeight * (from / 60f))
                            .height(hourHeight * ((to - from) / 60f)),
                    ) {
                        Text(
                            "${formatMinuteOfDay(from)} – ${formatMinuteOfDay(to)}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(4.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                laid.forEach { placed ->
                    val event = placed.event
                    val startMin = ((maxOf(event.startsAt, day) - day) / 60_000L).toInt()
                    val endMin = ((minOf(event.endsAt, day + DAY_MS) - day) / 60_000L).toInt()
                    val heightHours = ((endMin - startMin).coerceAtLeast(minutesPerStep) / 60f)
                    val colour = if (event.id < 0) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        Color(uiState.colorOf(event))
                    }
                    EventBlock(
                        event = event,
                        colour = colour,
                        lane = placed.lane,
                        lanes = placed.lanes,
                        offsetY = hourHeight * (startMin / 60f),
                        blockHeight = hourHeight * heightHours,
                        onEvent = onEvent,
                    )
                }
                if (CalendarViewModel.startOfDay(now) == day) {
                    val nowMin = ((now - day) / 60_000L).toInt()
                    HorizontalDivider(
                        modifier = Modifier.offset(y = hourHeight * (nowMin / 60f)),
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * One block in the hour grid. [lane] of [lanes] splits the column so clashing
 * events sit side by side, the way every real calendar draws them.
 */
@Composable
private fun EventBlock(
    event: CalendarEventEntity,
    colour: Color,
    lane: Int,
    lanes: Int,
    offsetY: Dp,
    blockHeight: Dp,
    onEvent: (CalendarUiEvent) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().offset(y = offsetY).height(blockHeight)) {
        Row(modifier = Modifier.fillMaxSize()) {
            repeat(lanes) { index ->
                if (index == lane) {
                    Surface(
                        onClick = { onEvent(CalendarUiEvent.EditEvent(event)) },
                        color = colour.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(end = 1.dp),
                    ) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            Text(
                                event.title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = onColour(colour),
                            )
                            // A squeezed block has no room for a second line.
                            if (blockHeight > 34.dp) {
                                Text(
                                    TIME.format(Date(event.startsAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onColour(colour).copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** An event plus the lane it was given among the ones it overlaps. */
private data class PlacedEvent(val event: CalendarEventEntity, val lane: Int, val lanes: Int)

/**
 * Greedy interval colouring: events are walked in start order and dropped into
 * the first lane whose last event has already finished. Every member of a
 * clashing cluster is then told how many lanes the cluster needed, so they all
 * shrink by the same amount.
 */
private fun layOut(events: List<CalendarEventEntity>, day: Long): List<PlacedEvent> {
    if (events.isEmpty()) return emptyList()
    val sorted = events.sortedWith(compareBy({ it.startsAt }, { -(it.endsAt - it.startsAt) }))
    val laneEnds = mutableListOf<Long>()
    val assigned = mutableListOf<Pair<CalendarEventEntity, Int>>()
    // Clusters are maximal runs of events that transitively overlap.
    val clusterOf = mutableListOf<Int>()
    var clusterIndex = 0
    var clusterEnd = Long.MIN_VALUE

    sorted.forEach { event ->
        val start = maxOf(event.startsAt, day)
        val end = maxOf(minOf(event.endsAt, day + DAY_MS), start + 60_000L)
        if (start >= clusterEnd) {
            clusterIndex++
            laneEnds.clear()
            clusterEnd = end
        } else {
            clusterEnd = maxOf(clusterEnd, end)
        }
        val lane = laneEnds.indexOfFirst { it <= start }.let { found ->
            if (found >= 0) {
                laneEnds[found] = end
                found
            } else {
                laneEnds += end
                laneEnds.lastIndex
            }
        }
        assigned += event to lane
        clusterOf += clusterIndex
    }

    val lanesPerCluster = mutableMapOf<Int, Int>()
    assigned.forEachIndexed { index, (_, lane) ->
        val cluster = clusterOf[index]
        lanesPerCluster[cluster] = maxOf(lanesPerCluster[cluster] ?: 1, lane + 1)
    }
    return assigned.mapIndexed { index, (event, lane) ->
        PlacedEvent(event, lane, lanesPerCluster[clusterOf[index]] ?: 1)
    }
}

/** Rounds a minute-of-day to the visible ladder step. */
private fun snapMinutes(minutes: Float, step: Int): Int =
    ((minutes / step).toInt() * step).coerceIn(0, 24 * 60)

/** Finer ladder the more the user zooms in; coarse when compressed. */
private fun stepForHeight(hourHeightDp: Float): Int = when {
    hourHeightDp >= 180f -> 5
    hourHeightDp >= 130f -> 10
    hourHeightDp >= 95f -> 15
    hourHeightDp >= 60f -> 30
    else -> 60
}

private fun formatMinuteOfDay(minute: Int): String =
    "%02d:%02d".format((minute / 60).coerceAtMost(23), minute % 60)

/** Readable text on an arbitrary calendar colour. */
private fun onColour(colour: Color): Color {
    val luminance = 0.299f * colour.red + 0.587f * colour.green + 0.114f * colour.blue
    return if (luminance > 0.6f) Color(0xFF10151C) else Color.White
}

// ------------------------------------------------------------------- shared --

@Composable
private fun EventRow(
    event: CalendarEventEntity,
    uiState: CalendarUiState,
    onEvent: (CalendarUiEvent) -> Unit,
) {
    val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    val isTask = event.id <= 0
    val colour = if (isTask) MaterialTheme.colorScheme.tertiary else Color(uiState.colorOf(event))
    ListItem(
        modifier = Modifier.clickable(enabled = !isTask) { onEvent(CalendarUiEvent.EditEvent(event)) },
        leadingContent = {
            Box(modifier = Modifier.size(10.dp).background(colour, CircleShape))
        },
        headlineContent = { Text(if (isTask) "To-do · ${event.title}" else event.title) },
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
                    event.location?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
                    uiState.calendarName(event.calendarId)?.let { append("  ·  $it") }
                    val alerts = DefaultCalendarRepository.decodeReminders(event.reminderMinutes)
                    if (alerts.isNotEmpty()) {
                        append(
                            "  ·  " + alerts.joinToString("/") {
                                DefaultCalendarRepository.humanOffset(it)
                            } + " before",
                        )
                    }
                },
            )
        },
        trailingContent = {
            // Negative id = a to-do surfaced from Tasks; edit it there, not here.
            if (!isTask) {
                Row {
                    IconButton(onClick = { onEvent(CalendarUiEvent.Duplicate(event.id)) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate")
                    }
                    IconButton(onClick = { onEvent(CalendarUiEvent.Delete(event.id)) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditorSheet(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    // Fully expanded immediately: tapping + should land straight in the form.
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    ModalBottomSheet(
        onDismissRequest = { onEvent(CalendarUiEvent.ToggleEditor) },
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (uiState.editingEventId != null) {
                            "Edit event"
                        } else {
                            "New event · ${DAY_TITLE.format(Date(uiState.selectedDay))}"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    OutlinedTextField(
                        value = uiState.editorTitle,
                        onValueChange = { onEvent(CalendarUiEvent.EditorTitleChanged(it)) },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Which calendar it lands in, colour and all.
                    if (uiState.calendars.any { it.subscriptionUrl == null }) {
                        Text("Calendar", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            uiState.calendars.filter { it.subscriptionUrl == null }.forEach { calendar ->
                                val colour = Color(calendar.colorArgb)
                                FilterChip(
                                    selected = uiState.editorCalendarId == calendar.id,
                                    onClick = { onEvent(CalendarUiEvent.EditorCalendarPicked(calendar.id)) },
                                    leadingIcon = {
                                        Box(modifier = Modifier.size(10.dp).background(colour, CircleShape))
                                    },
                                    label = { Text(calendar.name, maxLines = 1) },
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = uiState.editorLocation,
                        onValueChange = { onEvent(CalendarUiEvent.EditorLocationChanged(it)) },
                        label = { Text("Location") },
                        supportingText = {
                            Text(
                                if (uiState.searchingPlaces) {
                                    "Searching OpenStreetMap…"
                                } else {
                                    "Type three letters for suggestions"
                                },
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    uiState.locationSuggestions.forEach { suggestion ->
                        Surface(
                            onClick = { onEvent(CalendarUiEvent.EditorLocationPicked(suggestion)) },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                suggestion.label,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }

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
                                label = { Text("Length (min)") },
                                singleLine = true,
                                modifier = Modifier.weight(1.2f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CalendarViewModel.DURATION_PRESETS.forEach { minutes ->
                                AssistChip(
                                    onClick = { onEvent(CalendarUiEvent.EditorDurationPreset(minutes)) },
                                    label = { Text(DefaultCalendarRepository.humanOffset(minutes)) },
                                )
                            }
                        }
                    }

                    FilterChip(
                        selected = uiState.editorAllDay,
                        onClick = { onEvent(CalendarUiEvent.EditorAllDayToggled) },
                        label = { Text("All day") },
                    )

                    // Proton-style stack of alerts rather than one fixed 30 minutes.
                    Text("Alerts before it starts", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CalendarViewModel.REMINDER_PRESETS.take(5).forEach { minutes ->
                            ReminderChip(minutes, uiState, onEvent)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CalendarViewModel.REMINDER_PRESETS.drop(5).forEach { minutes ->
                            ReminderChip(minutes, uiState, onEvent)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = uiState.editorCustomReminder,
                            onValueChange = { onEvent(CalendarUiEvent.EditorCustomReminderChanged(it)) },
                            label = { Text("Custom (min)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = { onEvent(CalendarUiEvent.EditorCustomReminderAdded) },
                            enabled = uiState.editorCustomReminder.isNotBlank(),
                        ) { Text("Add") }
                    }
                    if (uiState.editorReminders.isNotEmpty()) {
                        Text(
                            "Alerting " + uiState.editorReminders.joinToString(", ") {
                                if (it == 0) "at start" else "${DefaultCalendarRepository.humanOffset(it)} before"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                            OutlinedButton(onClick = { onEvent(CalendarUiEvent.Duplicate(id)) }) {
                                Text("Copy")
                            }
                            OutlinedButton(onClick = { onEvent(CalendarUiEvent.Delete(id)) }) {
                                Text("Delete")
                            }
                        }
                    }
                    Box(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ReminderChip(minutes: Int, uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    FilterChip(
        selected = minutes in uiState.editorReminders,
        onClick = { onEvent(CalendarUiEvent.EditorReminderToggled(minutes)) },
        label = {
            Text(if (minutes == 0) "At start" else DefaultCalendarRepository.humanOffset(minutes))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarManagerSheet(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onEvent(CalendarUiEvent.ToggleCalendarManager) }) {
        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Calendars", style = MaterialTheme.typography.titleLarge)
                    if (uiState.calendars.isEmpty()) {
                        Text(
                            "No calendars yet — the first one is created as soon as you save an event.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(uiState.calendars, key = { it.id }) { calendar ->
                val colour = Color(calendar.colorArgb)
                ListItem(
                    modifier = Modifier.clickable { onEvent(CalendarUiEvent.EditCalendar(calendar.id)) },
                    leadingContent = {
                        Box(modifier = Modifier.size(18.dp).background(colour, CircleShape))
                    },
                    headlineContent = { Text(calendar.name) },
                    supportingContent = {
                        Text(
                            buildString {
                                append(CalendarPalette.nameOf(calendar.colorArgb))
                                if (calendar.isDefault) append("  ·  default")
                                if (calendar.subscriptionUrl != null) append("  ·  subscription")
                                calendar.lastSyncedAt?.let {
                                    append("  ·  synced ${STAMP.format(Date(it))}")
                                }
                            },
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { onEvent(CalendarUiEvent.ToggleCalendarVisible(calendar.id)) },
                            ) {
                                Icon(
                                    if (calendar.visible) {
                                        Icons.Filled.Visibility
                                    } else {
                                        Icons.Filled.VisibilityOff
                                    },
                                    contentDescription = "Show or hide",
                                )
                            }
                            if (!calendar.isDefault && calendar.subscriptionUrl == null) {
                                IconButton(
                                    onClick = { onEvent(CalendarUiEvent.SetDefaultCalendar(calendar.id)) },
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = "Make default")
                                }
                            }
                            IconButton(
                                onClick = {
                                    onEvent(
                                        CalendarUiEvent.DeleteCalendar(
                                            calendar.id,
                                            // A subscription's events are only mirrors, so they go too.
                                            deleteEvents = calendar.subscriptionUrl != null,
                                        ),
                                    )
                                },
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    },
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider()
                    Text(
                        if (uiState.editingCalendarId != null) "Edit calendar" else "New calendar",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = uiState.calendarNameDraft,
                        onValueChange = { onEvent(CalendarUiEvent.CalendarNameDraftChanged(it)) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Colour", style = MaterialTheme.typography.labelLarge)
                    // Swatches first, then a hex box for anything else.
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CalendarPalette.named.chunked(6).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (_, argb) ->
                                    val selected = uiState.calendarColorDraft == argb
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(Color(argb), CircleShape)
                                            .border(
                                                width = if (selected) 3.dp else 0.dp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                shape = CircleShape,
                                            )
                                            .clickable {
                                                onEvent(CalendarUiEvent.CalendarColorDraftChanged(argb))
                                            },
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = uiState.calendarHexDraft,
                            onValueChange = { onEvent(CalendarUiEvent.CalendarHexDraftChanged(it)) },
                            label = { Text("Custom hex") },
                            placeholder = { Text("#8B5CF6") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(uiState.calendarColorDraft), CircleShape),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onEvent(CalendarUiEvent.SaveCalendarDraft) },
                            enabled = uiState.calendarNameDraft.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (uiState.editingCalendarId != null) "Save" else "Create")
                        }
                        if (uiState.editingCalendarId != null) {
                            OutlinedButton(onClick = { onEvent(CalendarUiEvent.ClearCalendarDraft) }) {
                                Text("Cancel")
                            }
                        }
                    }

                    HorizontalDivider()
                    Text("Subscribe to a calendar", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Any ICS or webcal link — public holidays, a shared work calendar, a fixture " +
                            "list. It becomes a read-only coloured calendar and refreshes itself every " +
                            "six hours and on open.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = uiState.subscriptionNameDraft,
                        onValueChange = { onEvent(CalendarUiEvent.SubscriptionNameChanged(it)) },
                        label = { Text("Name") },
                        placeholder = { Text("German holidays") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.subscriptionUrlDraft,
                        onValueChange = { onEvent(CalendarUiEvent.SubscriptionUrlChanged(it)) },
                        label = { Text("ICS link") },
                        placeholder = { Text("https://…/holidays.ics") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (uiState.syncing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onEvent(CalendarUiEvent.Subscribe) },
                            enabled = uiState.subscriptionUrlDraft.isNotBlank() && !uiState.syncing,
                            modifier = Modifier.weight(1f),
                        ) { Text("Subscribe") }
                        OutlinedButton(
                            onClick = { onEvent(CalendarUiEvent.SyncSubscriptions) },
                            enabled = !uiState.syncing,
                        ) { Text("Refresh all") }
                    }
                    Box(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(uiState: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onEvent(CalendarUiEvent.ToggleSearch) }) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Find an event", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { onEvent(CalendarUiEvent.SearchQueryChanged(it)) },
                label = { Text("Title, place or notes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (uiState.searchQuery.isNotBlank() && uiState.searchResults.isEmpty()) {
                Text(
                    "Nothing matches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                items(uiState.searchResults, key = { it.id }) { event ->
                    ListItem(
                        modifier = Modifier.clickable { onEvent(CalendarUiEvent.EditEvent(event)) },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(uiState.colorOf(event)), CircleShape),
                            )
                        },
                        headlineContent = { Text(event.title) },
                        supportingContent = {
                            Text("${DAY_FULL.format(Date(event.startsAt))} · ${TIME.format(Date(event.startsAt))}")
                        },
                    )
                }
            }
            Box(modifier = Modifier.height(16.dp))
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
    CalendarViewMode.MONTH, CalendarViewMode.AGENDA -> MONTH_TITLE.format(Date(anchor))
    CalendarViewMode.WEEK -> {
        val end = anchor + 6 * DAY_MS
        "${DAY_SHORT.format(Date(anchor))} – ${DAY_SHORT.format(Date(end))}"
    }
    CalendarViewMode.DAY -> DAY_TITLE.format(Date(anchor))
}

private val MONTH_TITLE = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
private val DAY_TITLE = SimpleDateFormat("EEE d MMM", Locale.getDefault())
private val DAY_FULL = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
private val DAY_SHORT = SimpleDateFormat("d MMM", Locale.getDefault())
private val WEEKDAY = SimpleDateFormat("EEE", Locale.getDefault())
private val DAY_NUM = SimpleDateFormat("d", Locale.getDefault())
private val TIME = SimpleDateFormat("HH:mm", Locale.getDefault())
private val STAMP = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
