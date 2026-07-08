package com.lifeos.feature.chat

import androidx.lifecycle.viewModelScope
import com.lifeos.core.ai.model.AiEngineId
import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.chat.AiConversationEntity
import com.lifeos.core.database.chat.AiMessageEntity
import com.lifeos.feature.chat.data.ChatRepository
import com.lifeos.feature.chat.data.ReplyProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val conversations: List<AiConversationEntity> = emptyList(),
    val activeConversationId: Long? = null,
    val messages: List<AiMessageEntity> = emptyList(),
    val input: String = "",
    val streaming: Boolean = false,
    val activeEngine: AiEngineId? = null,
    val error: String? = null,
    val showConversations: Boolean = false,
    val showSettings: Boolean = false,
    /** Manual context (notes, pasted files) attached to every prompt. */
    val contextText: String = "",
    val showContext: Boolean = false,
)

sealed interface ChatUiEvent {
    data class InputChanged(val value: String) : ChatUiEvent
    data object Send : ChatUiEvent
    data object NewConversation : ChatUiEvent
    data class SelectConversation(val id: Long) : ChatUiEvent
    data class DeleteConversation(val id: Long) : ChatUiEvent
    data object ToggleConversations : ChatUiEvent
    data object ToggleSettings : ChatUiEvent
    data object ToggleContext : ChatUiEvent
    data class ContextChanged(val value: String) : ChatUiEvent
    /** File picked from the document picker — appended to the context. */
    data class ContextFileAttached(val name: String, val content: String) : ChatUiEvent
    data object DismissError : ChatUiEvent
}

sealed interface ChatUiEffect

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : LifeViewModel<ChatUiState, ChatUiEvent, ChatUiEffect>(ChatUiState()) {

    private val activeConversationId = MutableStateFlow<Long?>(null)
    private var sendJob: Job? = null

    init {
        viewModelScope.launch {
            chatRepository.observeConversations().collect { conversations ->
                updateState { it.copy(conversations = conversations) }
            }
        }
        viewModelScope.launch {
            activeConversationId
                .flatMapLatest { id ->
                    if (id == null) emptyFlow() else chatRepository.observeMessages(id)
                }
                .collect { messages -> updateState { it.copy(messages = messages) } }
        }
    }

    override fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.InputChanged -> updateState { it.copy(input = event.value) }
            ChatUiEvent.Send -> send()
            ChatUiEvent.NewConversation -> selectConversation(null)
            is ChatUiEvent.SelectConversation -> selectConversation(event.id)
            is ChatUiEvent.DeleteConversation -> deleteConversation(event.id)
            ChatUiEvent.ToggleConversations ->
                updateState { it.copy(showConversations = !it.showConversations) }
            ChatUiEvent.ToggleSettings ->
                updateState { it.copy(showSettings = !it.showSettings) }
            ChatUiEvent.ToggleContext -> updateState { it.copy(showContext = !it.showContext) }
            is ChatUiEvent.ContextChanged -> updateState { it.copy(contextText = event.value) }
            is ChatUiEvent.ContextFileAttached -> updateState {
                it.copy(
                    contextText = buildString {
                        append(it.contextText)
                        if (it.contextText.isNotBlank()) append("\n\n")
                        append("--- ${event.name} ---\n")
                        append(event.content.take(60_000))
                    },
                )
            }
            ChatUiEvent.DismissError -> updateState { it.copy(error = null) }
        }
    }

    private fun selectConversation(id: Long?) {
        activeConversationId.value = id
        updateState {
            it.copy(
                activeConversationId = id,
                messages = if (id == null) emptyList() else it.messages,
                showConversations = false,
            )
        }
    }

    private fun send() {
        val typed = uiState.value.input.trim()
        if (typed.isEmpty() || uiState.value.streaming) return

        // Manual context rides along visibly — no hidden prompt surgery.
        val context = uiState.value.contextText.trim()
        val text = if (context.isBlank()) {
            typed
        } else {
            "[Context]\n$context\n[/Context]\n\n$typed"
        }

        updateState { it.copy(input = "", streaming = true, error = null) }
        sendJob = viewModelScope.launch {
            chatRepository.sendMessage(uiState.value.activeConversationId, text)
                .collect { progress ->
                    when (progress) {
                        is ReplyProgress.Started -> {
                            if (activeConversationId.value != progress.conversationId) {
                                selectConversation(progress.conversationId)
                            }
                            updateState { it.copy(activeEngine = progress.engine) }
                        }
                        is ReplyProgress.Delta -> Unit // Room flow re-emits the row
                        is ReplyProgress.Done -> updateState { it.copy(streaming = false) }
                        is ReplyProgress.Failed -> updateState {
                            it.copy(streaming = false, error = progress.error.toUserMessage())
                        }
                    }
                }
            updateState { it.copy(streaming = false) }
        }
    }

    private fun deleteConversation(id: Long) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
            if (uiState.value.activeConversationId == id) selectConversation(null)
        }
    }

    private fun LifeError.toUserMessage(): String = message
}
