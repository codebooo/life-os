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
    /**
     * Local file paths of images that belong to this turn. Vision-capable
     * engines feed them to the model; text-only ones ignore them.
     */
    val imagePaths: List<String> = emptyList(),
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

/**
 * One streamed increment of a completion.
 *
 * A chunk whose text equals [REPLACE_ALL] tells the consumer to clear what it
 * has accumulated: the next chunk carries the whole cleaned answer. Streaming
 * engines use it to hand over a sanitized final text without re-emitting every
 * fragment.
 */
data class AiChunk(
    val text: String,
    val done: Boolean,
)

/** Sentinel chunk text: discard accumulated output, the next chunk replaces it. */
const val REPLACE_ALL = "\u0000LIFEOS_REPLACE_ALL\u0000"

data class AiCompletion(
    val text: String,
    val engine: AiEngineId,
)
