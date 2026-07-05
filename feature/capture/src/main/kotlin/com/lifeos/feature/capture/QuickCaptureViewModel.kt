package com.lifeos.feature.capture

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.feature.capture.data.CaptureDestination
import com.lifeos.feature.capture.data.CaptureRepository
import com.lifeos.feature.capture.data.DumpItem
import com.lifeos.feature.capture.data.PendingCapture
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuickCaptureUiState(
    val input: String = "",
    val classifying: Boolean = false,
    val pending: PendingCapture? = null,
    val confirmed: CaptureDestination? = null,
    // Brain-dump review ([src 16], R10)
    val dumpCaptureId: Long? = null,
    val dumpItems: List<DumpItem> = emptyList(),
    val dumpSaved: Int = 0,
    val error: String? = null,
)

sealed interface QuickCaptureUiEvent {
    data class InputChanged(val value: String) : QuickCaptureUiEvent
    data object Submit : QuickCaptureUiEvent
    data class Confirm(val destination: CaptureDestination) : QuickCaptureUiEvent
    data class VoiceResult(val transcript: String) : QuickCaptureUiEvent
    data class ConfirmDumpItem(val index: Int) : QuickCaptureUiEvent
    data class DiscardDumpItem(val index: Int) : QuickCaptureUiEvent
    data object SaveAllDumpItems : QuickCaptureUiEvent
    data object Reset : QuickCaptureUiEvent
}

sealed interface QuickCaptureUiEffect {
    data object Dismiss : QuickCaptureUiEffect
}

@HiltViewModel
class QuickCaptureViewModel @Inject constructor(
    private val captureRepository: CaptureRepository,
) : LifeViewModel<QuickCaptureUiState, QuickCaptureUiEvent, QuickCaptureUiEffect>(QuickCaptureUiState()) {

    override fun onEvent(event: QuickCaptureUiEvent) {
        when (event) {
            is QuickCaptureUiEvent.InputChanged -> updateState { it.copy(input = event.value) }
            QuickCaptureUiEvent.Submit -> submit()
            is QuickCaptureUiEvent.Confirm -> confirm(event.destination)
            is QuickCaptureUiEvent.VoiceResult -> onVoice(event.transcript)
            is QuickCaptureUiEvent.ConfirmDumpItem -> confirmDump(event.index)
            is QuickCaptureUiEvent.DiscardDumpItem -> updateState {
                it.copy(dumpItems = it.dumpItems.filterIndexed { i, _ -> i != event.index })
            }
            QuickCaptureUiEvent.SaveAllDumpItems -> saveAllDump()
            QuickCaptureUiEvent.Reset -> updateState { QuickCaptureUiState() }
        }
    }

    private fun submit() {
        val text = uiState.value.input.trim()
        if (text.isEmpty() || uiState.value.classifying) return
        submitInternal(text)
    }

    private fun submitInternal(text: String) {
        updateState { it.copy(input = "", classifying = true, error = null) }
        viewModelScope.launch {
            when (val result = captureRepository.submitQuick(text)) {
                is LifeResult.Success ->
                    updateState { it.copy(classifying = false, pending = result.value) }
                is LifeResult.Failure ->
                    updateState { it.copy(classifying = false, error = result.error.message) }
            }
        }
    }

    private fun onVoice(transcript: String) {
        val text = transcript.trim()
        if (text.isEmpty()) return
        // Long utterances are brain-dumps; short ones flow through quick capture.
        if (text.length < 80 && !text.contains(" and ") && !text.contains(" und ")) {
            updateState { it.copy(input = text) }
            submitInternal(text)
            return
        }
        updateState { it.copy(classifying = true, error = null, input = text) }
        viewModelScope.launch {
            when (val result = captureRepository.submitBrainDump(text)) {
                is LifeResult.Success -> updateState {
                    it.copy(
                        classifying = false,
                        dumpCaptureId = result.value.first,
                        dumpItems = result.value.second,
                    )
                }
                is LifeResult.Failure ->
                    updateState { it.copy(classifying = false, error = result.error.message) }
            }
        }
    }

    private fun confirmDump(index: Int) {
        val state = uiState.value
        val captureId = state.dumpCaptureId ?: return
        val item = state.dumpItems.getOrNull(index) ?: return
        viewModelScope.launch {
            captureRepository.confirmDumpItem(captureId, item)
            updateState {
                it.copy(
                    dumpItems = it.dumpItems.filterIndexed { i, _ -> i != index },
                    dumpSaved = it.dumpSaved + 1,
                )
            }
            if (uiState.value.dumpItems.isEmpty()) sendEffect(QuickCaptureUiEffect.Dismiss)
        }
    }

    private fun saveAllDump() {
        val state = uiState.value
        val captureId = state.dumpCaptureId ?: return
        viewModelScope.launch {
            state.dumpItems.forEach { captureRepository.confirmDumpItem(captureId, it) }
            updateState { it.copy(dumpItems = emptyList(), dumpSaved = it.dumpSaved + state.dumpItems.size) }
            sendEffect(QuickCaptureUiEffect.Dismiss)
        }
    }

    private fun confirm(destination: CaptureDestination) {
        val pending = uiState.value.pending ?: return
        viewModelScope.launch {
            when (val result = captureRepository.confirm(pending, destination)) {
                is LifeResult.Success -> {
                    updateState { it.copy(confirmed = destination) }
                    sendEffect(QuickCaptureUiEffect.Dismiss)
                }
                is LifeResult.Failure ->
                    updateState { it.copy(error = result.error.message) }
            }
        }
    }
}
