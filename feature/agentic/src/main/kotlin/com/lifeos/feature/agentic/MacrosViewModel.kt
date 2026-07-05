package com.lifeos.feature.agentic

import androidx.lifecycle.viewModelScope
import com.lifeos.core.ai.macro.MacroCompiler
import com.lifeos.core.ai.macro.MacroStep
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.agentic.MacroDao
import com.lifeos.core.database.agentic.MacroEntity
import com.lifeos.core.database.evolution.EvolutionDao
import com.lifeos.core.database.evolution.InteractionLogEntity
import com.lifeos.feature.agentic.engine.LifeAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class MacrosUiState(
    val macros: List<MacroEntity> = emptyList(),
    val nlPrompt: String = "",
    val compiling: Boolean = false,
    /** Compiled-but-unsaved preview (dry run) the user must confirm. */
    val preview: List<MacroStep>? = null,
    val serviceEnabled: Boolean = false,
    val running: Boolean = false,
    val message: String? = null,
)

sealed interface MacrosUiEvent {
    data class PromptChanged(val value: String) : MacrosUiEvent
    data object Compile : MacrosUiEvent
    data object SavePreview : MacrosUiEvent
    data object DiscardPreview : MacrosUiEvent
    data class Run(val macro: MacroEntity) : MacrosUiEvent
    data class ToggleEnabled(val macro: MacroEntity) : MacrosUiEvent
    data class Delete(val id: Long) : MacrosUiEvent
    data object RefreshServiceState : MacrosUiEvent
    data object DismissMessage : MacrosUiEvent
}

sealed interface MacrosUiEffect

@HiltViewModel
class MacrosViewModel @Inject constructor(
    private val macroDao: MacroDao,
    private val macroCompiler: MacroCompiler,
    private val evolutionDao: EvolutionDao,
) : LifeViewModel<MacrosUiState, MacrosUiEvent, MacrosUiEffect>(MacrosUiState()) {

    private val json = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            macroDao.observeAll().collect { macros ->
                updateState { it.copy(macros = macros, serviceEnabled = LifeAccessibilityService.isEnabled) }
            }
        }
    }

    override fun onEvent(event: MacrosUiEvent) {
        when (event) {
            is MacrosUiEvent.PromptChanged -> updateState { it.copy(nlPrompt = event.value) }
            MacrosUiEvent.Compile -> viewModelScope.launch {
                updateState { it.copy(compiling = true) }
                val result = macroCompiler.compile(uiState.value.nlPrompt)
                when (result) {
                    is LifeResult.Success -> updateState { it.copy(compiling = false, preview = result.value) }
                    is LifeResult.Failure -> updateState {
                        it.copy(compiling = false, message = result.error.message)
                    }
                }
                evolutionDao.insert(
                    InteractionLogEntity(
                        engine = "ON_DEVICE",
                        kind = "MACRO",
                        accepted = null,
                        at = System.currentTimeMillis(),
                    ),
                )
            }
            MacrosUiEvent.SavePreview -> viewModelScope.launch {
                val state = uiState.value
                val steps = state.preview ?: return@launch
                macroDao.insert(
                    MacroEntity(
                        name = state.nlPrompt.take(60).ifBlank { "Macro" },
                        nlPrompt = state.nlPrompt,
                        stepsJson = json.encodeToString(ListSerializer(MacroStep.serializer()), steps),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                updateState { it.copy(preview = null, nlPrompt = "", message = "Macro saved") }
            }
            MacrosUiEvent.DiscardPreview -> updateState { it.copy(preview = null) }
            is MacrosUiEvent.Run -> viewModelScope.launch {
                val service = LifeAccessibilityService.instance
                if (service == null) {
                    updateState {
                        it.copy(
                            serviceEnabled = false,
                            message = "Enable \"LifeOS Macros\" in accessibility settings first",
                        )
                    }
                    return@launch
                }
                val steps = try {
                    json.decodeFromString(ListSerializer(MacroStep.serializer()), event.macro.stepsJson)
                } catch (t: Throwable) {
                    updateState { it.copy(message = "Macro is corrupted: ${t.message}") }
                    return@launch
                }
                updateState { it.copy(running = true) }
                val failure = service.run(steps)
                macroDao.update(event.macro.copy(lastRunAt = System.currentTimeMillis()))
                updateState { it.copy(running = false, message = failure ?: "Macro finished") }
            }
            is MacrosUiEvent.ToggleEnabled -> viewModelScope.launch {
                macroDao.update(event.macro.copy(enabled = !event.macro.enabled))
            }
            is MacrosUiEvent.Delete -> viewModelScope.launch { macroDao.delete(event.id) }
            MacrosUiEvent.RefreshServiceState -> updateState {
                it.copy(serviceEnabled = LifeAccessibilityService.isEnabled)
            }
            MacrosUiEvent.DismissMessage -> updateState { it.copy(message = null) }
        }
    }
}
