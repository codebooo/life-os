package com.lifeos.feature.capture.data

import com.lifeos.core.ai.AiRouter
import com.lifeos.core.ai.model.AiMessage
import com.lifeos.core.ai.model.AiRequest
import com.lifeos.core.ai.model.AiRole
import com.lifeos.core.common.result.LifeResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class CaptureDestination { NOTE, TASK, LOG }

/** The classifier's proposal — always confirmed by the user before writing (R12). */
data class CaptureSuggestion(
    val destination: CaptureDestination,
    val title: String,
    /** Target log form name when [destination] is LOG. */
    val formName: String? = null,
)

/**
 * Routes a quick capture to its destination (§Module 20, R12): Gemma with a
 * strict JSON contract when an engine is available (privacy-tagged, so text
 * never leaves the device), a transparent keyword heuristic otherwise. The
 * suggestion is only that — nothing is written until the user confirms.
 */
@Singleton
class CaptureClassifier @Inject constructor(
    private val aiRouter: AiRouter,
) {

    suspend fun classify(text: String, knownFormNames: List<String>): CaptureSuggestion {
        classifyWithAi(text, knownFormNames)?.let { return it }
        return classifyHeuristically(text, knownFormNames)
    }

    private suspend fun classifyWithAi(
        text: String,
        knownFormNames: List<String>,
    ): CaptureSuggestion? {
        val request = AiRequest(
            system = "Classify a quick capture. Reply with ONLY minified JSON: " +
                """{"destination":"NOTE"|"TASK"|"LOG","title":"short title","formName":string|null}. """ +
                "TASK = something to do. LOG = a measurement/event for one of these forms: " +
                "${knownFormNames.joinToString().ifEmpty { "(none)" }}. NOTE = everything else.",
            messages = listOf(AiMessage(AiRole.USER, text)),
            localOnly = true,
        )
        val completion = when (val result = aiRouter.complete(request)) {
            is LifeResult.Success -> result.value.text
            is LifeResult.Failure -> return null
        }
        return parseAiSuggestion(completion, knownFormNames)
    }

    internal fun parseAiSuggestion(raw: String, knownFormNames: List<String>): CaptureSuggestion? {
        val jsonText = raw.substringAfter('{', "").let { if (it.isEmpty()) return null else "{$it" }
            .substringBeforeLast('}') + "}"
        val parsed = try {
            json.decodeFromString<AiSuggestionDto>(jsonText)
        } catch (e: Exception) {
            return null
        }
        val destination = when (parsed.destination.uppercase()) {
            "TASK" -> CaptureDestination.TASK
            "LOG" -> CaptureDestination.LOG
            "NOTE" -> CaptureDestination.NOTE
            else -> return null
        }
        val formName = parsed.formName?.takeIf { name ->
            knownFormNames.any { it.equals(name, ignoreCase = true) }
        }
        return CaptureSuggestion(
            destination = if (destination == CaptureDestination.LOG && formName == null) {
                CaptureDestination.NOTE
            } else {
                destination
            },
            title = parsed.title.ifBlank { raw.take(48) },
            formName = formName,
        )
    }

    internal fun classifyHeuristically(text: String, knownFormNames: List<String>): CaptureSuggestion {
        val trimmed = text.trim()
        val lower = trimmed.lowercase()

        // "<form name>: 7" or "<form name> 7" targets a known log form.
        knownFormNames.forEach { form ->
            if (lower.startsWith(form.lowercase()) && trimmed.any { it.isDigit() }) {
                return CaptureSuggestion(CaptureDestination.LOG, title = trimmed.take(48), formName = form)
            }
        }

        val taskSignals = listOf(
            "todo", "to do", "remember to", "need to", "don't forget", "buy ",
            "call ", "email ", "fix ", "schedule ", "book ", "pay ",
        )
        if (taskSignals.any { lower.startsWith(it) || lower.contains(" $it") }) {
            return CaptureSuggestion(CaptureDestination.TASK, title = trimmed.take(80))
        }

        return CaptureSuggestion(
            CaptureDestination.NOTE,
            title = trimmed.lineSequence().first().take(48),
        )
    }

    @Serializable
    private data class AiSuggestionDto(
        val destination: String = "",
        val title: String = "",
        val formName: String? = null,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
