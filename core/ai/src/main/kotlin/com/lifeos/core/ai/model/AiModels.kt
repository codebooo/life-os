package com.lifeos.core.ai.model

/** Which engine actually served a request; surfaced in the UI as provenance. */
enum class AiEngineId(val label: String) {
    ON_DEVICE_GEMMA("On-device"),
    NAS_OLLAMA("NAS"),
}

enum class AiRole { SYSTEM, USER, ASSISTANT }

data class AiMessage(
    val role: AiRole,
    val content: String,
)

/**
 * A routed AI request (§5). [localOnly] is the privacy tag: when set, the
 * router never sends the payload off the device, even if the NAS is faster.
 */
data class AiRequest(
    val messages: List<AiMessage>,
    val system: String? = null,
    val localOnly: Boolean = false,
)

/** One streamed increment of a completion. */
data class AiChunk(
    val text: String,
    val done: Boolean,
)

data class AiCompletion(
    val text: String,
    val engine: AiEngineId,
)
