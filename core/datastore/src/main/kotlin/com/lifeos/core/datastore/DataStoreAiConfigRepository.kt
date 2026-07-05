package com.lifeos.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DataStoreAiConfigRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AiConfigRepository {

    override val config: Flow<AiConfig> = dataStore.data.map { prefs ->
        AiConfig(
            ollamaBaseUrl = prefs[KEY_OLLAMA_BASE_URL] ?: "",
            ollamaModel = prefs[KEY_OLLAMA_MODEL] ?: DEFAULT_OLLAMA_MODEL,
            onDeviceModelPath = prefs[KEY_ON_DEVICE_MODEL_PATH] ?: "",
        )
    }

    override suspend fun setOllamaBaseUrl(url: String) {
        dataStore.edit { it[KEY_OLLAMA_BASE_URL] = url.trim().trimEnd('/') }
    }

    override suspend fun setOllamaModel(model: String) {
        dataStore.edit { it[KEY_OLLAMA_MODEL] = model.trim() }
    }

    override suspend fun setOnDeviceModelPath(path: String) {
        dataStore.edit { it[KEY_ON_DEVICE_MODEL_PATH] = path.trim() }
    }

    private companion object {
        val KEY_OLLAMA_BASE_URL = stringPreferencesKey("ai_ollama_base_url")
        val KEY_OLLAMA_MODEL = stringPreferencesKey("ai_ollama_model")
        val KEY_ON_DEVICE_MODEL_PATH = stringPreferencesKey("ai_on_device_model_path")
        const val DEFAULT_OLLAMA_MODEL = "gemma4:12b"
    }
}
