package com.lifeos.core.ai.engine.ollama

import com.lifeos.core.ai.engine.AiEngine
import com.lifeos.core.ai.model.AiChunk
import com.lifeos.core.ai.model.AiEngineId
import com.lifeos.core.ai.model.AiRequest
import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.datastore.AiConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemma 4 on the NAS via Ollama's chat API (§5.1, §8.3). Streams NDJSON
 * chunks; health is probed against `/api/tags` and cached for 30 seconds.
 */
@Singleton
class OllamaEngine @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val aiConfigRepository: AiConfigRepository,
    private val dispatchers: DispatcherProvider,
) : AiEngine {

    override val id: AiEngineId = AiEngineId.NAS_OLLAMA

    private val healthClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var lastHealth: Pair<Long, Boolean>? = null

    override suspend fun isAvailable(): Boolean = withContext(dispatchers.io) {
        val baseUrl = aiConfigRepository.config.first().ollamaBaseUrl
        if (baseUrl.isBlank()) return@withContext false

        lastHealth?.let { (at, healthy) ->
            if (System.currentTimeMillis() - at < HEALTH_TTL_MS) return@withContext healthy
        }

        val healthy = try {
            healthClient.newCall(Request.Builder().url("$baseUrl/api/tags").build())
                .execute()
                .use { it.isSuccessful }
        } catch (e: IOException) {
            false
        }
        lastHealth = System.currentTimeMillis() to healthy
        healthy
    }

    override fun stream(request: AiRequest): Flow<AiChunk> = flow {
        val config = aiConfigRepository.config.first()
        check(config.ollamaBaseUrl.isNotBlank()) { "Ollama endpoint not configured" }

        val body = OllamaProtocol
            .encode(OllamaProtocol.buildRequest(config.ollamaModel, request))
            .toRequestBody(JSON_MEDIA_TYPE)
        val httpRequest = Request.Builder()
            .url("${config.ollamaBaseUrl}/api/chat")
            .post(body)
            .build()

        okHttpClient.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Ollama returned HTTP ${response.code}")
            }
            val source = response.body.source()
            while (true) {
                val line = source.readUtf8Line() ?: break
                val chunk = OllamaProtocol.parseChunk(line) ?: continue
                emit(chunk)
                if (chunk.done) break
            }
        }
        LifeLogger.d(TAG, "Stream completed")
    }.flowOn(dispatchers.io)

    private companion object {
        const val TAG = "OllamaEngine"
        const val HEALTH_TTL_MS = 30_000L
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
