package com.lifeos.feature.calendar

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.calendar.CalendarEventEntity
import com.lifeos.core.database.calendar.CalendarListEntity
import com.lifeos.core.datastore.SettingsRepository
import com.lifeos.feature.calendar.data.CalendarPalette
import com.lifeos.feature.calendar.data.CalendarRepository
import com.lifeos.feature.calendar.data.CalendarSubscriptionSync
import com.lifeos.feature.calendar.data.DefaultCalendarRepository
import com.lifeos.feature.calendar.data.EventDraft
import com.lifeos.feature.calendar.data.PlaceLookup
import com.lifeos.feature.calendar.data.PlaceSuggestion
import com.lifeos.feature.calendar.data.ProtonIcsSync
import com.lifeos.feature.calendar.data.SystemCalendarMirror
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class CalendarViewMode { MONTH, WEEK, DAY, AGENDA }

data class CalendarUiState(
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    /** Anchor of the visible period: first ms of the month / week / day. */
    val anchor: Long = 0L,
    /** Events inside the loaded window, already filtered to visible calendars. */
    val events: List<CalendarEventEntity> = emptyList(),
    val calendars: List<CalendarListEntity> = emptyList(),
    /** +1 when the last move went forward, -1 backward — drives the slide direction. */
    val direction: Int = 1,
    /** Selected day (start-of-day millis) for the agenda + new events. */
    val selectedDay: Long = 0L,
    val showEditor: Boolean = false,
    val editingEventId: Long? = null,
    val editorTitle: String = "",
    val editorLocation: String = "",
    val editorNotes: String = "",
    val editorHour: String = "9",
    val editorMinute: String = "0",
    val editorDurationMinutes: String = "60",
    val editorAllDay: Boolean = false,
    val editorCalendarId: Long? = null,
    /** Minutes-before alerts, Proton-style multiples. */
    val editorReminders: List<Int> = listOf(30),
    val editorCustomReminder: String = "",
    val locationSuggestions: List<PlaceSuggestion> = emptyList(),
    val searchingPlaces: Boolean = false,
    /** Ladder granularity in minutes (5, 10, 15, 30, 60) — pinch to change. */
    val minutesPerStep: Int = 60,
    /** Height of one hour in dp; shrinks for the compressed overview. */
    val hourHeightDp: Float = 56f,
    val showConnections: Boolean = false,
    val protonUrlDraft: String = "",
    val syncing: Boolean = false,
    // ---- calendar manager -------------------------------------------------
    val showCalendarManager: Boolean = false,
    val editingCalendarId: Long? = null,
    val calendarNameDraft: String = "",
    val calendarColorDraft: Int = CalendarPalette.default,
    val calendarHexDraft: String = "",
    val subscriptionNameDraft: String = "",
    val subscriptionUrlDraft: String = "",
    // ---- search -----------------------------------------------------------
    val showSearch: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<CalendarEventEntity> = emptyList(),
    val showDatePicker: Boolean = false,
    val error: String? = null,
) {
    val defaultCalendarId: Long? get() = calendars.firstOrNull { it.isDefault }?.id

    /** Colour an event should be drawn in, from the calendar it belongs to. */
    fun colorOf(event: CalendarEventEntity): Int =
        calendars.firstOrNull { it.id == event.calendarId }?.colorArgb ?: CalendarPalette.default

    fun calendarName(id: Long?): String? = calendars.firstOrNull { it.id == id }?.name
}

sealed interface CalendarUiEvent {
    data class SetViewMode(val mode: CalendarViewMode) : CalendarUiEvent
    data object Previous : CalendarUiEvent
    data object Next : CalendarUiEvent
    data object Today : CalendarUiEvent
    data class SelectDay(val dayStart: Long) : CalendarUiEvent
    data class JumpToDate(val millis: Long) : CalendarUiEvent
    data object ToggleDatePicker : CalendarUiEvent

    /** Opens the editor pre-filled for [dayStart] at [hour] (timeline tap / FAB). */
    data class NewEventAt(val dayStart: Long, val hour: Int) : CalendarUiEvent

    /**
     * Drag-to-create (Google-Calendar style): the dragged window becomes the
     * event's start minute and duration, snapped by the visible ladder.
     */
    data class NewEventForRange(
        val dayStart: Long,
        val startMinuteOfDay: Int,
        val durationMinutes: Int,
    ) : CalendarUiEvent

    /** Pinch zoom: minutes represented by one ladder step (5..60). */
    data class SetZoom(val minutesPerStep: Int, val hourHeightDp: Float) : CalendarUiEvent
    data object ToggleEditor : CalendarUiEvent
    data class EditEvent(val event: CalendarEventEntity) : CalendarUiEvent
    data class EditorTitleChanged(val value: String) : CalendarUiEvent
    data class EditorLocationChanged(val value: String) : CalendarUiEvent
    data class EditorLocationPicked(val suggestion: PlaceSuggestion) : CalendarUiEvent
    data class EditorNotesChanged(val value: String) : CalendarUiEvent
    data class EditorHourChanged(val value: String) : CalendarUiEvent
    data class EditorMinuteChanged(val value: String) : CalendarUiEvent
    data class EditorDurationChanged(val value: String) : CalendarUiEvent
    data class EditorDurationPreset(val minutes: Int) : CalendarUiEvent
    data object EditorAllDayToggled : CalendarUiEvent
    data class EditorCalendarPicked(val calendarId: Long) : CalendarUiEvent
    data class EditorReminderToggled(val minutes: Int) : CalendarUiEvent
    data class EditorCustomReminderChanged(val value: String) : CalendarUiEvent
    data object EditorCustomReminderAdded : CalendarUiEvent
    data object Save : CalendarUiEvent
    data class Delete(val eventId: Long) : CalendarUiEvent
    data class Duplicate(val eventId: Long) : CalendarUiEvent
    data class ShiftEvent(val eventId: Long, val days: Int) : CalendarUiEvent
    data object MirrorToSystem : CalendarUiEvent

    // ---- calendars --------------------------------------------------------
    data object ToggleCalendarManager : CalendarUiEvent
    data class CalendarNameDraftChanged(val value: String) : CalendarUiEvent
    data class CalendarColorDraftChanged(val argb: Int) : CalendarUiEvent
    data class CalendarHexDraftChanged(val value: String) : CalendarUiEvent
    data object SaveCalendarDraft : CalendarUiEvent
    data class EditCalendar(val calendarId: Long) : CalendarUiEvent
    data object ClearCalendarDraft : CalendarUiEvent
    data class SetDefaultCalendar(val calendarId: Long) : CalendarUiEvent
    data class ToggleCalendarVisible(val calendarId: Long) : CalendarUiEvent
    data class DeleteCalendar(val calendarId: Long, val deleteEvents: Boolean) : CalendarUiEvent
    data class SubscriptionNameChanged(val value: String) : CalendarUiEvent
    data class SubscriptionUrlChanged(val value: String) : CalendarUiEvent
    data object Subscribe : CalendarUiEvent
    data object SyncSubscriptions : CalendarUiEvent

    // ---- search ----------------------------------------------------------
    data object ToggleSearch : CalendarUiEvent
    data class SearchQueryChanged(val value: String) : CalendarUiEvent

    data object ToggleConnections : CalendarUiEvent
    data class ProtonUrlChanged(val value: String) : CalendarUiEvent
    data object SyncProton : CalendarUiEvent
    data object ExportIcs : CalendarUiEvent
    data object DismissError : CalendarUiEvent
}

sealed interface CalendarUiEffect {
    data class ShareIcs(val ics: String) : CalendarUiEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val systemCalendarMirror: SystemCalendarMirror,
    private val protonIcsSync: ProtonIcsSync,
    private val subscriptionSync: CalendarSubscriptionSync,
    private val placeLookup: PlaceLookup,
    private val settingsRepository: SettingsRepository,
) : LifeViewModel<CalendarUiState, CalendarUiEvent, CalendarUiEffect>(
    CalendarUiState(
        anchor = startOfMonth(System.currentTimeMillis()),
        selectedDay = startOfDay(System.currentTimeMillis()),
    ),
) {

    /** Loaded window follows mode + anchor, padded a period either side. */
    private val window = MutableStateFlow(windowFor(CalendarViewMode.MONTH, startOfMonth(System.currentTimeMillis())))

    private var placeJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            // Hidden calendars are filtered here so every view agrees.
            window
                .flatMapLatest { (start, end) -> calendarRepository.observeWindow(start, end) }
                .combine(calendarRepository.observeCalendars()) { events, calendars ->
                    val hidden = calendars.filter { !it.visible }.map { it.id }.toSet()
                    events.filter { it.calendarId == null || it.calendarId !in hidden } to calendars
                }
                .collect { (events, calendars) ->
                    updateState { it.copy(events = events, calendars = calendars) }
                }
        }
        viewModelScope.launch {
            val url = settingsRepository.protonIcsUrl.first()
            updateState { it.copy(protonUrlDraft = url) }
        }
        viewModelScope.launch {
            // A calendar to save into always exists, even on a first run.
            calendarRepository.ensureDefaultCalendar()
            // Subscriptions are refreshed on open as well as on the six-hour worker.
            runCatching { subscriptionSync.syncAll() }
        }
    }

    override fun onEvent(event: CalendarUiEvent) {
        when (event) {
            is CalendarUiEvent.SetViewMode -> {
                val anchor = anchorFor(event.mode, uiState.value.selectedDay)
                updateState { it.copy(viewMode = event.mode, anchor = anchor) }
                window.value = windowFor(event.mode, anchor)
            }
            CalendarUiEvent.Previous -> shift(-1)
            CalendarUiEvent.Next -> shift(1)
            CalendarUiEvent.Today -> jumpTo(System.currentTimeMillis())
            is CalendarUiEvent.JumpToDate -> {
                updateState { it.copy(showDatePicker = false) }
                jumpTo(event.millis)
            }
            CalendarUiEvent.ToggleDatePicker ->
                updateState { it.copy(showDatePicker = !it.showDatePicker) }
            is CalendarUiEvent.SelectDay -> updateState { it.copy(selectedDay = event.dayStart) }
            is CalendarUiEvent.SetZoom -> updateState {
                it.copy(
                    minutesPerStep = event.minutesPerStep.coerceIn(5, 60),
                    hourHeightDp = event.hourHeightDp.coerceIn(24f, 260f),
                )
            }
            is CalendarUiEvent.NewEventForRange -> updateState {
                it.blankEditor(
                    day = event.dayStart,
                    hour = event.startMinuteOfDay / 60,
                    minute = event.startMinuteOfDay % 60,
                    duration = event.durationMinutes.coerceAtLeast(5),
                )
            }
            is CalendarUiEvent.NewEventAt -> updateState {
                it.blankEditor(day = event.dayStart, hour = event.hour, minute = 0, duration = 60)
            }
            CalendarUiEvent.ToggleEditor -> updateState {
                if (it.showEditor) {
                    it.copy(showEditor = false, editingEventId = null, locationSuggestions = emptyList())
                } else {
                    it.blankEditor(day = it.selectedDay, hour = 9, minute = 0, duration = 60)
                }
            }
            is CalendarUiEvent.EditEvent -> {
                // Task mirrors (negative ids) are edited in Tasks, not here.
                if (event.event.id <= 0) return
                val calendar = Calendar.getInstance().apply { timeInMillis = event.event.startsAt }
                updateState {
                    it.copy(
                        showEditor = true,
                        showSearch = false,
                        editingEventId = event.event.id,
                        editorTitle = event.event.title,
                        editorLocation = event.event.location.orEmpty(),
                        editorNotes = event.event.notes.orEmpty(),
                        editorHour = calendar.get(Calendar.HOUR_OF_DAY).toString(),
                        editorMinute = calendar.get(Calendar.MINUTE).toString(),
                        editorDurationMinutes =
                            ((event.event.endsAt - event.event.startsAt) / 60_000L).toString(),
                        editorAllDay = event.event.allDay,
                        editorCalendarId = event.event.calendarId ?: it.defaultCalendarId,
                        editorReminders = DefaultCalendarRepository.decodeReminders(event.event.reminderMinutes),
                        editorCustomReminder = "",
                        locationSuggestions = emptyList(),
                        selectedDay = startOfDay(event.event.startsAt),
                    )
                }
            }
            is CalendarUiEvent.EditorTitleChanged -> updateState { it.copy(editorTitle = event.value) }
            is CalendarUiEvent.EditorLocationChanged -> {
                updateState { it.copy(editorLocation = event.value) }
                suggestPlaces(event.value)
            }
            is CalendarUiEvent.EditorLocationPicked -> {
                placeJob?.cancel()
                updateState {
                    it.copy(
                        editorLocation = event.suggestion.label,
                        locationSuggestions = emptyList(),
                        searchingPlaces = false,
                    )
                }
            }
            is CalendarUiEvent.EditorNotesChanged -> updateState { it.copy(editorNotes = event.value) }
            is CalendarUiEvent.EditorHourChanged -> updateState { it.copy(editorHour = event.value) }
            is CalendarUiEvent.EditorMinuteChanged -> updateState { it.copy(editorMinute = event.value) }
            is CalendarUiEvent.EditorDurationChanged ->
                updateState { it.copy(editorDurationMinutes = event.value) }
            is CalendarUiEvent.EditorDurationPreset ->
                updateState { it.copy(editorDurationMinutes = event.minutes.toString()) }
            CalendarUiEvent.EditorAllDayToggled ->
                updateState { it.copy(editorAllDay = !it.editorAllDay) }
            is CalendarUiEvent.EditorCalendarPicked ->
                updateState { it.copy(editorCalendarId = event.calendarId) }
            is CalendarUiEvent.EditorReminderToggled -> updateState {
                val current = it.editorReminders
                it.copy(
                    editorReminders = if (event.minutes in current) {
                        current - event.minutes
                    } else {
                        (current + event.minutes).sorted()
                    },
                )
            }
            is CalendarUiEvent.EditorCustomReminderChanged ->
                updateState { it.copy(editorCustomReminder = event.value) }
            CalendarUiEvent.EditorCustomReminderAdded -> updateState {
                val minutes = it.editorCustomReminder.filter { c -> c.isDigit() }.toIntOrNull()
                if (minutes == null || minutes > 60 * 24 * 30) {
                    it.copy(editorCustomReminder = "", error = "Enter minutes between 0 and 43200")
                } else {
                    it.copy(
                        editorReminders = (it.editorReminders + minutes).distinct().sorted(),
                        editorCustomReminder = "",
                    )
                }
            }
            CalendarUiEvent.Save -> save()
            is CalendarUiEvent.Delete -> viewModelScope.launch {
                calendarRepository.delete(event.eventId)
                updateState { it.copy(showEditor = false, editingEventId = null) }
            }
            is CalendarUiEvent.Duplicate -> viewModelScope.launch {
                when (val result = calendarRepository.duplicate(event.eventId)) {
                    is LifeResult.Success -> updateState {
                        it.copy(showEditor = false, editingEventId = null, error = "Copied")
                    }
                    is LifeResult.Failure -> updateState { it.copy(error = result.error.message) }
                }
            }
            is CalendarUiEvent.ShiftEvent -> viewModelScope.launch {
                calendarRepository.shiftBy(event.eventId, event.days * DAY_MS)
            }
            CalendarUiEvent.MirrorToSystem -> viewModelScope.launch {
                when (val result = systemCalendarMirror.mirrorAll()) {
                    is LifeResult.Success -> updateState {
                        it.copy(error = "Mirrored ${result.value} event(s) to the system calendar")
                    }
                    is LifeResult.Failure -> updateState { it.copy(error = result.error.message) }
                }
            }

            CalendarUiEvent.ToggleCalendarManager -> updateState {
                it.copy(showCalendarManager = !it.showCalendarManager).clearCalendarDraft()
            }
            is CalendarUiEvent.CalendarNameDraftChanged ->
                updateState { it.copy(calendarNameDraft = event.value) }
            is CalendarUiEvent.CalendarColorDraftChanged -> updateState {
                it.copy(calendarColorDraft = event.argb, calendarHexDraft = CalendarPalette.hex(event.argb))
            }
            is CalendarUiEvent.CalendarHexDraftChanged -> updateState {
                val parsed = CalendarPalette.parse(event.value)
                it.copy(calendarHexDraft = event.value, calendarColorDraft = parsed)
            }
            CalendarUiEvent.SaveCalendarDraft -> saveCalendarDraft()
            is CalendarUiEvent.EditCalendar -> updateState { state ->
                val calendar = state.calendars.firstOrNull { it.id == event.calendarId }
                    ?: return@updateState state
                state.copy(
                    editingCalendarId = calendar.id,
                    calendarNameDraft = calendar.name,
                    calendarColorDraft = calendar.colorArgb,
                    calendarHexDraft = CalendarPalette.hex(calendar.colorArgb),
                )
            }
            CalendarUiEvent.ClearCalendarDraft -> updateState { it.clearCalendarDraft() }
            is CalendarUiEvent.SetDefaultCalendar -> viewModelScope.launch {
                calendarRepository.setDefaultCalendar(event.calendarId)
            }
            is CalendarUiEvent.ToggleCalendarVisible -> viewModelScope.launch {
                val calendar = uiState.value.calendars.firstOrNull { it.id == event.calendarId }
                    ?: return@launch
                calendarRepository.setCalendarVisible(calendar.id, !calendar.visible)
            }
            is CalendarUiEvent.DeleteCalendar -> viewModelScope.launch {
                calendarRepository.deleteCalendar(event.calendarId, event.deleteEvents)
                updateState { it.clearCalendarDraft() }
            }
            is CalendarUiEvent.SubscriptionNameChanged ->
                updateState { it.copy(subscriptionNameDraft = event.value) }
            is CalendarUiEvent.SubscriptionUrlChanged ->
                updateState { it.copy(subscriptionUrlDraft = event.value) }
            CalendarUiEvent.Subscribe -> subscribe()
            CalendarUiEvent.SyncSubscriptions -> viewModelScope.launch {
                updateState { it.copy(syncing = true) }
                val result = subscriptionSync.syncAll()
                updateState {
                    it.copy(
                        syncing = false,
                        error = when (result) {
                            is LifeResult.Success -> "Subscriptions refreshed: ${result.value} event(s)"
                            is LifeResult.Failure -> result.error.message
                        },
                    )
                }
            }

            CalendarUiEvent.ToggleSearch -> updateState {
                it.copy(showSearch = !it.showSearch, searchQuery = "", searchResults = emptyList())
            }
            is CalendarUiEvent.SearchQueryChanged -> {
                updateState { it.copy(searchQuery = event.value) }
                searchEvents(event.value)
            }

            CalendarUiEvent.ToggleConnections ->
                updateState { it.copy(showConnections = !it.showConnections) }
            is CalendarUiEvent.ProtonUrlChanged -> updateState { it.copy(protonUrlDraft = event.value) }
            CalendarUiEvent.SyncProton -> viewModelScope.launch {
                settingsRepository.setProtonIcsUrl(uiState.value.protonUrlDraft)
                updateState { it.copy(syncing = true) }
                when (val result = protonIcsSync.sync()) {
                    is LifeResult.Success -> updateState {
                        it.copy(syncing = false, error = "Imported ${result.value} new event(s) from Proton")
                    }
                    is LifeResult.Failure -> updateState {
                        it.copy(syncing = false, error = result.error.message)
                    }
                }
            }
            CalendarUiEvent.ExportIcs -> viewModelScope.launch {
                sendEffect(CalendarUiEffect.ShareIcs(calendarRepository.exportIcs()))
            }
            CalendarUiEvent.DismissError -> updateState { it.copy(error = null) }
        }
    }

    private fun jumpTo(at: Long) {
        val day = startOfDay(at)
        val anchor = anchorFor(uiState.value.viewMode, day)
        val forward = anchor >= uiState.value.anchor
        updateState { it.copy(anchor = anchor, selectedDay = day, direction = if (forward) 1 else -1) }
        window.value = windowFor(uiState.value.viewMode, anchor)
    }

    private fun shift(by: Int) {
        val state = uiState.value
        val calendar = Calendar.getInstance().apply { timeInMillis = state.anchor }
        when (state.viewMode) {
            CalendarViewMode.MONTH -> calendar.add(Calendar.MONTH, by)
            CalendarViewMode.WEEK -> calendar.add(Calendar.WEEK_OF_YEAR, by)
            CalendarViewMode.DAY -> calendar.add(Calendar.DAY_OF_YEAR, by)
            CalendarViewMode.AGENDA -> calendar.add(Calendar.MONTH, by)
        }
        val anchor = calendar.timeInMillis
        updateState {
            it.copy(
                anchor = anchor,
                direction = by,
                // Keep the selection inside the visible period.
                selectedDay = when (state.viewMode) {
                    CalendarViewMode.MONTH, CalendarViewMode.AGENDA -> anchor
                    else -> anchor
                },
            )
        }
        window.value = windowFor(state.viewMode, anchor)
    }

    /** Debounced Nominatim lookup; a short or empty box clears the list. */
    private fun suggestPlaces(query: String) {
        placeJob?.cancel()
        if (query.trim().length < 3) {
            updateState { it.copy(locationSuggestions = emptyList(), searchingPlaces = false) }
            return
        }
        placeJob = viewModelScope.launch {
            delay(450)
            updateState { it.copy(searchingPlaces = true) }
            val results = placeLookup.suggest(query)
            updateState { it.copy(locationSuggestions = results, searchingPlaces = false) }
        }
    }

    private fun searchEvents(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            updateState { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(200)
            val results = calendarRepository.search(query)
            updateState { it.copy(searchResults = results) }
        }
    }

    private fun saveCalendarDraft() {
        val state = uiState.value
        val name = state.calendarNameDraft.trim()
        if (name.isEmpty()) {
            updateState { it.copy(error = "Name the calendar first") }
            return
        }
        viewModelScope.launch {
            val editing = state.editingCalendarId
            if (editing != null) {
                calendarRepository.renameCalendar(editing, name)
                calendarRepository.recolourCalendar(editing, state.calendarColorDraft)
            } else {
                val result = calendarRepository.createCalendar(
                    name = name,
                    colorArgb = state.calendarColorDraft,
                )
                if (result is LifeResult.Failure) {
                    updateState { it.copy(error = result.error.message) }
                    return@launch
                }
            }
            updateState { it.clearCalendarDraft() }
        }
    }

    private fun subscribe() {
        val state = uiState.value
        val url = state.subscriptionUrlDraft.trim()
        val name = state.subscriptionNameDraft.trim().ifEmpty { "Subscribed calendar" }
        if (!url.contains("://")) {
            updateState { it.copy(error = "Paste a webcal:// or https:// ICS link") }
            return
        }
        viewModelScope.launch {
            updateState { it.copy(syncing = true) }
            val used = state.calendars.map { it.colorArgb }
            val created = calendarRepository.createCalendar(
                name = name,
                colorArgb = CalendarPalette.nextUnused(used),
                subscriptionUrl = url,
            )
            when (created) {
                is LifeResult.Success -> {
                    val synced = subscriptionSync.syncOne(created.value)
                    updateState {
                        it.copy(
                            syncing = false,
                            subscriptionNameDraft = "",
                            subscriptionUrlDraft = "",
                            error = when (synced) {
                                is LifeResult.Success -> "Subscribed: ${synced.value} event(s) imported"
                                is LifeResult.Failure -> synced.error.message
                            },
                        )
                    }
                }
                is LifeResult.Failure -> updateState {
                    it.copy(syncing = false, error = created.error.message)
                }
            }
        }
    }

    private fun save() {
        val state = uiState.value
        val title = state.editorTitle.trim()
        if (title.isEmpty()) return
        val hour = state.editorHour.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val minute = state.editorMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val duration = state.editorDurationMinutes.toLongOrNull()?.coerceAtLeast(5) ?: 60L
        val startsAt = if (state.editorAllDay) {
            state.selectedDay
        } else {
            state.selectedDay + hour * 3_600_000L + minute * 60_000L
        }
        val endsAt = if (state.editorAllDay) state.selectedDay + DAY_MS else startsAt + duration * 60_000L
        val draft = EventDraft(
            title = title,
            startsAt = startsAt,
            endsAt = endsAt,
            location = state.editorLocation.trim().ifEmpty { null },
            notes = state.editorNotes.trim().ifEmpty { null },
            allDay = state.editorAllDay,
            calendarId = state.editorCalendarId ?: state.defaultCalendarId,
            reminderMinutes = state.editorReminders,
        )
        viewModelScope.launch {
            val result = if (state.editingEventId != null) {
                calendarRepository.update(state.editingEventId, draft)
            } else {
                calendarRepository.create(draft)
            }
            when (result) {
                is LifeResult.Success -> updateState {
                    it.copy(showEditor = false, editingEventId = null, locationSuggestions = emptyList())
                }
                is LifeResult.Failure -> updateState { it.copy(error = result.error.message) }
            }
        }
    }

    private fun CalendarUiState.blankEditor(day: Long, hour: Int, minute: Int, duration: Int) = copy(
        selectedDay = day,
        showEditor = true,
        editingEventId = null,
        editorTitle = "",
        editorLocation = "",
        editorNotes = "",
        editorHour = hour.toString(),
        editorMinute = minute.toString(),
        editorDurationMinutes = duration.toString(),
        editorAllDay = false,
        editorCalendarId = defaultCalendarId,
        editorReminders = listOf(30),
        editorCustomReminder = "",
        locationSuggestions = emptyList(),
    )

    private fun CalendarUiState.clearCalendarDraft() = copy(
        editingCalendarId = null,
        calendarNameDraft = "",
        calendarColorDraft = CalendarPalette.nextUnused(calendars.map { it.colorArgb }),
        calendarHexDraft = "",
    )

    companion object {
        /** Alert offsets the editor offers, mirroring Proton Calendar's set. */
        val REMINDER_PRESETS = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080)

        /** Quick lengths in the editor. */
        val DURATION_PRESETS = listOf(15, 30, 45, 60, 90, 120, 240)

        fun startOfDay(at: Long): Long = Calendar.getInstance().apply {
            timeInMillis = at
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun startOfMonth(at: Long): Long = Calendar.getInstance().apply {
            timeInMillis = startOfDay(at)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

        /** Monday-start week containing [at]. */
        fun startOfWeek(at: Long): Long = Calendar.getInstance().apply {
            timeInMillis = startOfDay(at)
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }.timeInMillis

        fun anchorFor(mode: CalendarViewMode, day: Long): Long = when (mode) {
            CalendarViewMode.MONTH, CalendarViewMode.AGENDA -> startOfMonth(day)
            CalendarViewMode.WEEK -> startOfWeek(day)
            CalendarViewMode.DAY -> startOfDay(day)
        }

        /**
         * Loaded range. Padded a whole period either side so a swipe lands on a
         * page that already has its events, which is what makes the slide look
         * instant instead of flashing empty.
         */
        fun windowFor(mode: CalendarViewMode, anchor: Long): Pair<Long, Long> = when (mode) {
            CalendarViewMode.MONTH, CalendarViewMode.AGENDA -> {
                val gridStart = startOfWeek(anchor)
                (gridStart - 42L * DAY_MS) to (gridStart + 84L * DAY_MS)
            }
            CalendarViewMode.WEEK -> (anchor - 7L * DAY_MS) to (anchor + 14L * DAY_MS)
            CalendarViewMode.DAY -> (anchor - DAY_MS) to (anchor + 2L * DAY_MS)
        }

        const val DAY_MS = 86_400_000L
    }
}
