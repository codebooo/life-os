package com.lifeos.core.ai.rag

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * On-device text embedding for NotesRag (§5.4, [src 29]). Behind an interface
 * so the default can be swapped for a LiteRT/MediaPipe embedding model once
 * one is sideloaded — retrieval quality improves, the pipeline stays the same.
 */
interface TextEmbedder {
    val dimension: Int
    fun embed(text: String): FloatArray
}

/**
 * Dependency-free default: L2-normalized hashed bag-of-words with sublinear
 * term weighting. Fully local and deterministic; good enough for top-k recall
 * over personal notes until a neural embedder is installed.
 */
@Singleton
class HashingTextEmbedder @Inject constructor() : TextEmbedder {

    override val dimension: Int = DIMENSION

    override fun embed(text: String): FloatArray {
        val vector = FloatArray(DIMENSION)
        tokenize(text).forEach { token ->
            val bucket = (token.hashCode() % DIMENSION + DIMENSION) % DIMENSION
            vector[bucket] += 1f
        }
        // Sublinear scaling then L2 normalize so cosine ~ dot product.
        var norm = 0f
        for (i in vector.indices) {
            if (vector[i] > 0f) vector[i] = 1f + kotlin.math.ln(vector[i])
            norm += vector[i] * vector[i]
        }
        if (norm > 0f) {
            val inv = 1f / sqrt(norm)
            for (i in vector.indices) vector[i] *= inv
        }
        return vector
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(NON_WORD)
            .filter { it.length > 1 }

    companion object {
        const val DIMENSION = 512
        private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

        fun cosine(a: FloatArray, b: FloatArray): Float {
            var dot = 0f
            val n = minOf(a.size, b.size)
            for (i in 0 until n) dot += a[i] * b[i]
            return dot
        }
    }
}
