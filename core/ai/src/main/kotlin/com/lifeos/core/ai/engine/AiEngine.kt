package com.lifeos.core.ai.engine

import com.lifeos.core.ai.model.AiChunk
import com.lifeos.core.ai.model.AiEngineId
import com.lifeos.core.ai.model.AiRequest
import kotlinx.coroutines.flow.Flow

/**
 * A single inference backend. Modules never talk to engines directly — the
 * [com.lifeos.core.ai.AiRouter] picks one per request (§5.1).
 */
interface AiEngine {
    val id: AiEngineId

    /** Cheap availability probe; implementations cache aggressively. */
    suspend fun isAvailable(): Boolean

    /**
     * Streams a completion. Implementations throw on transport/engine errors;
     * the router owns fallback. The final emission has `done = true`.
     */
    fun stream(request: AiRequest): Flow<AiChunk>
}
