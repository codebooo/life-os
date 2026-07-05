package com.lifeos.feature.smarthome.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lifeos.core.common.coroutines.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class HaEntity(val entityId: String, val friendlyName: String, val state: String)

data class HaConfig(val baseUrl: String, val token: String)

/** Home Assistant REST client (§Module 23, §8.7). Token stays on-device. */
@Singleton
class HaClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) {

    val config: Flow<HaConfig> = dataStore.data.map {
        HaConfig(baseUrl = it[KEY_URL] ?: "", token = it[KEY_TOKEN] ?: "")
    }

    suspend fun saveConfig(config: HaConfig) {
        dataStore.edit {
            it[KEY_URL] = config.baseUrl.trim().trimEnd('/')
            it[KEY_TOKEN] = config.token.trim()
        }
    }

    suspend fun states(): List<HaEntity> = withContext(dispatchers.io) {
        val cfg = requireConfig()
        val body = okHttpClient.newCall(
            Request.Builder()
                .url("${cfg.baseUrl}/api/states")
                .header("Authorization", "Bearer ${cfg.token}")
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HA returned HTTP ${response.code}")
            response.body.string()
        }
        json.decodeFromString<List<StateDto>>(body)
            .filter { it.entity_id.substringBefore('.') in INTERESTING_DOMAINS }
            .map {
                HaEntity(
                    entityId = it.entity_id,
                    friendlyName = it.attributes?.friendly_name ?: it.entity_id,
                    state = it.state,
                )
            }
    }

    /** Calls a service, e.g. ("scene", "turn_on", "scene.movie_night") — R11's executor. */
    suspend fun callService(domain: String, service: String, entityId: String) =
        withContext(dispatchers.io) {
            val cfg = requireConfig()
            val payload = """{"entity_id":"$entityId"}"""
            okHttpClient.newCall(
                Request.Builder()
                    .url("${cfg.baseUrl}/api/services/$domain/$service")
                    .header("Authorization", "Bearer ${cfg.token}")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build(),
            ).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HA returned HTTP ${response.code}")
            }
        }

    suspend fun toggle(entityId: String) {
        callService("homeassistant", "toggle", entityId)
    }

    private suspend fun requireConfig(): HaConfig {
        val cfg = config.first()
        check(cfg.baseUrl.isNotBlank() && cfg.token.isNotBlank()) {
            "Home Assistant not configured — set URL and long-lived token"
        }
        return cfg
    }

    @Suppress("PropertyName")
    @Serializable
    internal data class StateDto(
        val entity_id: String = "",
        val state: String = "",
        val attributes: AttributesDto? = null,
    )

    @Suppress("PropertyName")
    @Serializable
    internal data class AttributesDto(val friendly_name: String? = null)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val INTERESTING_DOMAINS = setOf("light", "switch", "scene", "climate", "media_player", "cover")
        val KEY_URL = stringPreferencesKey("ha_base_url")
        val KEY_TOKEN = stringPreferencesKey("ha_token")
    }
}
