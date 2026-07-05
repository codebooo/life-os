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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
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

    override suspend fun isAvailable(): Boolean = modelFile()?.exists() == true

    override fun stream(request: AiRequest): Flow<AiChunk> = flow {
        val file = modelFile()
        check(file != null && file.exists()) { "No on-device model at ${file?.absolutePath}" }

        val text = mutex.withLock {
            val inference = loadIfNeeded(file.absolutePath)
            inference.generateResponse(buildPrompt(request))
        }
        emit(AiChunk(text = text.orEmpty(), done = true))
    }.flowOn(dispatchers.io)

    /** Frees the model memory (called from onTrimMemory via the app). */
    suspend fun release() = mutex.withLock {
        llm?.close()
        llm = null
        loadedModelPath = null
        LifeLogger.i(TAG, "Model released")
    }

    private fun loadIfNeeded(path: String): LlmInference {
        val current = llm
        if (current != null && loadedModelPath == path) return current
        current?.close()

        LifeLogger.i(TAG, "Loading on-device model from $path")
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(MAX_TOKENS)
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
        const val MAX_TOKENS = 2048
    }
}
