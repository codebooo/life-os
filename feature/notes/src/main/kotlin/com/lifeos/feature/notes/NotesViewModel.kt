package com.lifeos.feature.notes

import androidx.lifecycle.viewModelScope
import com.lifeos.core.ai.rag.RagChunk
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.notes.NoteEntity
import com.lifeos.feature.notes.data.AskNotesUseCase
import com.lifeos.feature.notes.data.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The three internal surfaces of the Notes feature. */
enum class NotesScreenMode { LIST, EDITOR, ASK }

data class EditorState(
    val noteId: Long? = null,
    val title: String = "",
    val body: String = "",
    val sensitive: Boolean = false,
    val preview: Boolean = false,
    val backlinks: List<NoteEntity> = emptyList(),
)

data class AskState(
    val question: String = "",
    val thinking: Boolean = false,
    val answer: String? = null,
    val citations: List<RagChunk> = emptyList(),
)

data class NotesUiState(
    val mode: NotesScreenMode = NotesScreenMode.LIST,
    val notes: List<NoteEntity> = emptyList(),
    val query: String = "",
    val editor: EditorState = EditorState(),
    val ask: AskState = AskState(),
    val error: String? = null,
)

sealed interface NotesUiEvent {
    data class QueryChanged(val value: String) : NotesUiEvent
    data object NewNote : NotesUiEvent
    data class OpenNote(val id: Long) : NotesUiEvent
    data class DeleteNote(val id: Long) : NotesUiEvent
    data class TitleChanged(val value: String) : NotesUiEvent
    data class BodyChanged(val value: String) : NotesUiEvent
    data object ToggleSensitive : NotesUiEvent
    data object TogglePreview : NotesUiEvent
    data object SaveNote : NotesUiEvent
    data object BackToList : NotesUiEvent
    data object OpenAsk : NotesUiEvent
    data class AskQuestionChanged(val value: String) : NotesUiEvent
    data object Ask : NotesUiEvent
    data object DismissError : NotesUiEvent
}

sealed interface NotesUiEffect

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val askNotesUseCase: AskNotesUseCase,
) : LifeViewModel<NotesUiState, NotesUiEvent, NotesUiEffect>(NotesUiState()) {

    private val query = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            query.flatMapLatest { notesRepository.observeNotes(it) }
                .collect { notes -> updateState { it.copy(notes = notes) } }
        }
    }

    override fun onEvent(event: NotesUiEvent) {
        when (event) {
            is NotesUiEvent.QueryChanged -> {
                query.value = event.value.ifBlank { null }
                updateState { it.copy(query = event.value) }
            }
            NotesUiEvent.NewNote ->
                updateState { it.copy(mode = NotesScreenMode.EDITOR, editor = EditorState()) }
            is NotesUiEvent.OpenNote -> openNote(event.id)
            is NotesUiEvent.DeleteNote -> viewModelScope.launch { notesRepository.delete(event.id) }
            is NotesUiEvent.TitleChanged ->
                updateState { it.copy(editor = it.editor.copy(title = event.value)) }
            is NotesUiEvent.BodyChanged ->
                updateState { it.copy(editor = it.editor.copy(body = event.value)) }
            NotesUiEvent.ToggleSensitive ->
                updateState { it.copy(editor = it.editor.copy(sensitive = !it.editor.sensitive)) }
            NotesUiEvent.TogglePreview ->
                updateState { it.copy(editor = it.editor.copy(preview = !it.editor.preview)) }
            NotesUiEvent.SaveNote -> saveNote()
            NotesUiEvent.BackToList -> updateState { it.copy(mode = NotesScreenMode.LIST) }
            NotesUiEvent.OpenAsk -> updateState { it.copy(mode = NotesScreenMode.ASK) }
            is NotesUiEvent.AskQuestionChanged ->
                updateState { it.copy(ask = it.ask.copy(question = event.value)) }
            NotesUiEvent.Ask -> ask()
            NotesUiEvent.DismissError -> updateState { it.copy(error = null) }
        }
    }

    private fun openNote(id: Long) {
        viewModelScope.launch {
            when (val loaded = notesRepository.load(id)) {
                is LifeResult.Success -> updateState {
                    it.copy(
                        mode = NotesScreenMode.EDITOR,
                        editor = EditorState(
                            noteId = loaded.value.note.id,
                            title = loaded.value.note.title,
                            body = loaded.value.body,
                            sensitive = loaded.value.note.bodyVaultRef != null,
                            backlinks = loaded.value.backlinks,
                        ),
                    )
                }
                is LifeResult.Failure ->
                    updateState { it.copy(error = loaded.error.message) }
            }
        }
    }

    private fun saveNote() {
        val editor = uiState.value.editor
        val title = editor.title.trim().ifEmpty { "Untitled" }
        if (editor.body.isBlank() && editor.title.isBlank()) return
        viewModelScope.launch {
            when (
                val saved =
                    notesRepository.save(editor.noteId, title, editor.body, editor.sensitive)
            ) {
                is LifeResult.Success -> updateState { it.copy(mode = NotesScreenMode.LIST) }
                is LifeResult.Failure -> updateState { it.copy(error = saved.error.message) }
            }
        }
    }

    private fun ask() {
        val question = uiState.value.ask.question.trim()
        if (question.isEmpty() || uiState.value.ask.thinking) return
        updateState { it.copy(ask = it.ask.copy(thinking = true, answer = null, citations = emptyList())) }
        viewModelScope.launch {
            val retrieved = askNotesUseCase.retrieve(question)
            if (retrieved.isEmpty()) {
                updateState {
                    it.copy(ask = it.ask.copy(thinking = false, answer = "No matching notes found."))
                }
                return@launch
            }
            when (val result = askNotesUseCase.answer(question, retrieved)) {
                is LifeResult.Success -> updateState {
                    it.copy(
                        ask = it.ask.copy(
                            thinking = false,
                            answer = result.value.answer,
                            citations = result.value.citations,
                        ),
                    )
                }
                is LifeResult.Failure -> updateState {
                    it.copy(
                        // Retrieval is always local; surface the chunks even when
                        // no engine could phrase an answer.
                        ask = it.ask.copy(thinking = false, answer = null, citations = retrieved),
                        error = result.error.message,
                    )
                }
            }
        }
    }
}
