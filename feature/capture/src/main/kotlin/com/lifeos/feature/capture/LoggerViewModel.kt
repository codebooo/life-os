package com.lifeos.feature.capture

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.capture.LogEntryEntity
import com.lifeos.core.database.capture.LogFormEntity
import com.lifeos.feature.capture.data.CaptureRepository
import com.lifeos.feature.capture.data.DefaultCaptureRepository
import com.lifeos.feature.capture.data.LogFieldSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoggerMode { FORMS, EDITOR, ENTRIES }

data class LoggerUiState(
    val mode: LoggerMode = LoggerMode.FORMS,
    val forms: List<LogFormEntity> = emptyList(),
    // Form editor
    val editorName: String = "",
    val editorFieldLines: String = "",
    // Entries view
    val activeForm: LogFormEntity? = null,
    val activeFields: List<LogFieldSpec> = emptyList(),
    val entries: List<LogEntryEntity> = emptyList(),
    val entryDraft: Map<String, String> = emptyMap(),
    val error: String? = null,
)

sealed interface LoggerUiEvent {
    data object NewForm : LoggerUiEvent
    data class EditorNameChanged(val value: String) : LoggerUiEvent
    data class EditorFieldsChanged(val value: String) : LoggerUiEvent
    data object SaveForm : LoggerUiEvent
    data class OpenForm(val form: LogFormEntity) : LoggerUiEvent
    data class DeleteForm(val formId: Long) : LoggerUiEvent
    data class DraftChanged(val field: String, val value: String) : LoggerUiEvent
    data object AddEntry : LoggerUiEvent
    data object Back : LoggerUiEvent
    data object DismissError : LoggerUiEvent
}

sealed interface LoggerUiEffect

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LoggerViewModel @Inject constructor(
    private val captureRepository: CaptureRepository,
) : LifeViewModel<LoggerUiState, LoggerUiEvent, LoggerUiEffect>(LoggerUiState()) {

    private val activeFormId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            captureRepository.observeForms().collect { forms ->
                updateState { it.copy(forms = forms) }
            }
        }
        viewModelScope.launch {
            activeFormId
                .flatMapLatest { id -> if (id == null) emptyFlow() else captureRepository.observeEntries(id) }
                .collect { entries -> updateState { it.copy(entries = entries) } }
        }
    }

    override fun onEvent(event: LoggerUiEvent) {
        when (event) {
            LoggerUiEvent.NewForm -> updateState {
                it.copy(mode = LoggerMode.EDITOR, editorName = "", editorFieldLines = "")
            }
            is LoggerUiEvent.EditorNameChanged -> updateState { it.copy(editorName = event.value) }
            is LoggerUiEvent.EditorFieldsChanged -> updateState { it.copy(editorFieldLines = event.value) }
            LoggerUiEvent.SaveForm -> saveForm()
            is LoggerUiEvent.OpenForm -> openForm(event.form)
            is LoggerUiEvent.DeleteForm -> viewModelScope.launch {
                captureRepository.deleteForm(event.formId)
            }
            is LoggerUiEvent.DraftChanged -> updateState {
                it.copy(entryDraft = it.entryDraft + (event.field to event.value))
            }
            LoggerUiEvent.AddEntry -> addEntry()
            LoggerUiEvent.Back -> updateState { it.copy(mode = LoggerMode.FORMS, activeForm = null) }
            LoggerUiEvent.DismissError -> updateState { it.copy(error = null) }
        }
    }

    private fun saveForm() {
        val state = uiState.value
        val name = state.editorName.trim()
        if (name.isEmpty()) return
        val fields = parseFieldLines(state.editorFieldLines)
        if (fields.isEmpty()) {
            updateState { it.copy(error = "Add at least one field (one per line, e.g. \"mood: rating\")") }
            return
        }
        viewModelScope.launch {
            when (val result = captureRepository.createForm(name, fields)) {
                is LifeResult.Success -> updateState { it.copy(mode = LoggerMode.FORMS) }
                is LifeResult.Failure -> updateState { it.copy(error = result.error.message) }
            }
        }
    }

    private fun openForm(form: LogFormEntity) {
        activeFormId.value = form.id
        updateState {
            it.copy(
                mode = LoggerMode.ENTRIES,
                activeForm = form,
                activeFields = DefaultCaptureRepository.parseFields(form.fieldsJson),
                entryDraft = emptyMap(),
            )
        }
    }

    private fun addEntry() {
        val state = uiState.value
        val form = state.activeForm ?: return
        val values = state.entryDraft.filterValues { it.isNotBlank() }
        if (values.isEmpty()) return
        viewModelScope.launch {
            when (val result = captureRepository.addEntry(form.id, values)) {
                is LifeResult.Success -> updateState { it.copy(entryDraft = emptyMap()) }
                is LifeResult.Failure -> updateState { it.copy(error = result.error.message) }
            }
        }
    }

    /**
     * One field per line, "name: type[: unit]" with auto-detected type when
     * omitted ([src 11] — a form is as easy as typing a line per field).
     */
    internal fun parseFieldLines(lines: String): List<LogFieldSpec> =
        lines.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val parts = line.split(":").map { it.trim() }
                val name = parts[0]
                val type = parts.getOrNull(1)?.lowercase()?.takeIf { it.isNotEmpty() }
                    ?: inferType(name)
                LogFieldSpec(name = name, type = type, unit = parts.getOrNull(2))
            }

    private fun inferType(name: String): String = when {
        name.contains("date") || name.contains("day") -> "date"
        name.contains("time") || name.contains("duration") -> "duration"
        name.contains("rating") || name.contains("mood") || name.contains("score") -> "rating"
        name.contains("count") || name.contains("weight") || name.contains("amount") ||
            name.contains("hours") || name.contains("kg") || name.contains("km") -> "number"
        else -> "text"
    }
}
