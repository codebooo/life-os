package com.lifeos.feature.chat.settings

import androidx.lifecycle.viewModelScope
import com.lifeos.core.ai.engine.gemma.DownloadState
import com.lifeos.core.ai.engine.gemma.GemmaEngine
import com.lifeos.core.ai.engine.gemma.GemmaVariant
import com.lifeos.core.ai.engine.gemma.ModelDownloader
import com.lifeos.core.ai.engine.ollama.OllamaEngine
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.datastore.AiConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiSettingsUiState(
    val ollamaBaseUrl: String = "",
    val ollamaModel: String = "",
    val onDeviceModelPath: String = "",
    val onDeviceModelInstalled: Boolean = false,
    val hfToken: String = "",
    val variants: List<GemmaVariant> = emptyList(),
    val installedVariantIds: Set<String> = emptySet(),
    val downloadingId: String? = null,
    val downloadPercent: Int = 0,
    val testing: Boolean = false,
    val testResult: String? = null,
    val loaded: Boolean = false,
)

sealed interface AiSettingsUiEvent {
    data class BaseUrlChanged(val value: String) : AiSettingsUiEvent
    data class ModelChanged(val value: String) : AiSettingsUiEvent
    data class ModelPathChanged(val value: String) : AiSettingsUiEvent
    data class HfTokenChanged(val value: String) : AiSettingsUiEvent
    data class DownloadModel(val variant: GemmaVariant) : AiSettingsUiEvent
    data class DeleteModel(val variant: GemmaVariant) : AiSettingsUiEvent
    data object Save : AiSettingsUiEvent
    data object TestConnection : AiSettingsUiEvent
}

sealed interface AiSettingsUiEffect {
    data object Saved : AiSettingsUiEffect
}

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val aiConfigRepository: AiConfigRepository,
    private val ollamaEngine: OllamaEngine,
    private val gemmaEngine: GemmaEngine,
    private val modelDownloader: ModelDownloader,
) : LifeViewModel<AiSettingsUiState, AiSettingsUiEvent, AiSettingsUiEffect>(AiSettingsUiState()) {

    init {
        viewModelScope.launch {
            val config = aiConfigRepository.config.first()
            val deviceModelInstalled = gemmaEngine.isAvailable()
            updateState {
                it.copy(
                    ollamaBaseUrl = config.ollamaBaseUrl,
                    ollamaModel = config.ollamaModel,
                    onDeviceModelPath = config.onDeviceModelPath,
                    onDeviceModelInstalled = deviceModelInstalled,
                    hfToken = config.hfToken,
                    variants = modelDownloader.variants,
                    installedVariantIds = modelDownloader.installedVariantIds(),
                    loaded = true,
                )
            }
        }
    }

    override fun onEvent(event: AiSettingsUiEvent) {
        when (event) {
            is AiSettingsUiEvent.BaseUrlChanged -> updateState { it.copy(ollamaBaseUrl = event.value) }
            is AiSettingsUiEvent.ModelChanged -> updateState { it.copy(ollamaModel = event.value) }
            is AiSettingsUiEvent.ModelPathChanged -> updateState { it.copy(onDeviceModelPath = event.value) }
            is AiSettingsUiEvent.HfTokenChanged -> updateState { it.copy(hfToken = event.value) }
            is AiSettingsUiEvent.DownloadModel -> download(event.variant)
            is AiSettingsUiEvent.DeleteModel -> {
                modelDownloader.delete(event.variant)
                updateState { it.copy(installedVariantIds = modelDownloader.installedVariantIds()) }
            }
            AiSettingsUiEvent.Save -> save()
            AiSettingsUiEvent.TestConnection -> testConnection()
        }
    }

    private fun save() {
        viewModelScope.launch {
            val state = uiState.value
            aiConfigRepository.setOllamaBaseUrl(state.ollamaBaseUrl)
            aiConfigRepository.setOllamaModel(state.ollamaModel)
            aiConfigRepository.setOnDeviceModelPath(state.onDeviceModelPath)
            aiConfigRepository.setHfToken(state.hfToken)
            sendEffect(AiSettingsUiEffect.Saved)
        }
    }

    private fun download(variant: GemmaVariant) {
        if (uiState.value.downloadingId != null) return
        updateState { it.copy(downloadingId = variant.id, downloadPercent = 0, testResult = null) }
        viewModelScope.launch {
            aiConfigRepository.setHfToken(uiState.value.hfToken)
            try {
                modelDownloader.download(variant).collect { state ->
                    when (state) {
                        is DownloadState.Progress -> updateState {
                            it.copy(
                                downloadPercent = if (state.totalBytes > 0) {
                                    (state.downloadedBytes * 100 / state.totalBytes).toInt()
                                } else 0,
                            )
                        }
                        is DownloadState.Done -> updateState {
                            it.copy(
                                downloadingId = null,
                                onDeviceModelPath = state.path,
                                onDeviceModelInstalled = true,
                                installedVariantIds = modelDownloader.installedVariantIds(),
                                testResult = "Model installed — on-device AI ready",
                            )
                        }
                        is DownloadState.Failed -> updateState {
                            it.copy(downloadingId = null, testResult = state.message)
                        }
                    }
                }
            } catch (e: Exception) {
                updateState { it.copy(downloadingId = null, testResult = e.message ?: "Download failed") }
            }
        }
    }

    private fun testConnection() {
        viewModelScope.launch {
            save()
            updateState { it.copy(testing = true, testResult = null) }
            val nasOk = ollamaEngine.isAvailable()
            val deviceOk = gemmaEngine.isAvailable()
            updateState {
                it.copy(
                    testing = false,
                    onDeviceModelInstalled = deviceOk,
                    testResult = buildString {
                        append(if (nasOk) "NAS Ollama: reachable" else "NAS Ollama: unreachable")
                        append(" · ")
                        append(if (deviceOk) "On-device model: installed" else "On-device model: not found")
                    },
                )
            }
        }
    }
}
