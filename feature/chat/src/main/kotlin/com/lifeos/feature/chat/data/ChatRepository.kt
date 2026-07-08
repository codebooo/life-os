package com.lifeos.feature.chat.data

import com.lifeos.core.ai.AiRouter
import com.lifeos.core.ai.model.AiEngineId
import com.lifeos.core.ai.model.AiMessage
import com.lifeos.core.ai.model.AiRequest
import com.lifeos.core.ai.model.AiRole
import com.lifeos.core.common.result.LifeError
import com.lifeos.core.database.chat.AiConversationEntity
import com.lifeos.core.database.chat.AiMessageEntity
import com.lifeos.core.database.chat.ChatDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** Progress of one assistant reply as it streams in. */
sealed interface ReplyProgress {
    data class Started(val conversationId: Long, val engine: AiEngineId) : ReplyProgress
    data class Delta(val accumulatedText: String) : ReplyProgress
    data class Done(val conversationId: Long) : ReplyProgress
    data class Failed(val error: LifeError) : ReplyProgress
}

interface ChatRepository {
    fun observeConversations(): Flow<List<AiConversationEntity>>
    fun observeMessages(conversationId: Long): Flow<List<AiMessageEntity>>

    /**
     * Persists the user message (creating the conversation when [conversationId]
     * is null), streams the assistant reply into a message row, and reports
     * progress. History is replayed to the engine for context.
     */
    fun sendMessage(conversationId: Long?, text: String): Flow<ReplyProgress>

    suspend fun deleteConversation(conversationId: Long)
}

@Singleton
internal class DefaultChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val aiRouter: AiRouter,
    private val commandHandler: ChatCommandHandler,
) : ChatRepository {

    override fun observeConversations(): Flow<List<AiConversationEntity>> =
        chatDao.observeConversations()

    override fun observeMessages(conversationId: Long): Flow<List<AiMessageEntity>> =
        chatDao.observeMessages(conversationId)

    override fun sendMessage(conversationId: Long?, text: String): Flow<ReplyProgress> = flow {
        val now = System.currentTimeMillis()
        val convId = conversationId ?: chatDao.insertConversation(
            AiConversationEntity(title = text.take(48), createdAt = now, updatedAt = now),
        )

        val history = mutableListOf<AiMessage>()
        chatDao.getMessages(convId).forEach { entity ->
            val role = if (entity.role == ROLE_USER) AiRole.USER else AiRole.ASSISTANT
            history += AiMessage(role, entity.content)
        }

        chatDao.insertMessage(
            AiMessageEntity(
                conversationId = convId,
                role = ROLE_USER,
                content = text,
                engine = null,
                createdAt = now,
            ),
        )
        history += AiMessage(AiRole.USER, text)

        var assistantMessageId: Long? = null
        var engine: AiEngineId? = null
        val accumulated = StringBuilder()

        suspend fun persistAssistant() {
            val id = assistantMessageId
            if (id == null) {
                assistantMessageId = chatDao.insertMessage(
                    AiMessageEntity(
                        conversationId = convId,
                        role = ROLE_ASSISTANT,
                        content = accumulated.toString(),
                        engine = engine?.name,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                chatDao.updateMessageContent(id, accumulated.toString(), engine?.name)
            }
        }

        // Deterministic module commands run first and actually DO the thing
        // (add task, set reminder/timer/event, read packages/tasks). Only open
        // questions fall through to the LLM. The context block from the AI
        // Context sheet is stripped before matching so it never confuses intent.
        val command = commandHandler.tryHandle(text.substringAfterLast("[/Context]").trim().ifBlank { text })
        if (command != null) {
            engine = AiEngineId.ON_DEVICE_GEMMA
            emit(ReplyProgress.Started(convId, engine!!))
            accumulated.append(command)
            persistAssistant()
            chatDao.touchConversation(convId, updatedAt = System.currentTimeMillis())
            emit(ReplyProgress.Delta(command))
            emit(ReplyProgress.Done(convId))
            return@flow
        }

        val request = AiRequest(messages = history, system = SYSTEM_PROMPT)
        aiRouter.stream(request).collect { event ->
            when (event) {
                is AiRouter.StreamEvent.EngineSelected -> {
                    engine = event.engine
                    emit(ReplyProgress.Started(convId, event.engine))
                }
                is AiRouter.StreamEvent.Restart -> {
                    engine = event.engine
                    accumulated.setLength(0)
                    emit(ReplyProgress.Started(convId, event.engine))
                }
                is AiRouter.StreamEvent.Chunk -> {
                    accumulated.append(event.chunk.text)
                    persistAssistant()
                    emit(ReplyProgress.Delta(accumulated.toString()))
                }
                is AiRouter.StreamEvent.Failed -> {
                    emit(ReplyProgress.Failed(event.error))
                }
            }
        }

        if (accumulated.isNotEmpty()) {
            persistAssistant()
            chatDao.touchConversation(convId, updatedAt = System.currentTimeMillis())
            emit(ReplyProgress.Done(convId))
        }
    }

    override suspend fun deleteConversation(conversationId: Long) {
        chatDao.deleteConversation(conversationId)
    }

    private companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val SYSTEM_PROMPT =
            "You are LifeOS, a private on-device life assistant. Be concise, practical and direct. " +
                "If a question needs reasoning, you MAY put your step-by-step thinking inside " +
                "<think>...</think> first, then give the final answer after </think>. Keep thinking brief."
    }
}
