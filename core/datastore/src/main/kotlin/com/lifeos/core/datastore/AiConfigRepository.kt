package com.lifeos.core.datastore

import kotlinx.coroutines.flow.Flow

/** AI engine endpoints and model choices (§5, §8.2–8.3). */
data class AiConfig(
    /** Base URL of the NAS Ollama instance, e.g. `http://192.168.1.2:11434`. Blank = not configured. */
    val ollamaBaseUrl: String,
    /** Model tag to request from Ollama. */
    val ollamaModel: String,
    /** Absolute path of the on-device Gemma model file. Blank = default models dir. */
    val onDeviceModelPath: String,
    /** Hugging Face token — needed because Google's Gemma repos are license-gated. */
    val hfToken: String,
)

interface AiConfigRepository {
    val config: Flow<AiConfig>

    suspend fun setOllamaBaseUrl(url: String)
    suspend fun setOllamaModel(model: String)
    suspend fun setOnDeviceModelPath(path: String)
    suspend fun setHfToken(token: String)
}
