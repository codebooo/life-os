package com.lifeos.feature.email

import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.email.EmailMessageEntity
import com.lifeos.feature.email.data.EmailRepository
import com.lifeos.feature.email.data.ImapConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmailUiState(
    val emails: List<EmailMessageEntity> = emptyList(),
    val refreshing: Boolean = false,
    val showSettings: Boolean = false,
    val host: String = "",
    val port: String = "1143",
    val user: String = "",
    val password: String = "",
    val message: String? = null,
)

sealed interface EmailUiEvent {
    data object Refresh : EmailUiEvent
    data object ToggleSettings : EmailUiEvent
    data class HostChanged(val value: String) : EmailUiEvent
    data class PortChanged(val value: String) : EmailUiEvent
    data class UserChanged(val value: String) : EmailUiEvent
    data class PasswordChanged(val value: String) : EmailUiEvent
    data object SaveSettings : EmailUiEvent
    data object DismissMessage : EmailUiEvent
}

sealed interface EmailUiEffect

@HiltViewModel
class EmailViewModel @Inject constructor(
    private val emailRepository: EmailRepository,
) : LifeViewModel<EmailUiState, EmailUiEvent, EmailUiEffect>(EmailUiState()) {

    init {
        viewModelScope.launch {
            emailRepository.observeEmails().collect { emails ->
                updateState { it.copy(emails = emails) }
            }
        }
        viewModelScope.launch {
            val cfg = emailRepository.config.first()
            updateState {
                it.copy(host = cfg.host, port = cfg.port.toString(), user = cfg.user, password = cfg.password)
            }
        }
    }

    override fun onEvent(event: EmailUiEvent) {
        when (event) {
            EmailUiEvent.Refresh -> refresh()
            EmailUiEvent.ToggleSettings -> updateState { it.copy(showSettings = !it.showSettings) }
            is EmailUiEvent.HostChanged -> updateState { it.copy(host = event.value) }
            is EmailUiEvent.PortChanged -> updateState { it.copy(port = event.value) }
            is EmailUiEvent.UserChanged -> updateState { it.copy(user = event.value) }
            is EmailUiEvent.PasswordChanged -> updateState { it.copy(password = event.value) }
            EmailUiEvent.SaveSettings -> viewModelScope.launch {
                val state = uiState.value
                emailRepository.saveConfig(
                    ImapConfig(
                        host = state.host,
                        port = state.port.toIntOrNull() ?: 1143,
                        user = state.user,
                        password = state.password,
                    ),
                )
                updateState { it.copy(showSettings = false, message = "Saved") }
            }
            EmailUiEvent.DismissMessage -> updateState { it.copy(message = null) }
        }
    }

    private fun refresh() {
        if (uiState.value.refreshing) return
        updateState { it.copy(refreshing = true) }
        viewModelScope.launch {
            when (val result = emailRepository.refresh()) {
                is LifeResult.Success ->
                    updateState { it.copy(refreshing = false, message = "${result.value} new") }
                is LifeResult.Failure ->
                    updateState { it.copy(refreshing = false, message = result.error.message) }
            }
        }
    }
}
