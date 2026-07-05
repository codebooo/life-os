package com.lifeos.core.ai.rag

import com.lifeos.core.ai.AiRouter
import com.lifeos.core.ai.engine.AiEngine
import com.lifeos.core.ai.model.AiChunk
import com.lifeos.core.ai.model.AiEngineId
import com.lifeos.core.ai.model.AiRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesRagTest {

    private val embedder = HashingTextEmbedder()
    private val rag = NotesRag(
        embedder = embedder,
        aiRouter = AiRouter(
            onDevice = object : AiEngine {
                override val id = AiEngineId.ON_DEVICE_GEMMA
                override suspend fun isAvailable() = true
                override fun stream(request: AiRequest): Flow<AiChunk> =
                    flow { emit(AiChunk("grounded answer [1]", done = true)) }
            },
            nas = object : AiEngine {
                override val id = AiEngineId.NAS_OLLAMA
                override suspend fun isAvailable() = false
                override fun stream(request: AiRequest): Flow<AiChunk> = flow {}
            },
        ),
    )

    @Test
    fun `retrieval ranks the topically matching chunk first`() {
        val candidates = listOf(
            stored(1, "Cooking", "Slow-roasted tomato pasta with garlic and basil"),
            stored(2, "Servers", "The NAS runs Ollama in a container on port 11434"),
            stored(3, "Fitness", "Interval training three times a week improves endurance"),
        )

        val results = rag.retrieve("which port does ollama use on the nas", candidates, topK = 2)

        assertEquals(2L, results.first().sourceId)
    }

    @Test
    fun `chunking splits long bodies and preserves short ones`() {
        val short = rag.chunk("just one paragraph")
        val long = rag.chunk((1..30).joinToString("\n\n") { "Paragraph $it " + "x".repeat(100) })

        assertEquals(1, short.size)
        assertTrue(long.size > 1)
    }

    @Test
    fun `vector encoding round-trips`() {
        val vector = embedder.embed("hello world hello")
        val decoded = NotesRag.decode(NotesRag.encode(vector))

        assertEquals(vector.size, decoded.size)
        assertEquals(vector.toList(), decoded.toList())
    }

    private fun stored(id: Long, title: String, text: String) =
        NotesRag.StoredEmbedding(
            sourceId = id,
            sourceTitle = title,
            chunkText = text,
            vector = NotesRag.encode(embedder.embed(text)),
        )
}
