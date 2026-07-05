package com.lifeos.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-integration keys/endpoints (§9.3): values are entered at runtime and
 * never live in source. The DHL key comes from the DHL developer portal.
 */
interface IntegrationsRepository {
    val dhlApiKey: Flow<String>
    val dhlApiSecret: Flow<String>
    suspend fun setDhlApiKey(key: String)
    suspend fun setDhlApiSecret(secret: String)

    /** MCP-over-HTTP endpoint for the NAS mail MCP (§8.5); empty = IMAP only. */
    val mailMcpUrl: Flow<String>
    suspend fun setMailMcpUrl(url: String)
}

@Singleton
internal class DataStoreIntegrationsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : IntegrationsRepository {

    override val dhlApiKey: Flow<String> =
        dataStore.data.map { it[KEY_DHL_API_KEY] ?: "" }

    override val dhlApiSecret: Flow<String> =
        dataStore.data.map { it[KEY_DHL_API_SECRET] ?: "" }

    override suspend fun setDhlApiKey(key: String) {
        dataStore.edit { it[KEY_DHL_API_KEY] = key.trim() }
    }

    override suspend fun setDhlApiSecret(secret: String) {
        dataStore.edit { it[KEY_DHL_API_SECRET] = secret.trim() }
    }

    override val mailMcpUrl: Flow<String> =
        dataStore.data.map { it[KEY_MAIL_MCP_URL] ?: "" }

    override suspend fun setMailMcpUrl(url: String) {
        dataStore.edit { it[KEY_MAIL_MCP_URL] = url.trim() }
    }

    private companion object {
        val KEY_DHL_API_KEY = stringPreferencesKey("integration_dhl_api_key")
        val KEY_DHL_API_SECRET = stringPreferencesKey("integration_dhl_api_secret")
        val KEY_MAIL_MCP_URL = stringPreferencesKey("integration_mail_mcp_url")
    }
}
