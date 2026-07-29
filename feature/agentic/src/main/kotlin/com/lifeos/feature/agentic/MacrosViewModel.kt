package com.lifeos.feature.agentic

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.lifeos.core.ai.macro.MacroArg
import com.lifeos.core.ai.macro.MacroCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** A macro being written by hand or edited (§Module 12). */
data class MacroDraft(
    val id: Long = 0,
    val name: String = "",
    val steps: List<MacroStep> = emptyList(),
    val nlPrompt: String = "",
)

data class MacrosUiState(
    val macros: List<MacroEntity> = emptyList(),
    /** Open editor, for a new macro or an existing one. */
    val draft: MacroDraft? = null,
    /** Installed app labels, offered when a step needs an app. */
    val installedApps: List<String> = emptyList(),
    val nlPrompt: String = "",
    val compiling: Boolean = false,
    /** Compiled-but-unsaved preview (dry run) the user must confirm. */
    val preview: List<MacroStep>? = null,
    val serviceEnabled: Boolean = false,
    val running: Boolean = false,
    /** Macro opened in the detail screen, or null for the list. */
    val detail: MacroEntity? = null,
    val message: String? = null,
)

sealed interface MacrosUiEvent {
    data class PromptChanged(val value: String) : MacrosUiEvent
    /** Opens an empty editor (the "+" button). */
    data object NewMacro : MacrosUiEvent
    /** Opens the editor on an existing macro, however it was created. */
    data class EditMacro(val macro: MacroEntity) : MacrosUiEvent
    data class DraftNameChanged(val value: String) : MacrosUiEvent
    data class AddStep(val action: String) : MacrosUiEvent
    data class UpdateStep(val index: Int, val step: MacroStep) : MacrosUiEvent
    data class RemoveStep(val index: Int) : MacrosUiEvent
    data class MoveStep(val index: Int, val delta: Int) : MacrosUiEvent
    data object SaveDraft : MacrosUiEvent
    data object CloseDraft : MacrosUiEvent
    /** Runs whatever is in the editor without saving it. */
    data object TestDraft : MacrosUiEvent
    data object Compile : MacrosUiEvent
    data object SavePreview : MacrosUiEvent
    data object DiscardPreview : MacrosUiEvent
    data class Run(val macro: MacroEntity) : MacrosUiEvent
    data class ToggleEnabled(val macro: MacroEntity) : MacrosUiEvent
    data class Delete(val id: Long) : MacrosUiEvent
    data class OpenDetail(val macro: MacroEntity) : MacrosUiEvent
    data object CloseDetail : MacrosUiEvent
    data class Rename(val macro: MacroEntity, val name: String) : MacrosUiEvent
    data object RefreshServiceState : MacrosUiEvent
    data object DismissMessage : MacrosUiEvent
}

sealed interface MacrosUiEffect

@HiltViewModel
class MacrosViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val macroDao: MacroDao,
    private val macroCompiler: MacroCompiler,
    private val evolutionDao: EvolutionDao,
) : LifeViewModel<MacrosUiState, MacrosUiEvent, MacrosUiEffect>(MacrosUiState()) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Bound instance OR the settings flag — either means the banner can go. */
    private fun serviceOn(): Boolean =
        LifeAccessibilityService.isEnabled || LifeAccessibilityService.isEnabledInSettings(context)

    init {
        viewModelScope.launch {
            macroDao.observeAll().collect { macros ->
                updateState { it.copy(macros = macros, serviceEnabled = serviceOn()) }
            }
        }
        viewModelScope.launch {
            val labels = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                    0,
                )
                    .mapNotNull { it.activityInfo?.applicationInfo }
                    .distinctBy { it.packageName }
                    .map { runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault(it.packageName) }
                    .sorted()
            }
            updateState { it.copy(installedApps = labels) }
        }
    }

    override fun onEvent(event: MacrosUiEvent) {
        when (event) {
            is MacrosUiEvent.PromptChanged -> updateState { it.copy(nlPrompt = event.value) }
            MacrosUiEvent.NewMacro -> updateState { it.copy(draft = MacroDraft()) }
            is MacrosUiEvent.EditMacro -> updateState {
                it.copy(
                    detail = null,
                    draft = MacroDraft(
                        id = event.macro.id,
                        name = event.macro.name,
                        steps = decode(event.macro.stepsJson),
                        nlPrompt = event.macro.nlPrompt,
                    ),
                )
            }
            is MacrosUiEvent.DraftNameChanged -> updateDraft { it.copy(name = event.value) }
            is MacrosUiEvent.AddStep -> updateDraft { draft ->
                draft.copy(steps = draft.steps + MacroStep(action = event.action))
            }
            is MacrosUiEvent.UpdateStep -> updateDraft { draft ->
                draft.copy(
                    steps = draft.steps.toMutableList().also { list ->
                        if (event.index in list.indices) list[event.index] = event.step
                    },
                )
            }
            is MacrosUiEvent.RemoveStep -> updateDraft { draft ->
                draft.copy(steps = draft.steps.filterIndexed { index, _ -> index != event.index })
            }
            is MacrosUiEvent.MoveStep -> updateDraft { draft ->
                val to = event.index + event.delta
                if (event.index !in draft.steps.indices || to !in draft.steps.indices) {
                    draft
                } else {
                    draft.copy(
                        steps = draft.steps.toMutableList().also { list ->
                            list.add(to, list.removeAt(event.index))
                        },
                    )
                }
            }
            MacrosUiEvent.CloseDraft -> updateState { it.copy(draft = null) }
            MacrosUiEvent.SaveDraft -> viewModelScope.launch { saveDraft() }
            MacrosUiEvent.TestDraft -> viewModelScope.launch {
                val steps = uiState.value.draft?.steps.orEmpty()
                if (steps.isEmpty()) {
                    updateState { it.copy(message = "Add a step first") }
                } else {
                    runSteps(steps, macro = null)
                }
            }
            MacrosUiEvent.Compile -> viewModelScope.launch {
                updateState { it.copy(compiling = true) }
                val result = macroCompiler.compile(uiState.value.nlPrompt, uiState.value.installedApps)
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
            MacrosUiEvent.SavePreview -> {
                // Compiled steps land in the editor, so a wrong guess can be
                // fixed before it is ever saved or run.
                val state = uiState.value
                val steps = state.preview.orEmpty()
                updateState {
                    it.copy(
                        preview = null,
                        draft = MacroDraft(
                            name = state.nlPrompt.take(60).ifBlank { "Macro" },
                            steps = steps,
                            nlPrompt = state.nlPrompt,
                        ),
                        nlPrompt = "",
                    )
                }
            }
            MacrosUiEvent.DiscardPreview -> updateState { it.copy(preview = null) }
            is MacrosUiEvent.Run -> viewModelScope.launch {
                runSteps(decode(event.macro.stepsJson), event.macro)
            }
            is MacrosUiEvent.ToggleEnabled -> viewModelScope.launch {
                macroDao.update(event.macro.copy(enabled = !event.macro.enabled))
            }
            is MacrosUiEvent.Delete -> viewModelScope.launch {
                macroDao.delete(event.id)
                if (uiState.value.detail?.id == event.id) updateState { it.copy(detail = null) }
                if (uiState.value.draft?.id == event.id) updateState { it.copy(draft = null) }
            }
            is MacrosUiEvent.OpenDetail -> updateState { it.copy(detail = event.macro) }
            MacrosUiEvent.CloseDetail -> updateState { it.copy(detail = null) }
            is MacrosUiEvent.Rename -> viewModelScope.launch {
                val renamed = event.macro.copy(name = event.name.trim().ifBlank { event.macro.name })
                macroDao.update(renamed)
                updateState { it.copy(detail = renamed) }
            }
            MacrosUiEvent.RefreshServiceState -> updateState {
                it.copy(serviceEnabled = serviceOn())
            }
            MacrosUiEvent.DismissMessage -> updateState { it.copy(message = null) }
        }
    }

    private fun updateDraft(transform: (MacroDraft) -> MacroDraft) {
        updateState { state -> state.copy(draft = state.draft?.let(transform)) }
    }

    private fun decode(stepsJson: String): List<MacroStep> = runCatching {
        json.decodeFromString(ListSerializer(MacroStep.serializer()), stepsJson)
    }.getOrDefault(emptyList())

    private suspend fun saveDraft() {
        val draft = uiState.value.draft ?: return
        if (draft.steps.isEmpty()) {
            updateState { it.copy(message = "A macro needs at least one step") }
            return
        }
        val missing = draft.steps.withIndex().firstOrNull { (_, step) ->
            when (MacroCatalog.byAction[step.action]?.arg) {
                MacroArg.TARGET -> step.target.isNullOrBlank()
                MacroArg.TEXT -> step.text.isNullOrBlank()
                MacroArg.DELAY -> step.delayMs == null
                else -> false
            }
        }
        if (missing != null) {
            updateState {
                it.copy(message = "Step ${missing.index + 1} (${missing.value.action}) still needs a value")
            }
            return
        }
        val stepsJson = json.encodeToString(ListSerializer(MacroStep.serializer()), draft.steps)
        val name = draft.name.trim().ifBlank { "Macro" }
        if (draft.id == 0L) {
            macroDao.insert(
                MacroEntity(
                    name = name,
                    nlPrompt = draft.nlPrompt,
                    stepsJson = stepsJson,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        } else {
            val existing = macroDao.byId(draft.id)
            if (existing != null) {
                macroDao.update(existing.copy(name = name, stepsJson = stepsJson))
            }
        }
        updateState { it.copy(draft = null, message = "Macro saved") }
    }

    /** Shared by Run and the editor's Test button. */
    private suspend fun runSteps(steps: List<MacroStep>, macro: MacroEntity?) {
                val service = LifeAccessibilityService.instance
                if (service == null) {
                    val enabledInSettings = LifeAccessibilityService.isEnabledInSettings(context)
                    updateState {
                        it.copy(
                            serviceEnabled = enabledInSettings,
                            message = if (enabledInSettings) {
                                "Service enabled but not connected yet — toggle \"LifeOS Macros\" off and on once"
                            } else {
                                "Enable \"LifeOS Macros\" in accessibility settings first"
                            },
                        )
                    }
                    return
                }
                if (steps.isEmpty()) {
                    updateState { it.copy(message = "That macro has no steps") }
                    return
                }
                updateState { it.copy(running = true) }
                val failure = service.run(steps)
                macro?.let { macroDao.update(it.copy(lastRunAt = System.currentTimeMillis())) }
                updateState { it.copy(running = false, message = failure ?: "Macro finished") }
    }
}
