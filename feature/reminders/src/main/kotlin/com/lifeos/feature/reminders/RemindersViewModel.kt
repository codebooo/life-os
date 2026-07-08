package com.lifeos.feature.reminders

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.reminders.ReminderEntity
import com.lifeos.feature.reminders.data.NaturalTimeParser
import com.lifeos.feature.reminders.data.RemindersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RemindersUiState(
    val reminders: List<ReminderEntity> = emptyList(),
    val showEditor: Boolean = false,
    val editorTitle: String = "",
    val editorWhen: String = "",
    val editorParsedAt: Long? = null,
    val editorRecurrence: String = "NONE",
    val error: String? = null,
)

sealed interface RemindersUiEvent {
    data object ToggleEditor : RemindersUiEvent
    data class EditorTitleChanged(val value: String) : RemindersUiEvent
    data class EditorWhenChanged(val value: String) : RemindersUiEvent
    data class EditorRecurrenceChanged(val value: String) : RemindersUiEvent
    data object Save : RemindersUiEvent
    data class SetEnabled(val id: Long, val enabled: Boolean) : RemindersUiEvent
    data class Delete(val id: Long) : RemindersUiEvent
    /** Schedules a real alarm 10 seconds out to verify sound + full-screen. */
    data object TestAlarm : RemindersUiEvent
    data object DismissError : RemindersUiEvent
}

sealed interface RemindersUiEffect

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val remindersRepository: RemindersRepository,
) : LifeViewModel<RemindersUiState, RemindersUiEvent, RemindersUiEffect>(RemindersUiState()) {

    init {
        viewModelScope.launch {
            remindersRepository.observeAll().collect { reminders ->
                updateState { it.copy(reminders = reminders) }
            }
        }
    }

    override fun onEvent(event: RemindersUiEvent) {
        when (event) {
            RemindersUiEvent.ToggleEditor -> updateState {
                it.copy(
                    showEditor = !it.showEditor,
                    editorTitle = "",
                    editorWhen = "",
                    editorParsedAt = null,
                    editorRecurrence = "NONE",
                )
            }
            is RemindersUiEvent.EditorTitleChanged ->
                updateState { it.copy(editorTitle = event.value) }
            is RemindersUiEvent.EditorWhenChanged -> updateState {
                it.copy(
                    editorWhen = event.value,
                    editorParsedAt = NaturalTimeParser.parse(event.value),
                )
            }
            is RemindersUiEvent.EditorRecurrenceChanged ->
                updateState { it.copy(editorRecurrence = event.value) }
            RemindersUiEvent.Save -> save()
            is RemindersUiEvent.SetEnabled -> viewModelScope.launch {
                remindersRepository.setEnabled(event.id, event.enabled)
            }
            is RemindersUiEvent.Delete -> viewModelScope.launch {
                remindersRepository.delete(event.id)
            }
            RemindersUiEvent.TestAlarm -> viewModelScope.launch {
                remindersRepository.create(
                    title = "Alarm test — it works!",
                    at = System.currentTimeMillis() + 10_000L,
                )
                updateState { it.copy(error = "Test alarm set — lock the phone, it rings in 10 s") }
            }
            RemindersUiEvent.DismissError -> updateState { it.copy(error = null) }
        }
    }

    private fun save() {
        val state = uiState.value
        val at = state.editorParsedAt ?: return
        viewModelScope.launch {
            when (
                val result = remindersRepository.create(
                    title = state.editorTitle.trim(),
                    at = at,
                    recurrence = state.editorRecurrence,
                )
            ) {
                is LifeResult.Success -> updateState { it.copy(showEditor = false) }
                is LifeResult.Failure -> updateState { it.copy(error = result.error.message) }
            }
        }
    }
}
