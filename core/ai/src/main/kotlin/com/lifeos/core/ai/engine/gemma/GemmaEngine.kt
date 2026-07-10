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
        emit(AiChunk(text = sanitize(text), done = true))
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

    /**
     * Strips Gemma's turn/control tokens that the model sometimes echoes
     * (`<end_of_turn>`, `<start_of_turn>`, `<eos>`) and truncates at the first
     * end-of-turn so the answer never bleeds into a fake next turn.
     */
    private fun sanitize(raw: String?): String {
        var t = raw.orEmpty()
        listOf("<end_of_turn>", "<eos>", "<start_of_turn>").forEach { token ->
            val cut = t.indexOf(token)
            if (cut >= 0) t = t.substring(0, cut)
        }
        return t
            .replace("<start_of_turn>model", "")
            .replace(Regex("</?(start|end)_of_turn>"), "")
            .replace("<eos>", "")
            .replace("model\n", "")
            .trim()
    }

    /**
     * Flattens the chat into Gemma's plain-text turn format, dropping the
     * OLDEST turns first when the prompt would eat into the output budget
     * (maxTokens covers input + output, so an unbounded prompt = a truncated
     * answer). System prompt and the newest user turn always survive.
     */
    private fun buildPrompt(request: AiRequest): String {
        val header = buildString {
            request.system?.let { appendLine(it).appendLine() }
        }
        val turns = request.messages.map { message ->
            when (message.role) {
                AiRole.SYSTEM -> message.content + "\n"
                AiRole.USER -> "<start_of_turn>user\n${message.content}<end_of_turn>\n"
                AiRole.ASSISTANT -> "<start_of_turn>model\n${message.content}<end_of_turn>\n"
            }
        }
        val kept = ArrayDeque<String>()
        var budget = MAX_PROMPT_CHARS - header.length
        for (turn in turns.asReversed()) {
            if (budget - turn.length < 0 && kept.isNotEmpty()) break
            kept.addFirst(turn)
            budget -= turn.length
        }
        return header + kept.joinToString("") + "<start_of_turn>model\n"
    }

    private companion object {
        const val TAG = "GemmaEngine"
        // MediaPipe's maxTokens is the whole context window — INPUT + output.
        // 512 left almost no output budget once a few history turns were in the
        // prompt, which is what truncated answers mid-sentence. 1024 with a
        // trimmed prompt keeps decoding fast while leaving real room to answer.
        const val MAX_TOKENS = 1280
        // ~4 chars/token: keep the prompt bounded so a healthy share of the
        // window is always left for the reply, whatever the caller sends.
        const val MAX_PROMPT_CHARS = 2600
        const val GENERATE_TIMEOUT_MS = 90_000L
    }
}
