package com.lifeos.feature.memex

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.result.onFailure
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.memex.ArchiveItemEntity
import com.lifeos.feature.memex.data.MemexRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemexUiState(
    val items: List<ArchiveItemEntity> = emptyList(),
    val query: String = "",
    val clipDraft: String = "",
    val annotating: ArchiveItemEntity? = null,
    val annotationDraft: String = "",
    val message: String? = null,
)

sealed interface MemexUiEvent {
    data class QueryChanged(val value: String) : MemexUiEvent
    data class ClipDraftChanged(val value: String) : MemexUiEvent
    data object Clip : MemexUiEvent
    data class StartAnnotate(val item: ArchiveItemEntity) : MemexUiEvent
    data class AnnotationChanged(val value: String) : MemexUiEvent
    data object SaveAnnotation : MemexUiEvent
    data object CancelAnnotation : MemexUiEvent
    data class Delete(val id: Long) : MemexUiEvent
    data object DismissMessage : MemexUiEvent
}

sealed interface MemexUiEffect

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class MemexViewModel @Inject constructor(
    private val repository: MemexRepository,
) : LifeViewModel<MemexUiState, MemexUiEvent, MemexUiEffect>(MemexUiState()) {

    private val queries = MutableStateFlow("")

    init {
        viewModelScope.launch {
            repository.purgeExpired()
            queries.debounce(200).flatMapLatest { repository.observe(it) }.collect { items ->
                updateState { it.copy(items = items) }
            }
        }
    }

    override fun onEvent(event: MemexUiEvent) {
        when (event) {
            is MemexUiEvent.QueryChanged -> {
                updateState { it.copy(query = event.value) }
                queries.value = event.value
            }
            is MemexUiEvent.ClipDraftChanged -> updateState { it.copy(clipDraft = event.value) }
            MemexUiEvent.Clip -> viewModelScope.launch {
                repository.clip(uiState.value.clipDraft, source = "CLIP")
                    .onFailure { error -> updateState { it.copy(message = error.message) } }
                updateState { it.copy(clipDraft = "") }
            }
            is MemexUiEvent.StartAnnotate -> updateState {
                it.copy(annotating = event.item, annotationDraft = event.item.annotation)
            }
            is MemexUiEvent.AnnotationChanged -> updateState { it.copy(annotationDraft = event.value) }
            MemexUiEvent.SaveAnnotation -> viewModelScope.launch {
                val state = uiState.value
                state.annotating?.let { repository.annotate(it, state.annotationDraft) }
                updateState { it.copy(annotating = null, annotationDraft = "") }
            }
            MemexUiEvent.CancelAnnotation -> updateState { it.copy(annotating = null, annotationDraft = "") }
            is MemexUiEvent.Delete -> viewModelScope.launch { repository.delete(event.id) }
            MemexUiEvent.DismissMessage -> updateState { it.copy(message = null) }
        }
    }
}
