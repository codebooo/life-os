package com.lifeos.core.common.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Base ViewModel enforcing the plan's convention (§1.6): one immutable
 * [UiState] StateFlow, an [onEvent] entry point, and a one-shot effect channel.
 */
abstract class LifeViewModel<UiState, UiEvent, UiEffect>(initialState: UiState) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = effectChannel.receiveAsFlow()

    abstract fun onEvent(event: UiEvent)

    protected fun updateState(transform: (UiState) -> UiState) = _uiState.update(transform)

    protected fun sendEffect(effect: UiEffect) {
        effectChannel.trySend(effect)
    }
}
