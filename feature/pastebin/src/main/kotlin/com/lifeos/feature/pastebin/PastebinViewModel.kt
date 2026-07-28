package com.lifeos.feature.pastebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.feature.pastebin.data.PasteExpiry
import com.lifeos.feature.pastebin.data.PasteRequest
import com.lifeos.feature.pastebin.data.PasteSummary
import com.lifeos.feature.pastebin.data.PasteVisibility
import com.lifeos.feature.pastebin.data.PastebinRepository
import com.lifeos.feature.pastebin.data.ShareDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PastebinUiState(
    val tab: Int = 0,
    // Composer
    val title: String = "",
    val content: String = "",
    val expiry: PasteExpiry = PasteExpiry.NEVER,
    val visibility: PasteVisibility = PasteVisibility.UNLISTED,
    val format: String = "text",
    val burnAfterRead: Boolean = false,
    val password: String = "",
    val posting: Boolean = false,
    val lastUrl: String? = null,
    // Account + list
    val signedIn: Boolean = false,
    val username: String = "",
    val accountPassword: String = "",
    val pastes: List<PasteSummary> = emptyList(),
    val loadingList: Boolean = false,
    // Share defaults
    val shareDefaults: ShareDefaults = ShareDefaults(),
    val message: String? = null,
)

@HiltViewModel
class PastebinViewModel @Inject constructor(
    private val repository: PastebinRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PastebinUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                shareDefaults = repository.shareDefaults(),
                signedIn = repository.userKey().isNotBlank(),
            )
            if (_uiState.value.signedIn) refreshList()
        }
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(tab = index)
        if (index == 1 && _uiState.value.signedIn && _uiState.value.pastes.isEmpty()) refreshList()
    }

    // ---- composer ----------------------------------------------------------
    fun onTitle(value: String) { _uiState.value = _uiState.value.copy(title = value) }
    fun onContent(value: String) { _uiState.value = _uiState.value.copy(content = value) }
    fun onExpiry(value: PasteExpiry) { _uiState.value = _uiState.value.copy(expiry = value) }
    fun onVisibility(value: PasteVisibility) { _uiState.value = _uiState.value.copy(visibility = value) }
    fun onFormat(value: String) { _uiState.value = _uiState.value.copy(format = value) }
    fun onBurn(value: Boolean) { _uiState.value = _uiState.value.copy(burnAfterRead = value) }
    fun onPassword(value: String) { _uiState.value = _uiState.value.copy(password = value) }

    fun createPaste() {
        val state = _uiState.value
        if (state.content.isBlank()) {
            _uiState.value = state.copy(message = "Nothing to paste yet")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(posting = true)
            val result = repository.create(
                PasteRequest(
                    title = state.title.trim().ifBlank { "LifeOS paste" },
                    content = state.content,
                    expiry = state.expiry,
                    visibility = state.visibility,
                    format = state.format,
                    burnAfterRead = state.burnAfterRead,
                    password = state.password,
                ),
                // Burn-after-read is a guest-only feature on Pastebin.
                underAccount = state.signedIn && !state.burnAfterRead,
            )
            _uiState.value = _uiState.value.copy(
                posting = false,
                lastUrl = result.getOrNull(),
                message = result.fold(
                    onSuccess = { "Paste created" },
                    onFailure = { it.message ?: "Could not create the paste" },
                ),
            )
            if (result.isSuccess && _uiState.value.signedIn) refreshList()
        }
    }

    fun clearComposer() {
        _uiState.value = _uiState.value.copy(title = "", content = "", lastUrl = null, password = "")
    }

    // ---- account -----------------------------------------------------------
    fun onUsername(value: String) { _uiState.value = _uiState.value.copy(username = value) }
    fun onAccountPassword(value: String) { _uiState.value = _uiState.value.copy(accountPassword = value) }

    fun signIn() {
        val state = _uiState.value
        viewModelScope.launch {
            val result = repository.signIn(state.username.trim(), state.accountPassword)
            _uiState.value = _uiState.value.copy(
                signedIn = result.isSuccess,
                accountPassword = if (result.isSuccess) "" else state.accountPassword,
                message = result.fold(
                    onSuccess = { "Signed in" },
                    onFailure = { it.message ?: "Sign-in failed" },
                ),
            )
            if (result.isSuccess) refreshList()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _uiState.value = _uiState.value.copy(signedIn = false, pastes = emptyList(), message = "Signed out")
        }
    }

    fun refreshList() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingList = true)
            val result = repository.list()
            _uiState.value = _uiState.value.copy(
                loadingList = false,
                pastes = result.getOrDefault(emptyList()),
                message = result.exceptionOrNull()?.message,
            )
        }
    }

    fun deletePaste(key: String) {
        viewModelScope.launch {
            val result = repository.delete(key)
            _uiState.value = _uiState.value.copy(
                message = result.fold(onSuccess = { "Paste deleted" }, onFailure = { it.message }),
            )
            if (result.isSuccess) refreshList()
        }
    }

    // ---- share defaults ----------------------------------------------------
    fun updateShareDefaults(transform: (ShareDefaults) -> ShareDefaults) {
        val next = transform(_uiState.value.shareDefaults)
        _uiState.value = _uiState.value.copy(shareDefaults = next)
        viewModelScope.launch { repository.setShareDefaults(next) }
    }

    fun dismissMessage() { _uiState.value = _uiState.value.copy(message = null) }
}
