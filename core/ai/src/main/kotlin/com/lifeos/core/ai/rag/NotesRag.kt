package com.lifeos.core.ai.rag

import com.lifeos.core.ai.AiRouter
import com.lifeos.core.ai.model.AiMessage
import com.lifeos.core.ai.model.AiRequest
import com.lifeos.core.ai.model.AiRole
import com.lifeos.core.common.result.LifeResult
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/** A retrieved chunk with its provenance for citations. */
data class RagChunk(
    val sourceId: Long,
    val sourceTitle: String,
    val text: String,
    val score: Float,
)

data class RagAnswer(
    val answer: String,
    val citations: List<RagChunk>,
)

/**
 * On-device retrieval-augmented answers over notes (§5.4, [src 29]).
 * Chunking + embedding + cosine retrieval run fully locally; the grounded
 * answer is generated with the privacy tag set, so note text never leaves
 * the device.
 */
@Singleton
class NotesRag @Inject constructor(
    private val embedder: TextEmbedder,
    private val aiRouter: AiRouter,
) {

    /** Splits a note body into overlapping chunks sized for retrieval. */
    fun chunk(body: String): List<String> {
        val paragraphs = body.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        paragraphs.forEach { paragraph ->
            if (current.length + paragraph.length > CHUNK_TARGET_CHARS && current.isNotEmpty()) {
                chunks += current.toString()
                current.setLength(0)
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(paragraph)
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }

    fun embed(text: String): ByteArray = encode(embedder.embed(text))

    /** Ranks stored embeddings against the query; pure vector math, no I/O. */
    fun retrieve(
        query: String,
        candidates: List<StoredEmbedding>,
        topK: Int = DEFAULT_TOP_K,
    ): List<RagChunk> {
        val queryVector = embedder.embed(query)
        return candidates
            .map { candidate ->
                RagChunk(
                    sourceId = candidate.sourceId,
                    sourceTitle = candidate.sourceTitle,
                    text = candidate.chunkText,
                    score = HashingTextEmbedder.cosine(queryVector, decode(candidate.vector)),
                )
            }
            .sortedByDescending { it.score }
            .take(topK)
            .filter { it.score > MIN_SCORE }
    }

    /** Answers [question] grounded in [retrieved] chunks; on-device only. */
    suspend fun answer(question: String, retrieved: List<RagChunk>): LifeResult<RagAnswer> {
        val context = retrieved
            .mapIndexed { i, chunk -> "[${i + 1}] (${chunk.sourceTitle})\n${chunk.text}" }
            .joinToString("\n\n")
        val request = AiRequest(
            system = "Answer strictly from the provided note excerpts. " +
                "Cite sources as [n]. If the excerpts don't contain the answer, say so.",
            messages = listOf(
                AiMessage(AiRole.USER, "Notes:\n$context\n\nQuestion: $question"),
            ),
            localOnly = true,
        )
        return when (val completion = aiRouter.complete(request)) {
            is LifeResult.Success ->
                LifeResult.Success(RagAnswer(answer = completion.value.text, citations = retrieved))
            is LifeResult.Failure -> completion
        }
    }

    data class StoredEmbedding(
        val sourceId: Long,
        val sourceTitle: String,
        val chunkText: String,
        val vector: ByteArray,
    )

    companion object {
        private const val CHUNK_TARGET_CHARS = 800
        private const val DEFAULT_TOP_K = 5
        private const val MIN_SCORE = 0.01f

        fun encode(vector: FloatArray): ByteArray {
            val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            vector.forEach { buffer.putFloat(it) }
            return buffer.array()
        }

        fun decode(bytes: ByteArray): FloatArray {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(bytes.size / 4) { buffer.getFloat() }
        }
    }
}
