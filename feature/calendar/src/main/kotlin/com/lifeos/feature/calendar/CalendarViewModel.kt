package com.lifeos.feature.calendar

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.calendar.CalendarEventEntity
import com.lifeos.feature.calendar.data.CalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class CalendarUiState(
    /** First millisecond of the shown month. */
    val monthStart: Long = 0L,
    val monthEvents: List<CalendarEventEntity> = emptyList(),
    /** Selected day (start-of-day millis) for the agenda below the grid. */
    val selectedDay: Long = 0L,
    val showEditor: Boolean = false,
    val editorTitle: String = "",
    val editorLocation: String = "",
    val editorHour: String = "9",
    val editorDurationMinutes: String = "60",
    val editorRemind: Boolean = true,
    val error: String? = null,
)

sealed interface CalendarUiEvent {
    data object PreviousMonth : CalendarUiEvent
    data object NextMonth : CalendarUiEvent
    data class SelectDay(val dayStart: Long) : CalendarUiEvent
    data object ToggleEditor : CalendarUiEvent
    data class EditorTitleChanged(val value: String) : CalendarUiEvent
    data class EditorLocationChanged(val value: String) : CalendarUiEvent
    data class EditorHourChanged(val value: String) : CalendarUiEvent
    data class EditorDurationChanged(val value: String) : CalendarUiEvent
    data object EditorRemindToggled : CalendarUiEvent
    data object Save : CalendarUiEvent
    data class Delete(val eventId: Long) : CalendarUiEvent
    data object DismissError : CalendarUiEvent
}

sealed interface CalendarUiEffect

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
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
    }

    override fun onEvent(event: CalendarUiEvent) {
        when (event) {
            CalendarUiEvent.PreviousMonth -> shiftMonth(-1)
            CalendarUiEvent.NextMonth -> shiftMonth(1)
            is CalendarUiEvent.SelectDay -> updateState { it.copy(selectedDay = event.dayStart) }
            CalendarUiEvent.ToggleEditor -> updateState {
                it.copy(showEditor = !it.showEditor, editorTitle = "", editorLocation = "")
            }
            is CalendarUiEvent.EditorTitleChanged -> updateState { it.copy(editorTitle = event.value) }
            is CalendarUiEvent.EditorLocationChanged ->
                updateState { it.copy(editorLocation = event.value) }
            is CalendarUiEvent.EditorHourChanged -> updateState { it.copy(editorHour = event.value) }
            is CalendarUiEvent.EditorDurationChanged ->
                updateState { it.copy(editorDurationMinutes = event.value) }
            CalendarUiEvent.EditorRemindToggled ->
                updateState { it.copy(editorRemind = !it.editorRemind) }
            CalendarUiEvent.Save -> save()
            is CalendarUiEvent.Delete -> viewModelScope.launch {
                calendarRepository.delete(event.eventId)
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
        val duration = state.editorDurationMinutes.toLongOrNull()?.coerceAtLeast(5) ?: 60L
        val startsAt = state.selectedDay + hour * 3_600_000L
        viewModelScope.launch {
            when (
                val result = calendarRepository.create(
                    title = title,
                    startsAt = startsAt,
                    endsAt = startsAt + duration * 60_000L,
                    location = state.editorLocation.trim().ifEmpty { null },
                    remindMinutesBefore = if (state.editorRemind) 30 else null,
                )
            ) {
                is LifeResult.Success -> updateState { it.copy(showEditor = false) }
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
