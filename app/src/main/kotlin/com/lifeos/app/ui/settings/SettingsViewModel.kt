package com.lifeos.app.ui.settings

import androidx.lifecycle.viewModelScope
import com.lifeos.app.BuildConfig
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.datastore.AiConfigRepository
import com.lifeos.core.datastore.IntegrationsRepository
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
    val versionName: String = BuildConfig.VERSION_NAME,
    val message: String? = null,
)

sealed interface SettingsUiEvent {
    data class OllamaUrlChanged(val value: String) : SettingsUiEvent
    data class OllamaModelChanged(val value: String) : SettingsUiEvent
    data class HfTokenChanged(val value: String) : SettingsUiEvent
    data class DhlKeyChanged(val value: String) : SettingsUiEvent
    data class DhlSecretChanged(val value: String) : SettingsUiEvent
    data object Save : SettingsUiEvent
    data object DismissMessage : SettingsUiEvent
}

sealed interface SettingsUiEffect

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val aiConfigRepository: AiConfigRepository,
    private val integrationsRepository: IntegrationsRepository,
) : LifeViewModel<SettingsUiState, SettingsUiEvent, SettingsUiEffect>(SettingsUiState()) {

    init {
        viewModelScope.launch {
            val ai = aiConfigRepository.config.first()
            val dhlKey = integrationsRepository.dhlApiKey.first()
            val dhlSecret = integrationsRepository.dhlApiSecret.first()
            updateState {
                it.copy(
                    ollamaBaseUrl = ai.ollamaBaseUrl,
                    ollamaModel = ai.ollamaModel,
                    hfToken = ai.hfToken,
                    dhlApiKey = dhlKey,
                    dhlApiSecret = dhlSecret,
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
}
