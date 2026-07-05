package com.lifeos.feature.nas

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.datastore.AiConfigRepository
import com.lifeos.feature.nas.data.DsmClient
import com.lifeos.feature.nas.data.NasFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Curated NAS-side services LifeOS depends on ([src 23], §8). */
data class ServerApp(
    val name: String,
    val purpose: String,
    val setup: String,
    val healthUrl: (baseHost: String) -> String,
    val healthy: Boolean? = null,
)

data class NasUiState(
    val tab: Int = 0,
    val baseUrl: String = "",
    val user: String = "",
    val password: String = "",
    val connected: Boolean = false,
    val busy: Boolean = false,
    val currentPath: String? = null,
    val files: List<NasFile> = emptyList(),
    val serverApps: List<ServerApp> = emptyList(),
    val message: String? = null,
)

sealed interface NasUiEvent {
    data class SelectTab(val index: Int) : NasUiEvent
    data class BaseUrlChanged(val value: String) : NasUiEvent
    data class UserChanged(val value: String) : NasUiEvent
    data class PasswordChanged(val value: String) : NasUiEvent
    data object Connect : NasUiEvent
    data class Open(val path: String) : NasUiEvent
    data object NavigateUp : NasUiEvent
    data object CheckHealth : NasUiEvent
    data object DismissMessage : NasUiEvent
}

sealed interface NasUiEffect

@HiltViewModel
class NasViewModel @Inject constructor(
    private val dsmClient: DsmClient,
    private val dataStore: DataStore<Preferences>,
    private val aiConfigRepository: AiConfigRepository,
) : LifeViewModel<NasUiState, NasUiEvent, NasUiEffect>(NasUiState(serverApps = defaultApps())) {

    private var sid: String? = null

    init {
        viewModelScope.launch {
            val prefs = dataStore.data.map { it }.first()
            updateState {
                it.copy(
                    baseUrl = prefs[KEY_URL] ?: "",
                    user = prefs[KEY_USER] ?: "",
                    password = prefs[KEY_PASSWORD] ?: "",
                )
            }
        }
    }

    override fun onEvent(event: NasUiEvent) {
        when (event) {
            is NasUiEvent.SelectTab -> updateState { it.copy(tab = event.index) }
            is NasUiEvent.BaseUrlChanged -> updateState { it.copy(baseUrl = event.value) }
            is NasUiEvent.UserChanged -> updateState { it.copy(user = event.value) }
            is NasUiEvent.PasswordChanged -> updateState { it.copy(password = event.value) }
            NasUiEvent.Connect -> connect()
            is NasUiEvent.Open -> open(event.path)
            NasUiEvent.NavigateUp -> navigateUp()
            NasUiEvent.CheckHealth -> checkHealth()
            NasUiEvent.DismissMessage -> updateState { it.copy(message = null) }
        }
    }

    private fun connect() {
        val state = uiState.value
        if (state.baseUrl.isBlank() || state.user.isBlank()) return
        updateState { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                dataStore.edit {
                    it[KEY_URL] = state.baseUrl.trim().trimEnd('/')
                    it[KEY_USER] = state.user.trim()
                    it[KEY_PASSWORD] = state.password
                }
                sid = dsmClient.login(state.baseUrl.trim().trimEnd('/'), state.user.trim(), state.password)
                val shares = dsmClient.listShares(state.baseUrl.trim().trimEnd('/'), sid!!)
                updateState { it.copy(busy = false, connected = true, files = shares, currentPath = null) }
            } catch (e: Exception) {
                updateState { it.copy(busy = false, message = e.message ?: "Connection failed") }
            }
        }
    }

    private fun open(path: String) {
        val currentSid = sid ?: return
        viewModelScope.launch {
            try {
                val files = dsmClient.listFolder(uiState.value.baseUrl.trimEnd('/'), currentSid, path)
                updateState { it.copy(files = files, currentPath = path) }
            } catch (e: Exception) {
                updateState { it.copy(message = e.message ?: "Listing failed") }
            }
        }
    }

    private fun navigateUp() {
        val current = uiState.value.currentPath ?: return
        val parent = current.substringBeforeLast('/', "")
        if (parent.isEmpty() || parent == current) {
            val currentSid = sid ?: return
            viewModelScope.launch {
                try {
                    val shares = dsmClient.listShares(uiState.value.baseUrl.trimEnd('/'), currentSid)
                    updateState { it.copy(files = shares, currentPath = null) }
                } catch (e: Exception) {
                    updateState { it.copy(message = e.message) }
                }
            }
        } else {
            open(parent)
        }
    }

    private fun checkHealth() {
        viewModelScope.launch {
            val host = uiState.value.baseUrl
                .removePrefix("https://").removePrefix("http://").substringBefore(':')
            val ollamaUrl = aiConfigRepository.config.first().ollamaBaseUrl
            val updated = uiState.value.serverApps.map { app ->
                val url = if (app.name == "Ollama" && ollamaUrl.isNotBlank()) {
                    "$ollamaUrl/api/tags"
                } else {
                    app.healthUrl(host)
                }
                app.copy(healthy = if (host.isBlank() && ollamaUrl.isBlank()) null else dsmClient.ping(url))
            }
            updateState { it.copy(serverApps = updated) }
        }
    }

    private companion object {
        val KEY_URL = stringPreferencesKey("nas_dsm_url")
        val KEY_USER = stringPreferencesKey("nas_dsm_user")
        val KEY_PASSWORD = stringPreferencesKey("nas_dsm_password")

        fun defaultApps() = listOf(
            ServerApp(
                name = "Ollama",
                purpose = "Gemma on the NAS — powers heavier AI when reachable",
                setup = "Container Manager: image ollama/ollama, port 11434 LAN-only, then `ollama pull gemma4:12b` (§8.3)",
                healthUrl = { host -> "http://$host:11434/api/tags" },
            ),
            ServerApp(
                name = "Proton Bridge",
                purpose = "IMAP/SMTP endpoint for the Email module",
                setup = "Run Bridge (or hydroxide) in a container; use the Bridge app password in Email settings (§8.5)",
                healthUrl = { host -> "http://$host:1143" },
            ),
            ServerApp(
                name = "Mail MCP",
                purpose = "protonmail-pro-mcp behind HTTP/SSE — the primary mail path",
                setup = "Review + pin the repo, run behind an MCP-over-HTTP wrapper, LAN/VPN only (§8.5)",
                healthUrl = { host -> "http://$host:3000" },
            ),
            ServerApp(
                name = "Home Assistant",
                purpose = "Scenes + zones for the Smart Home module",
                setup = "Container: homeassistant/home-assistant, port 8123; create a long-lived token (§8.7)",
                healthUrl = { host -> "http://$host:8123" },
            ),
        )
    }
}
