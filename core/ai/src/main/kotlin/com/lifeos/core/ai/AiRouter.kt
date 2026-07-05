package com.lifeos.core.ai

import com.lifeos.core.ai.engine.AiEngine
import com.lifeos.core.ai.model.AiChunk
import com.lifeos.core.ai.model.AiCompletion
import com.lifeos.core.ai.model.AiEngineId
import com.lifeos.core.ai.model.AiRequest
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.onEach

/** A streamed routing outcome: which engine answered plus its chunks. */
data class RoutedStream(
    val engine: AiEngineId,
    val chunks: Flow<AiChunk>,
)

/**
 * The single entry point for all inference (§5). Policy, in order:
 * privacy tag → NAS reachability → default on-device; on engine failure the
 * stream transparently falls back to the other engine. Features never pick
 * an engine themselves.
 */
class AiRouter(
    private val onDevice: AiEngine,
    private val nas: AiEngine,
) {

    /** Picks an engine per the routing policy; null when nothing can serve. */
    suspend fun route(request: AiRequest): AiEngineId? = when {
        request.localOnly -> if (onDevice.isAvailable()) onDevice.id else null
        nas.isAvailable() -> nas.id
        onDevice.isAvailable() -> onDevice.id
        else -> null
    }

    /**
     * Streams a completion with transparent fallback: if the primary engine's
     * stream fails before finishing, the secondary (when permitted) restarts
     * the request. Emissions of the failed attempt are replaced, not appended —
     * collectors get `Restart` to reset accumulated text.
     */
    fun stream(request: AiRequest): Flow<StreamEvent> = flow {
        val primary = route(request)
        if (primary == null) {
            emit(StreamEvent.Failed(noEngineError(request)))
            return@flow
        }
        val primaryEngine = engineFor(primary)
        val secondaryEngine = when {
            request.localOnly -> null
            else -> listOf(onDevice, nas).firstOrNull { it.id != primary && it.isAvailable() }
        }

        var primaryFailed = false
        emit(StreamEvent.EngineSelected(primaryEngine.id))
        try {
            primaryEngine.stream(request).collect { emit(StreamEvent.Chunk(it)) }
        } catch (t: Throwable) {
            if (t is kotlin.coroutines.cancellation.CancellationException) throw t
            LifeLogger.w(TAG, "${primaryEngine.id} failed, falling back", t)
            primaryFailed = true
        }

        if (primaryFailed) {
            if (secondaryEngine == null) {
                emit(StreamEvent.Failed(LifeError.Network("AI request failed and no fallback engine is available")))
                return@flow
            }
            emit(StreamEvent.Restart(secondaryEngine.id))
            try {
                secondaryEngine.stream(request).collect { emit(StreamEvent.Chunk(it)) }
            } catch (t: Throwable) {
                if (t is kotlin.coroutines.cancellation.CancellationException) throw t
                LifeLogger.e(TAG, "Fallback ${secondaryEngine.id} also failed", t)
                emit(StreamEvent.Failed(LifeError.Network("Both AI engines failed: ${t.message}")))
            }
        }
    }

    /** Non-streaming convenience: collects [stream] into a single completion. */
    suspend fun complete(request: AiRequest): LifeResult<AiCompletion> {
        var engine: AiEngineId? = null
        var error: LifeError? = null
        val text = StringBuilder()
        stream(request).collect { event ->
            when (event) {
                is StreamEvent.EngineSelected -> engine = event.engine
                is StreamEvent.Restart -> {
                    engine = event.engine
                    text.setLength(0)
                }
                is StreamEvent.Chunk -> text.append(event.chunk.text)
                is StreamEvent.Failed -> error = event.error
            }
        }
        error?.let { return LifeResult.Failure(it) }
        val servedBy = engine ?: return LifeResult.Failure(noEngineError(request))
        return LifeResult.Success(AiCompletion(text = text.toString(), engine = servedBy))
    }

    private fun engineFor(id: AiEngineId): AiEngine =
        if (onDevice.id == id) onDevice else nas

    private fun noEngineError(request: AiRequest): LifeError =
        if (request.localOnly) {
            LifeError.Validation(
                "This request is private (on-device only), but no on-device model is installed",
            )
        } else {
            LifeError.Network("No AI engine is available — configure the NAS endpoint or install the on-device model")
        }

    sealed interface StreamEvent {
        data class EngineSelected(val engine: AiEngineId) : StreamEvent
        data class Chunk(val chunk: AiChunk) : StreamEvent

        /** Primary engine failed mid-stream; discard accumulated text and continue on [engine]. */
        data class Restart(val engine: AiEngineId) : StreamEvent
        data class Failed(val error: LifeError) : StreamEvent
    }

    private companion object {
        const val TAG = "AiRouter"
    }
}
