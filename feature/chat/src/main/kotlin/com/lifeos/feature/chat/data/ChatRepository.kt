package com.lifeos.feature.chat.data

import com.lifeos.core.ai.AiRouter
import com.lifeos.core.ai.model.AiEngineId
import com.lifeos.core.ai.model.AiMessage
import com.lifeos.core.ai.model.AiRequest
import com.lifeos.core.ai.model.AiRole
import com.lifeos.core.ai.model.REPLACE_ALL
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
    fun sendMessage(
        conversationId: Long?,
        text: String,
        imagePaths: List<String> = emptyList(),
    ): Flow<ReplyProgress>

    suspend fun deleteConversation(conversationId: Long)
}

@Singleton
internal class DefaultChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val aiRouter: AiRouter,
    private val toolbox: JarvisToolbox,
    private val debug: JarvisDebug,
    private val publicMirror: com.lifeos.core.common.storage.LifeOsPublicMirror,
) : ChatRepository {

    override fun observeConversations(): Flow<List<AiConversationEntity>> =
        chatDao.observeConversations()

    override fun observeMessages(conversationId: Long): Flow<List<AiMessageEntity>> =
        chatDao.observeMessages(conversationId)

    override fun sendMessage(
        conversationId: Long?,
        text: String,
        imagePaths: List<String>,
    ): Flow<ReplyProgress> = flow {
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
                imagePaths = imagePaths.joinToString("\n"),
            ),
        )
        history += AiMessage(AiRole.USER, text, imagePaths = imagePaths)

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
        // Trimming keeps the newest turn's images: they are the question.
        val trimmedHistory = history.takeLast(4).map { it.copy(content = it.content.take(400)) }
        val snapshot = runCatching { toolbox.snapshot() }.getOrDefault("")
        val system = SYSTEM_PROMPT + "\n\n" + toolbox.toolSpec + "\n\n" + snapshot
        debug.add("snapshot", snapshot)
        var request = AiRequest(messages = trimmedHistory, system = system)

        // Two-pass tool use: the first reply may ask for a module's detail with
        // a [[get: topic]] line. That costs one extra inference only when the
        // model actually needs data, which is why the always-on snapshot can
        // stay small.
        suspend fun runPass() {
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
                        // Streaming engines end with a sentinel plus the cleaned
                        // full text, so the visible reply never keeps raw
                        // fragments (or a stray turn token split across two).
                        if (event.chunk.text == REPLACE_ALL) {
                            accumulated.setLength(0)
                        } else {
                            accumulated.append(event.chunk.text)
                            persistAssistant()
                            emit(ReplyProgress.Delta(accumulated.toString()))
                        }
                    }
                    is AiRouter.StreamEvent.Failed -> {
                        debug.add("error", event.error.message)
                        emit(ReplyProgress.Failed(event.error))
                    }
                }
            }
        }

        runPass()

        // Multi-hop tool use (§Module 9 v2): each pass may ask for one more
        // module read, so "compare my screen time with my focus streak" works
        // without either being in the always-on prompt. Bounded hard - a small
        // model left to loop will happily ask forever.
        var hop = 0
        var fetchedSoFar = ""
        while (hop < MAX_TOOL_HOPS) {
            val reads = toolbox.requestedReads(accumulated.toString())
            if (reads.isEmpty()) break
            val fetched = runCatching { toolbox.fetchReads(reads, debug) }.getOrDefault("")
            if (fetched.isBlank()) break
            fetchedSoFar = (fetchedSoFar + "\n\n" + fetched).trim()
            debug.add("fetched", fetched)
            accumulated.setLength(0)
            val lastHop = hop == MAX_TOOL_HOPS - 1
            request = AiRequest(
                messages = trimmedHistory,
                system = system + "\n\nFETCHED DATA (you asked for this):\n" + fetchedSoFar +
                    if (lastHop) {
                        "\nAnswer now from this data. Do not emit [[get:]] again."
                    } else {
                        "\nAnswer from this data, or ask for ONE more topic if you truly need it."
                    },
            )
            runPass()
            hop++
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
            mirrorConversation(convId)
            emit(ReplyProgress.Delta(acted))
            emit(ReplyProgress.Done(convId))
        }
    }

    /** Writes the whole conversation as Markdown into /LifeOS/Jarvis (no-op without all-files access). */
    private suspend fun mirrorConversation(convId: Long) {
        runCatching {
            val messages = chatDao.getMessages(convId)
            if (messages.isEmpty()) return
            val title = messages.first().content.take(40).replace(Regex("[^A-Za-z0-9 _-]"), "").trim()
                .ifBlank { "chat" }
            val md = buildString {
                appendLine("# $title")
                appendLine()
                messages.forEach { message ->
                    appendLine(if (message.role == ROLE_USER) "**You:**" else "**Jarvis:**")
                    appendLine()
                    appendLine(message.content.trim())
                    appendLine()
                }
            }
            publicMirror.writeText("Jarvis", "$title-$convId.md", md)
        }
    }

    override suspend fun deleteConversation(conversationId: Long) {
        chatDao.deleteConversation(conversationId)
    }

    private companion object {
        /** One initial pass plus at most this many tool hops. */
        const val MAX_TOOL_HOPS = 2
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val SYSTEM_PROMPT =
            "You are Jarvis, a capable general assistant for LifeOS. You happily do " +
                "everything a good assistant does: answer questions, do math, write poems, " +
                "stories, lists and advice, explain things, chat. Never refuse a normal " +
                "request or claim you lack a capability. Answer directly, plain text only — " +
                "never output XML, role markers, or <start_of_turn>/<end_of_turn> tokens. " +
                "When (and only when) asked about the user's OWN tasks/reminders/notes/etc., " +
                "answer from LIVE DATA below and never invent entries that aren't listed."
    }
}
