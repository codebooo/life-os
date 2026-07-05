package com.lifeos.core.ai.engine.ollama

import com.lifeos.core.ai.model.AiChunk
import com.lifeos.core.ai.model.AiMessage
import com.lifeos.core.ai.model.AiRequest
import com.lifeos.core.ai.model.AiRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ollama `/api/chat` wire format (§8.3). Kept as pure functions so the
 * request/response mapping is unit-testable without a server.
 */
@Serializable
internal data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = true,
)

@Serializable
internal data class OllamaMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class OllamaChatChunk(
    val message: OllamaMessage? = null,
    val done: Boolean = false,
    @SerialName("error") val error: String? = null,
)

internal object OllamaProtocol {

    val json = Json { ignoreUnknownKeys = true }

    fun buildRequest(model: String, request: AiRequest): OllamaChatRequest {
        val messages = buildList {
            request.system?.let { add(OllamaMessage(role = "system", content = it)) }
            request.messages.forEach { add(it.toOllama()) }
        }
        return OllamaChatRequest(model = model, messages = messages)
    }

    fun encode(request: OllamaChatRequest): String = json.encodeToString(request)

    /**
     * Parses one NDJSON line from a streamed chat response.
     * Returns null for blank keep-alive lines; throws on an in-band error.
     */
    fun parseChunk(line: String): AiChunk? {
        if (line.isBlank()) return null
        val chunk = json.decodeFromString<OllamaChatChunk>(line)
        chunk.error?.let { throw OllamaException(it) }
        return AiChunk(text = chunk.message?.content.orEmpty(), done = chunk.done)
    }

    private fun AiMessage.toOllama() = OllamaMessage(
        role = when (role) {
            AiRole.SYSTEM -> "system"
            AiRole.USER -> "user"
            AiRole.ASSISTANT -> "assistant"
        },
        content = content,
    )
}

internal class OllamaException(message: String) : Exception(message)
