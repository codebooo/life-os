package com.lifeos.core.ai.engine.gemma

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.lifeos.core.ai.engine.AiEngine
import com.lifeos.core.ai.model.AiChunk
import com.lifeos.core.ai.model.AiEngineId
import com.lifeos.core.ai.model.AiRequest
import com.lifeos.core.ai.model.AiRole
import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.datastore.AiConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device Gemma via the LLM Inference API (LiteRT under the hood, §8.2).
 * The model file is sideloaded to `<external-files>/models/` (or a custom
 * path from settings). Lazily loaded, serialized access, released on demand.
 *
 * The sync generate API is wrapped as a single-chunk flow; token-level
 * streaming lands with the richer session API in a later phase.
 */
@Singleton
class GemmaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiConfigRepository: AiConfigRepository,
    private val dispatchers: DispatcherProvider,
) : AiEngine {

    override val id: AiEngineId = AiEngineId.ON_DEVICE_GEMMA

    private val mutex = Mutex()
    private var llm: LlmInference? = null
    private var loadedModelPath: String? = null

    // A dedicated single background thread so inference NEVER competes with the
    // shared IO pool (which the whole app uses) — that competition, plus the
    // GPU delegate, is what froze the device. CPU backend only.
    private val inferenceDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "gemma-inference").apply { priority = Thread.NORM_PRIORITY - 1 }
    }.asCoroutineDispatcher()

    override suspend fun isAvailable(): Boolean = modelFile()?.exists() == true

    /**
     * Runs the prompt on the CPU backend, on a dedicated low-priority thread,
     * with a hard [GENERATE_TIMEOUT_MS] ceiling. Failsafes: any load/generate
     * error or timeout releases the model and surfaces a clean failure instead
     * of wedging — a hung inference can never brick the device again.
     */
    override fun stream(request: AiRequest): Flow<AiChunk> = flow {
        val file = modelFile()
        check(file != null && file.exists()) { "No on-device model at ${file?.absolutePath}" }

        val text = mutex.withLock {
            try {
                withTimeout(GENERATE_TIMEOUT_MS) {
                    val inference = loadIfNeeded(file.absolutePath)
                    inference.generateResponse(buildPrompt(request))
                }
            } catch (t: Throwable) {
                // Poisoned session/OOM/timeout — drop the model so the next try is clean.
                LifeLogger.e(TAG, "Inference failed; releasing model", t)
                runCatching { llm?.close() }
                llm = null
                loadedModelPath = null
                throw t
            }
        }
        emit(AiChunk(text = text.orEmpty(), done = true))
    }.flowOn(inferenceDispatcher)

    /** Frees the model memory (called from onTrimMemory via the app). */
    suspend fun release() = mutex.withLock {
        runCatching { llm?.close() }
        llm = null
        loadedModelPath = null
        LifeLogger.i(TAG, "Model released")
    }

    private fun loadIfNeeded(path: String): LlmInference {
        val current = llm
        if (current != null && loadedModelPath == path) return current
        current?.close()

        LifeLogger.i(TAG, "Loading on-device model from $path (CPU)")
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            // Smaller cap = faster answers and far less memory pressure than 2048.
            .setMaxTokens(MAX_TOKENS)
            // CPU backend on purpose: the GPU delegate froze the S22 Ultra
            // compositor. Reliability over raw speed for on-device.
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()
        return LlmInference.createFromOptions(context, options).also {
            llm = it
            loadedModelPath = path
        }
    }

    private suspend fun modelFile(): File? {
        val configured = aiConfigRepository.config.first().onDeviceModelPath
        if (configured.isNotBlank()) return File(configured)
        val modelsDir = context.getExternalFilesDir("models") ?: return null
        return modelsDir.listFiles()
            ?.firstOrNull { it.extension in setOf("task", "litertlm", "bin") }
    }

    /** Flattens the chat into Gemma's plain-text turn format. */
    private fun buildPrompt(request: AiRequest): String = buildString {
        request.system?.let { appendLine(it).appendLine() }
        request.messages.forEach { message ->
            when (message.role) {
                AiRole.SYSTEM -> appendLine(message.content).appendLine()
                AiRole.USER -> appendLine("<start_of_turn>user\n${message.content}<end_of_turn>")
                AiRole.ASSISTANT -> appendLine("<start_of_turn>model\n${message.content}<end_of_turn>")
            }
        }
        append("<start_of_turn>model\n")
    }

    private companion object {
        const val TAG = "GemmaEngine"
        // Lower than before (2048): shorter answers decode much faster on CPU
        // and use far less RAM, which is what actually matters on-device.
        const val MAX_TOKENS = 512
        const val GENERATE_TIMEOUT_MS = 90_000L
    }
}
