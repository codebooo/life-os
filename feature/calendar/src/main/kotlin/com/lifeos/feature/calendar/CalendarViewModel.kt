package com.lifeos.feature.calendar

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.calendar.CalendarEventEntity
import com.lifeos.core.datastore.SettingsRepository
import com.lifeos.feature.calendar.data.CalendarRepository
import com.lifeos.feature.calendar.data.ProtonIcsSync
import com.lifeos.feature.calendar.data.SystemCalendarMirror
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class CalendarViewMode { MONTH, WEEK, DAY }

data class CalendarUiState(
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    /** Anchor of the visible period: first ms of the month / week / day. */
    val anchor: Long = 0L,
    /** All events inside the loaded window (padded month grid or week). */
    val events: List<CalendarEventEntity> = emptyList(),
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
    val editorRemind: Boolean = true,
    val showConnections: Boolean = false,
    val protonUrlDraft: String = "",
    val syncing: Boolean = false,
    val error: String? = null,
)

sealed interface CalendarUiEvent {
    data class SetViewMode(val mode: CalendarViewMode) : CalendarUiEvent
    data object Previous : CalendarUiEvent
    data object Next : CalendarUiEvent
    data object Today : CalendarUiEvent
    data class SelectDay(val dayStart: Long) : CalendarUiEvent
    /** Opens the editor pre-filled for [dayStart] at [hour] (timeline tap / FAB). */
    data class NewEventAt(val dayStart: Long, val hour: Int) : CalendarUiEvent
    data object ToggleEditor : CalendarUiEvent
    data class EditEvent(val event: CalendarEventEntity) : CalendarUiEvent
    data class EditorTitleChanged(val value: String) : CalendarUiEvent
    data class EditorLocationChanged(val value: String) : CalendarUiEvent
    data class EditorNotesChanged(val value: String) : CalendarUiEvent
    data class EditorHourChanged(val value: String) : CalendarUiEvent
    data class EditorMinuteChanged(val value: String) : CalendarUiEvent
    data class EditorDurationChanged(val value: String) : CalendarUiEvent
    data object EditorAllDayToggled : CalendarUiEvent
    data object EditorRemindToggled : CalendarUiEvent
    data object Save : CalendarUiEvent
    data class Delete(val eventId: Long) : CalendarUiEvent
    data object MirrorToSystem : CalendarUiEvent
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
    private val settingsRepository: SettingsRepository,
) : LifeViewModel<CalendarUiState, CalendarUiEvent, CalendarUiEffect>(
    CalendarUiState(
        anchor = startOfMonth(System.currentTimeMillis()),
        selectedDay = startOfDay(System.currentTimeMillis()),
    ),
) {

    /** Loaded window follows mode + anchor; padded so month grids fill fully. */
    private val window = MutableStateFlow(windowFor(CalendarViewMode.MONTH, startOfMonth(System.currentTimeMillis())))

    init {
        viewModelScope.launch {
            window
                .flatMapLatest { (start, end) -> calendarRepository.observeWindow(start, end) }
                .collect { events -> updateState { it.copy(events = events) } }
        }
        viewModelScope.launch {
            val url = settingsRepository.protonIcsUrl.first()
            updateState { it.copy(protonUrlDraft = url) }
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
            CalendarUiEvent.Today -> {
                val today = startOfDay(System.currentTimeMillis())
                val anchor = anchorFor(uiState.value.viewMode, today)
                updateState { it.copy(anchor = anchor, selectedDay = today) }
                window.value = windowFor(uiState.value.viewMode, anchor)
            }
            is CalendarUiEvent.SelectDay -> updateState { it.copy(selectedDay = event.dayStart) }
            is CalendarUiEvent.NewEventAt -> updateState {
                it.copy(
                    selectedDay = event.dayStart,
                    showEditor = true,
                    editingEventId = null,
                    editorTitle = "",
                    editorLocation = "",
                    editorNotes = "",
                    editorHour = event.hour.toString(),
                    editorMinute = "0",
                    editorDurationMinutes = "60",
                    editorAllDay = false,
                    editorRemind = true,
                )
            }
            CalendarUiEvent.ToggleEditor -> updateState {
                it.copy(
                    showEditor = !it.showEditor,
                    editingEventId = null,
                    editorTitle = "",
                    editorLocation = "",
                    editorNotes = "",
                    editorHour = "9",
                    editorMinute = "0",
                    editorDurationMinutes = "60",
                    editorAllDay = false,
                )
            }
            is CalendarUiEvent.EditEvent -> {
                // Task mirrors (negative ids) are edited in Tasks, not here.
                if (event.event.id <= 0) return
                val calendar = Calendar.getInstance().apply { timeInMillis = event.event.startsAt }
                updateState {
                    it.copy(
                        showEditor = true,
                        editingEventId = event.event.id,
                        editorTitle = event.event.title,
                        editorLocation = event.event.location.orEmpty(),
                        editorNotes = event.event.notes.orEmpty(),
                        editorHour = calendar.get(Calendar.HOUR_OF_DAY).toString(),
                        editorMinute = calendar.get(Calendar.MINUTE).toString(),
                        editorDurationMinutes =
                            ((event.event.endsAt - event.event.startsAt) / 60_000L).toString(),
                        editorAllDay = event.event.allDay,
                        selectedDay = startOfDay(event.event.startsAt),
                    )
                }
            }
            is CalendarUiEvent.EditorTitleChanged -> updateState { it.copy(editorTitle = event.value) }
            is CalendarUiEvent.EditorLocationChanged ->
                updateState { it.copy(editorLocation = event.value) }
            is CalendarUiEvent.EditorNotesChanged -> updateState { it.copy(editorNotes = event.value) }
            is CalendarUiEvent.EditorHourChanged -> updateState { it.copy(editorHour = event.value) }
            is CalendarUiEvent.EditorMinuteChanged -> updateState { it.copy(editorMinute = event.value) }
            is CalendarUiEvent.EditorDurationChanged ->
                updateState { it.copy(editorDurationMinutes = event.value) }
            CalendarUiEvent.EditorAllDayToggled ->
                updateState { it.copy(editorAllDay = !it.editorAllDay) }
            CalendarUiEvent.EditorRemindToggled ->
                updateState { it.copy(editorRemind = !it.editorRemind) }
            CalendarUiEvent.Save -> save()
            is CalendarUiEvent.Delete -> viewModelScope.launch {
                calendarRepository.delete(event.eventId)
                updateState { it.copy(showEditor = false, editingEventId = null) }
            }
            CalendarUiEvent.MirrorToSystem -> viewModelScope.launch {
                when (val result = systemCalendarMirror.mirrorAll()) {
                    is LifeResult.Success -> updateState {
                        it.copy(error = "Mirrored ${result.value} event(s) to the system calendar")
                    }
                    is LifeResult.Failure -> updateState { it.copy(error = result.error.message) }
                }
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

    private fun shift(by: Int) {
        val state = uiState.value
        val calendar = Calendar.getInstance().apply { timeInMillis = state.anchor }
        when (state.viewMode) {
            CalendarViewMode.MONTH -> calendar.add(Calendar.MONTH, by)
            CalendarViewMode.WEEK -> calendar.add(Calendar.WEEK_OF_YEAR, by)
            CalendarViewMode.DAY -> calendar.add(Calendar.DAY_OF_YEAR, by)
        }
        val anchor = calendar.timeInMillis
        updateState {
            it.copy(
                anchor = anchor,
                // Keep the selection inside the visible period.
                selectedDay = when (state.viewMode) {
                    CalendarViewMode.DAY -> anchor
                    else -> anchor
                },
            )
        }
        window.value = windowFor(state.viewMode, anchor)
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
        val endsAt = if (state.editorAllDay) state.selectedDay + 86_400_000L else startsAt + duration * 60_000L
        viewModelScope.launch {
            val result = if (state.editingEventId != null) {
                calendarRepository.update(
                    eventId = state.editingEventId,
                    title = title,
                    startsAt = startsAt,
                    endsAt = endsAt,
                    location = state.editorLocation.trim().ifEmpty { null },
                    notes = state.editorNotes.trim().ifEmpty { null },
                    allDay = state.editorAllDay,
                )
            } else {
                calendarRepository.create(
                    title = title,
                    startsAt = startsAt,
                    endsAt = endsAt,
                    location = state.editorLocation.trim().ifEmpty { null },
                    notes = state.editorNotes.trim().ifEmpty { null },
                    allDay = state.editorAllDay,
                    remindMinutesBefore = if (state.editorRemind) 30 else null,
                )
            }
            when (result) {
                is LifeResult.Success -> updateState { it.copy(showEditor = false, editingEventId = null) }
                is LifeResult.Failure -> updateState { it.copy(error = result.error.message) }
            }
        }
    }

    companion object {
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
            CalendarViewMode.MONTH -> startOfMonth(day)
            CalendarViewMode.WEEK -> startOfWeek(day)
            CalendarViewMode.DAY -> startOfDay(day)
        }

        /** Loaded range, padded so the 6x7 month grid's leading/trailing days have events too. */
        fun windowFor(mode: CalendarViewMode, anchor: Long): Pair<Long, Long> = when (mode) {
            CalendarViewMode.MONTH -> {
                val gridStart = startOfWeek(anchor)
                gridStart to gridStart + 42L * DAY_MS
            }
            CalendarViewMode.WEEK -> anchor to anchor + 7L * DAY_MS
            CalendarViewMode.DAY -> anchor to anchor + DAY_MS
        }

        const val DAY_MS = 86_400_000L
    }
}
