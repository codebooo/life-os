package com.lifeos.feature.smarthome

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.feature.smarthome.data.HaClient
import com.lifeos.feature.smarthome.data.HaConfig
import com.lifeos.feature.smarthome.data.HaEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SmartHomeUiState(
    val baseUrl: String = "",
    val token: String = "",
    val entities: List<HaEntity> = emptyList(),
    val showSettings: Boolean = false,
    val message: String? = null,
)

sealed interface SmartHomeUiEvent {
    data class UrlChanged(val value: String) : SmartHomeUiEvent
    data class TokenChanged(val value: String) : SmartHomeUiEvent
    data object SaveAndConnect : SmartHomeUiEvent
    data object Refresh : SmartHomeUiEvent
    data object ToggleSettings : SmartHomeUiEvent
    data class Toggle(val entityId: String) : SmartHomeUiEvent
    data class RunScene(val entityId: String) : SmartHomeUiEvent
    data object DismissMessage : SmartHomeUiEvent
}

sealed interface SmartHomeUiEffect

@HiltViewModel
class SmartHomeViewModel @Inject constructor(
    private val haClient: HaClient,
) : LifeViewModel<SmartHomeUiState, SmartHomeUiEvent, SmartHomeUiEffect>(SmartHomeUiState()) {

    init {
        viewModelScope.launch {
            val cfg = haClient.config.first()
            updateState { it.copy(baseUrl = cfg.baseUrl, token = cfg.token) }
            if (cfg.baseUrl.isNotBlank() && cfg.token.isNotBlank()) refresh()
        }
    }

    override fun onEvent(event: SmartHomeUiEvent) {
        when (event) {
            is SmartHomeUiEvent.UrlChanged -> updateState { it.copy(baseUrl = event.value) }
            is SmartHomeUiEvent.TokenChanged -> updateState { it.copy(token = event.value) }
            SmartHomeUiEvent.SaveAndConnect -> viewModelScope.launch {
                haClient.saveConfig(HaConfig(uiState.value.baseUrl, uiState.value.token))
                updateState { it.copy(showSettings = false) }
                refresh()
            }
            SmartHomeUiEvent.Refresh -> refresh()
            SmartHomeUiEvent.ToggleSettings ->
                updateState { it.copy(showSettings = !it.showSettings) }
            is SmartHomeUiEvent.Toggle -> viewModelScope.launch {
                try {
                    haClient.toggle(event.entityId)
                } catch (e: Exception) {
                    updateState { it.copy(message = e.message) }
                }
                refresh()
            }
            is SmartHomeUiEvent.RunScene -> viewModelScope.launch {
                try {
                    haClient.callService("scene", "turn_on", event.entityId)
                    updateState { it.copy(message = "Scene running") }
                } catch (e: Exception) {
                    updateState { it.copy(message = e.message) }
                }
            }
            SmartHomeUiEvent.DismissMessage -> updateState { it.copy(message = null) }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            try {
                val states = haClient.states()
                updateState { it.copy(entities = states) }
            } catch (e: Exception) {
                updateState { it.copy(message = e.message ?: "Connection failed") }
            }
        }
    }
}
