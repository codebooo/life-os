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

/** One organized item split out of a brain-dump ([src 16], R10). */
data class DumpItem(
    val destination: CaptureDestination,
    val title: String,
    val body: String,
)

/**
 * Splits a spoken/typed stream of thoughts into typed items — on-device AI
 * first (privacy tag), sentence heuristic fallback. Nothing is written until
 * each item is confirmed in the review sheet.
 */
@Singleton
class BrainDumpOrganizer @Inject constructor(
    private val aiRouter: AiRouter,
    private val classifier: CaptureClassifier,
) {

    suspend fun organize(text: String, knownFormNames: List<String>): List<DumpItem> {
        organizeWithAi(text, knownFormNames)?.let { if (it.isNotEmpty()) return it }
        return organizeHeuristically(text, knownFormNames)
    }

    private suspend fun organizeWithAi(text: String, forms: List<String>): List<DumpItem>? {
        val request = AiRequest(
            system = "Split this brain-dump into items. Reply ONLY minified JSON array: " +
                """[{"destination":"NOTE"|"TASK"|"LOG","title":"...","body":"..."}]. """ +
                "TASK = actionable. LOG only for forms: ${forms.joinToString().ifEmpty { "(none)" }}.",
            messages = listOf(AiMessage(AiRole.USER, text)),
            localOnly = true,
        )
        val raw = when (val result = aiRouter.complete(request)) {
            is LifeResult.Success -> result.value.text
            is LifeResult.Failure -> return null
        }
        return parseAiItems(raw)
    }

    internal fun parseAiItems(raw: String): List<DumpItem>? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start == -1 || end <= start) return null
        return try {
            json.decodeFromString<List<DumpItemDto>>(raw.substring(start, end + 1))
                .mapNotNull { dto ->
                    val destination = when (dto.destination.uppercase()) {
                        "TASK" -> CaptureDestination.TASK
                        "LOG" -> CaptureDestination.LOG
                        "NOTE" -> CaptureDestination.NOTE
                        else -> return@mapNotNull null
                    }
                    DumpItem(destination, dto.title.ifBlank { dto.body.take(48) }, dto.body)
                }
        } catch (e: Exception) {
            null
        }
    }

    internal fun organizeHeuristically(text: String, forms: List<String>): List<DumpItem> =
        text.split(Regex("[.\n;]+"))
            .map { it.trim() }
            .filter { it.length > 2 }
            .map { sentence ->
                val suggestion = classifier.classifyHeuristically(sentence, forms)
                DumpItem(suggestion.destination, suggestion.title, sentence)
            }

    @Serializable
    private data class DumpItemDto(
        val destination: String = "",
        val title: String = "",
        val body: String = "",
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
