package com.lifeos.core.ai.engine.gemma

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.lifeos.core.ai.engine.AiEngine
import com.lifeos.core.ai.model.AiChunk
import com.lifeos.core.ai.model.AiEngineId
import com.lifeos.core.ai.model.AiRequest
import com.lifeos.core.ai.model.AiRole
import com.lifeos.core.ai.model.REPLACE_ALL
import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.datastore.AiConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.channelFlow
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
    private var loadedWithVision = false

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
    override fun stream(request: AiRequest): Flow<AiChunk> = channelFlow {
        val file = modelFile()
        check(file != null && file.exists()) { "No on-device model at ${file?.absolutePath}" }

        val images = request.messages.flatMap { it.imagePaths }.takeLast(MAX_IMAGES)
        mutex.withLock {
            try {
                withTimeout(GENERATE_TIMEOUT_MS) {
                    val inference = loadIfNeeded(file.absolutePath, withVision = images.isNotEmpty())
                    if (images.isEmpty()) {
                        streamText(inference, buildPrompt(request))
                    } else {
                        // Vision goes through a session, which has no progress
                        // callback; one chunk is the honest shape there.
                        val text = generateWithImages(inference, buildPrompt(request), images)
                        send(AiChunk(text = sanitize(text), done = true))
                    }
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
    }.flowOn(inferenceDispatcher)

    /**
     * Token streaming (§Module 9 v2). MediaPipe calls the progress listener with
     * each new fragment, so the reply appears as it is decoded instead of after
     * the whole thing is done — first-token latency replaces total latency as the
     * felt cost. Partials are sanitized individually and the accumulated text is
     * cleaned once at the end, because turn tokens can straddle two fragments.
     */
    private suspend fun ProducerScope<AiChunk>.streamText(inference: LlmInference, prompt: String) {
        val accumulated = StringBuilder()
        val finished = CompletableDeferred<Unit>()
        val listener = ProgressListener<String> { partial, done ->
            if (partial != null) {
                accumulated.append(partial)
                trySend(AiChunk(text = partial, done = false))
            }
            if (done) finished.complete(Unit)
        }
        val future = inference.generateResponseAsync(prompt, listener)
        try {
            finished.await()
        } finally {
            runCatching { future.get() }
        }
        // One last chunk carries the cleaned full text so callers that keep only
        // the final value (and the sanitizer) still see a coherent answer.
        val clean = sanitize(accumulated.toString())
        send(AiChunk(text = REPLACE_ALL, done = false))
        send(AiChunk(text = clean, done = true))
    }

    /** Frees the model memory (called from onTrimMemory via the app). */
    suspend fun release() = mutex.withLock {
        runCatching { llm?.close() }
        llm = null
        loadedModelPath = null
        LifeLogger.i(TAG, "Model released")
    }

    private fun loadIfNeeded(path: String, withVision: Boolean): LlmInference {
        val current = llm
        // Vision needs an image slot reserved at load time, so a text-only
        // handle is reloaded the first time an image shows up (and vice versa).
        if (current != null && loadedModelPath == path && loadedWithVision == withVision) return current
        current?.close()

        LifeLogger.i(TAG, "Loading on-device model from $path (CPU, vision=$withVision)")
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            // Smaller cap = faster answers and far less memory pressure than 2048.
            .setMaxTokens(if (withVision) MAX_TOKENS_VISION else MAX_TOKENS)
            .apply { if (withVision) setMaxNumImages(MAX_IMAGES) }
            // CPU backend on purpose: the GPU delegate froze the S22 Ultra
            // compositor. Reliability over raw speed for on-device.
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()
        return LlmInference.createFromOptions(context, options).also {
            llm = it
            loadedModelPath = path
            loadedWithVision = withVision
        }
    }

    /**
     * Vision prompts go through a session: images are added as their own chunks
     * alongside the text, which is what the multimodal Gemma builds expect.
     * Decoding the bitmaps is bounded so a 12 MP photo cannot blow up memory.
     */
    private fun generateWithImages(inference: LlmInference, prompt: String, imagePaths: List<String>): String {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
            .build()
        return LlmInferenceSession.createFromOptions(inference, sessionOptions).use { session ->
            imagePaths.forEach { path ->
                val bitmap = decodeBounded(path)
                if (bitmap != null) {
                    session.addImage(BitmapImageBuilder(bitmap).build())
                }
            }
            session.addQueryChunk(prompt)
            session.generateResponse()
        }
    }

    /** Decodes at most [MAX_IMAGE_EDGE] px on the long edge. */
    private fun decodeBounded(path: String): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (longest / inSampleSize > MAX_IMAGE_EDGE) inSampleSize *= 2
        }
        BitmapFactory.decodeFile(path, options)
    }.getOrNull()

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
        // Vision prompts need headroom for the image tokens on top of the text.
        const val MAX_TOKENS_VISION = 2048
        const val MAX_IMAGES = 2
        const val MAX_IMAGE_EDGE = 768
    }
}
