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
    /** First millisecond of the shown month. */
    val monthStart: Long = 0L,
    val monthEvents: List<CalendarEventEntity> = emptyList(),
    /** Selected day (start-of-day millis) for the agenda below the grid. */
    val selectedDay: Long = 0L,
    val showEditor: Boolean = false,
    /** Non-null while editing an existing event. */
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
    data object PreviousMonth : CalendarUiEvent
    data object NextMonth : CalendarUiEvent
    data class SelectDay(val dayStart: Long) : CalendarUiEvent
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
    /** Hand the exported ICS text to a share sheet. */
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
        monthStart = startOfMonth(System.currentTimeMillis()),
        selectedDay = startOfDay(System.currentTimeMillis()),
    ),
) {

    private val monthStart = MutableStateFlow(startOfMonth(System.currentTimeMillis()))

    init {
        viewModelScope.launch {
            monthStart
                .flatMapLatest { start ->
                    calendarRepository.observeWindow(start, endOfMonth(start))
                }
                .collect { events -> updateState { it.copy(monthEvents = events) } }
        }
        viewModelScope.launch {
            val url = settingsRepository.protonIcsUrl.first()
            updateState { it.copy(protonUrlDraft = url) }
        }
    }

    override fun onEvent(event: CalendarUiEvent) {
        when (event) {
            is CalendarUiEvent.SetViewMode -> updateState { it.copy(viewMode = event.mode) }
            CalendarUiEvent.PreviousMonth -> shiftMonth(-1)
            CalendarUiEvent.NextMonth -> shiftMonth(1)
            is CalendarUiEvent.SelectDay -> updateState { it.copy(selectedDay = event.dayStart) }
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

    private fun shiftMonth(by: Int) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = monthStart.value
            add(Calendar.MONTH, by)
        }
        monthStart.value = calendar.timeInMillis
        updateState { it.copy(monthStart = calendar.timeInMillis) }
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

        fun endOfMonth(monthStart: Long): Long = Calendar.getInstance().apply {
            timeInMillis = monthStart
            add(Calendar.MONTH, 1)
        }.timeInMillis
    }
}

private fun startOfMonth(at: Long) = CalendarViewModel.startOfMonth(at)
private fun startOfDay(at: Long) = CalendarViewModel.startOfDay(at)
