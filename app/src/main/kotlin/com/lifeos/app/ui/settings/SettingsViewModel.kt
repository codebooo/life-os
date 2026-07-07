package com.lifeos.app.ui.settings

import androidx.lifecycle.viewModelScope
import com.lifeos.app.BuildConfig
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.datastore.AiConfigRepository
import com.lifeos.core.datastore.IntegrationsRepository
import com.lifeos.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val ollamaBaseUrl: String = "",
    val ollamaModel: String = "",
    val hfToken: String = "",
    val dhlApiKey: String = "",
    val dhlApiSecret: String = "",
    val themePalette: String = "dynamic",
    /** Bottom-bar tab ids currently enabled, in display order (Home pinned). */
    val navBarItems: List<String> = emptyList(),
    /** Home tile arrangement, in display order. */
    val homeOrder: List<String> = emptyList(),
    val versionName: String = BuildConfig.VERSION_NAME,
    val message: String? = null,
)

sealed interface SettingsUiEvent {
    data class OllamaUrlChanged(val value: String) : SettingsUiEvent
    data class OllamaModelChanged(val value: String) : SettingsUiEvent
    data class HfTokenChanged(val value: String) : SettingsUiEvent
    data class DhlKeyChanged(val value: String) : SettingsUiEvent
    data class DhlSecretChanged(val value: String) : SettingsUiEvent
    data class ThemePaletteChanged(val value: String) : SettingsUiEvent
    data class ToggleNavItem(val id: String) : SettingsUiEvent
    /** Moves a nav tab up (-1) or down (+1) in the bar order. */
    data class MoveNavItem(val id: String, val delta: Int) : SettingsUiEvent
    /** Moves a Home tile up (-1) or down (+1) in the arrangement. */
    data class MoveHomeItem(val label: String, val delta: Int) : SettingsUiEvent
    data object Save : SettingsUiEvent
    data object DismissMessage : SettingsUiEvent
}

sealed interface SettingsUiEffect

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val aiConfigRepository: AiConfigRepository,
    private val integrationsRepository: IntegrationsRepository,
    private val settingsRepository: SettingsRepository,
) : LifeViewModel<SettingsUiState, SettingsUiEvent, SettingsUiEffect>(SettingsUiState()) {

    init {
        viewModelScope.launch {
            val ai = aiConfigRepository.config.first()
            val dhlKey = integrationsRepository.dhlApiKey.first()
            val dhlSecret = integrationsRepository.dhlApiSecret.first()
            val palette = settingsRepository.themePalette.first()
            val navItems = settingsRepository.navBarItems.first()
                .ifEmpty { listOf("CALENDAR", "TASKS", "INBOX", "ASSISTANT") }
            val homeOrder = settingsRepository.homeOrder.first().ifEmpty { DEFAULT_HOME_ORDER }
            updateState {
                it.copy(
                    ollamaBaseUrl = ai.ollamaBaseUrl,
                    ollamaModel = ai.ollamaModel,
                    hfToken = ai.hfToken,
                    dhlApiKey = dhlKey,
                    dhlApiSecret = dhlSecret,
                    themePalette = palette,
                    navBarItems = navItems,
                    homeOrder = homeOrder,
                )
            }
        }
    }

    override fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.OllamaUrlChanged -> updateState { it.copy(ollamaBaseUrl = event.value) }
            is SettingsUiEvent.OllamaModelChanged -> updateState { it.copy(ollamaModel = event.value) }
            is SettingsUiEvent.HfTokenChanged -> updateState { it.copy(hfToken = event.value) }
            is SettingsUiEvent.DhlKeyChanged -> updateState { it.copy(dhlApiKey = event.value) }
            is SettingsUiEvent.DhlSecretChanged -> updateState { it.copy(dhlApiSecret = event.value) }
            is SettingsUiEvent.ThemePaletteChanged -> {
                // Theme applies instantly; no Save button involved.
                updateState { it.copy(themePalette = event.value) }
                viewModelScope.launch { settingsRepository.setThemePalette(event.value) }
            }
            is SettingsUiEvent.ToggleNavItem -> {
                val current = uiState.value.navBarItems
                val next = if (event.id in current) current - event.id else current + event.id
                if (next.isEmpty()) return // at least one tab besides Home
                updateState { it.copy(navBarItems = next) }
                viewModelScope.launch { settingsRepository.setNavBarItems(next) }
            }
            is SettingsUiEvent.MoveNavItem -> {
                val next = uiState.value.navBarItems.moved(event.id, event.delta) ?: return
                updateState { it.copy(navBarItems = next) }
                viewModelScope.launch { settingsRepository.setNavBarItems(next) }
            }
            is SettingsUiEvent.MoveHomeItem -> {
                val next = uiState.value.homeOrder.moved(event.label, event.delta) ?: return
                updateState { it.copy(homeOrder = next) }
                viewModelScope.launch { settingsRepository.setHomeOrder(next) }
            }
            SettingsUiEvent.Save -> viewModelScope.launch {
                val state = uiState.value
                aiConfigRepository.setOllamaBaseUrl(state.ollamaBaseUrl)
                aiConfigRepository.setOllamaModel(state.ollamaModel)
                aiConfigRepository.setHfToken(state.hfToken)
                integrationsRepository.setDhlApiKey(state.dhlApiKey)
                integrationsRepository.setDhlApiSecret(state.dhlApiSecret)
                updateState { it.copy(message = "Saved") }
            }
            SettingsUiEvent.DismissMessage -> updateState { it.copy(message = null) }
        }
    }

    companion object {
        /** Canonical Home tiles (must match HomeScreen labels). */
        val DEFAULT_HOME_ORDER = listOf(
            "Notes", "Logger", "Packages", "Finance", "Scan", "Planner", "Books",
            "Routes", "Smart home", "NAS", "Clock", "Focus", "Memex", "Macros", "Evolution",
        )
    }
}

/** Returns the list with [element] shifted by [delta], or null when it can't move. */
private fun <T> List<T>.moved(element: T, delta: Int): List<T>? {
    val from = indexOf(element)
    val to = from + delta
    if (from == -1 || to !in indices) return null
    return toMutableList().apply { add(to, removeAt(from)) }
}
