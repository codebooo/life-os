package com.lifeos.feature.capture.data

import com.lifeos.core.ai.AiRouter
import com.lifeos.core.ai.engine.AiEngine
import com.lifeos.core.ai.model.AiChunk
import com.lifeos.core.ai.model.AiEngineId
import com.lifeos.core.ai.model.AiRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureClassifierTest {

    private val classifier = CaptureClassifier(
        aiRouter = AiRouter(
            onDevice = unavailableEngine(AiEngineId.ON_DEVICE_GEMMA),
            nas = unavailableEngine(AiEngineId.NAS_OLLAMA),
        ),
    )

    @Test
    fun `task-like text routes to TASK heuristically`() {
        val suggestion = classifier.classifyHeuristically("buy oat milk tomorrow", emptyList())
        assertEquals(CaptureDestination.TASK, suggestion.destination)
    }

    @Test
    fun `known form with a number routes to LOG`() {
        val suggestion = classifier.classifyHeuristically("mood 7 after workout", listOf("Mood"))
        assertEquals(CaptureDestination.LOG, suggestion.destination)
        assertEquals("Mood", suggestion.formName)
    }

    @Test
    fun `freeform ideas route to NOTE`() {
        val suggestion = classifier.classifyHeuristically(
            "an idea about linking notes to calendar events",
            listOf("Mood"),
        )
        assertEquals(CaptureDestination.NOTE, suggestion.destination)
    }

    @Test
    fun `valid AI json is parsed with form validation`() {
        val suggestion = classifier.parseAiSuggestion(
            """{"destination":"LOG","title":"Coffee","formName":"caffeine"}""",
            knownFormNames = listOf("Caffeine"),
        )
        assertEquals(CaptureDestination.LOG, suggestion?.destination)
        assertEquals("caffeine", suggestion?.formName)
    }

    @Test
    fun `LOG suggestion without a known form degrades to NOTE`() {
        val suggestion = classifier.parseAiSuggestion(
            """{"destination":"LOG","title":"Coffee","formName":"unknown"}""",
            knownFormNames = listOf("Mood"),
        )
        assertEquals(CaptureDestination.NOTE, suggestion?.destination)
    }

    @Test
    fun `garbage AI output returns null and falls back`() {
        assertNull(classifier.parseAiSuggestion("Sure! I'd classify this as a task.", emptyList()))
    }

    private fun unavailableEngine(engineId: AiEngineId) = object : AiEngine {
        override val id = engineId
        override suspend fun isAvailable() = false
        override fun stream(request: AiRequest): Flow<AiChunk> = flow {}
    }
}
