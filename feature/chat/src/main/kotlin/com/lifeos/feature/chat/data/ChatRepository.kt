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
    private val toolbox: JarvisToolbox,
    private val debug: JarvisDebug,
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

        // No pre-canned answers here (power phrases live in the power menu and
        // overlay only): the LLM always writes the reply. It sees a live
        // cross-module snapshot + a tool contract, and acts by emitting
        // [[tool: args]] lines that the app executes after generation.
        // Small on-device models degrade with long transcripts, and Gemma's
        // maxTokens budget covers input AND output — an oversized prompt eats
        // the answer's budget and truncates it mid-sentence. Keep it tight.
        debug.beginTurn(text)
        val trimmedHistory = history.takeLast(4).map { it.copy(content = it.content.take(400)) }
        val snapshot = runCatching { toolbox.snapshot() }.getOrDefault("")
        val system = SYSTEM_PROMPT + "\n\n" + toolbox.toolSpec + "\n\n" + snapshot
        debug.add("snapshot", snapshot)
        val request = AiRequest(messages = trimmedHistory, system = system)
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
                    debug.add("error", event.error.message)
                    emit(ReplyProgress.Failed(event.error))
                }
            }
        }

        if (accumulated.isNotEmpty()) {
            debug.add("raw-output", accumulated.toString())
            // Execute any [[tool: args]] lines the model emitted; the final
            // message is the cleaned reply + app-generated confirmations.
            val acted = runCatching { toolbox.runActions(accumulated.toString(), debug) }
                .getOrElse { debug.add("error", it.message ?: "runActions failed"); accumulated.toString() }
            accumulated.setLength(0)
            accumulated.append(acted)
            persistAssistant()
            chatDao.touchConversation(convId, updatedAt = System.currentTimeMillis())
            emit(ReplyProgress.Delta(acted))
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
            "You are Jarvis, a private on-device assistant for LifeOS. " +
                "Answer directly in 1-3 short sentences. Plain text only — never output XML, " +
                "role markers, or <start_of_turn>/<end_of_turn> tokens. " +
                "Answer questions about the user's data ONLY from LIVE DATA below — " +
                "never invent tasks, reminders or events that aren't listed."
    }
}
