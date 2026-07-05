package com.lifeos.feature.capture

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.feature.capture.data.CaptureDestination
import com.lifeos.feature.capture.data.CaptureRepository
import com.lifeos.feature.capture.data.PendingCapture
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuickCaptureUiState(
    val input: String = "",
    val classifying: Boolean = false,
    val pending: PendingCapture? = null,
    val confirmed: CaptureDestination? = null,
    val error: String? = null,
)

sealed interface QuickCaptureUiEvent {
    data class InputChanged(val value: String) : QuickCaptureUiEvent
    data object Submit : QuickCaptureUiEvent
    data class Confirm(val destination: CaptureDestination) : QuickCaptureUiEvent
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
            QuickCaptureUiEvent.Reset -> updateState { QuickCaptureUiState() }
        }
    }

    private fun submit() {
        val text = uiState.value.input.trim()
        if (text.isEmpty() || uiState.value.classifying) return
        updateState { it.copy(classifying = true, error = null) }
        viewModelScope.launch {
            when (val result = captureRepository.submitQuick(text)) {
                is LifeResult.Success ->
                    updateState { it.copy(classifying = false, pending = result.value) }
                is LifeResult.Failure ->
                    updateState { it.copy(classifying = false, error = result.error.message) }
            }
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
