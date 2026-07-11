package com.lifeos.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.vault.VaultBlobDao
import com.lifeos.core.database.vault.VaultBlobEntity
import com.lifeos.core.model.vault.VaultMeta
import com.lifeos.core.model.vault.VaultRef
import com.lifeos.core.vault.VaultRepository
import com.lifeos.feature.vault.data.AttachmentRef
import com.lifeos.feature.vault.data.PasswordEntry
import com.lifeos.feature.vault.data.VaultMime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Which surface the vault is showing. */
sealed interface VaultMode {
    data object List : VaultMode
    data class PasswordEditor(val ref: String?, val entry: PasswordEntry) : VaultMode
    data class PasswordView(val ref: String, val entry: PasswordEntry) : VaultMode
    data class TextEditor(val ref: String?, val title: String, val body: String) : VaultMode
    data class TextView(val ref: String, val title: String, val body: String) : VaultMode
    data class ImageView(val title: String, val bytes: ByteArray) : VaultMode
    data class FileInfo(val item: VaultBlobEntity, val bytes: ByteArray) : VaultMode
}

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultBlobDao: VaultBlobDao,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val items: StateFlow<List<VaultBlobEntity>> = vaultBlobDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _mode = MutableStateFlow<VaultMode>(VaultMode.List)
    val mode: StateFlow<VaultMode> = _mode.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun backToList() { _mode.value = VaultMode.List }

    // ---- open existing items -------------------------------------------------

    fun open(item: VaultBlobEntity) {
        viewModelScope.launch {
            _busy.value = true
            when (val result = vaultRepository.openBlob(VaultRef(item.ref))) {
                is LifeResult.Success -> _mode.value = decode(item, result.value)
                is LifeResult.Failure -> Unit
            }
            _busy.value = false
        }
    }

    private fun decode(item: VaultBlobEntity, bytes: ByteArray): VaultMode = when {
        item.mimeType == VaultMime.PASSWORD ->
            runCatching { json.decodeFromString(PasswordEntry.serializer(), bytes.decodeToString()) }
                .getOrDefault(PasswordEntry(title = item.title ?: "Login"))
                .let { VaultMode.PasswordView(item.ref, it) }
        item.mimeType == VaultMime.SECURE_TEXT || item.mimeType == "text/plain" ->
            VaultMode.TextView(item.ref, item.title ?: "Note", bytes.decodeToString())
        item.mimeType.startsWith("image/") ->
            VaultMode.ImageView(item.title ?: "Image", bytes)
        else -> VaultMode.FileInfo(item, bytes)
    }

    // ---- create / edit flows -------------------------------------------------

    fun newPassword() { _mode.value = VaultMode.PasswordEditor(null, PasswordEntry()) }
    fun newText() { _mode.value = VaultMode.TextEditor(null, "", "") }

    fun editPassword(ref: String, entry: PasswordEntry) {
        _mode.value = VaultMode.PasswordEditor(ref, entry)
    }

    fun editText(ref: String, title: String, body: String) {
        _mode.value = VaultMode.TextEditor(ref, title, body)
    }

    fun savePassword(ref: String?, entry: PasswordEntry) {
        viewModelScope.launch {
            _busy.value = true
            ref?.let { vaultRepository.deleteBlob(VaultRef(it)) }
            vaultRepository.putBlob(
                json.encodeToString(PasswordEntry.serializer(), entry).encodeToByteArray(),
                VaultMeta(title = entry.title.ifBlank { "Login" }, mimeType = VaultMime.PASSWORD),
            )
            _busy.value = false
            backToList()
        }
    }

    fun saveText(ref: String?, title: String, body: String) {
        viewModelScope.launch {
            _busy.value = true
            ref?.let { vaultRepository.deleteBlob(VaultRef(it)) }
            vaultRepository.putBlob(
                body.encodeToByteArray(),
                VaultMeta(title = title.ifBlank { "Note" }, mimeType = VaultMime.SECURE_TEXT),
            )
            _busy.value = false
            backToList()
        }
    }

    /** Stores an attachment as its own encrypted blob and returns its reference. */
    suspend fun putAttachment(bytes: ByteArray, name: String, mime: String): AttachmentRef? {
        return when (val result = vaultRepository.putBlob(bytes, VaultMeta(title = name, mimeType = mime))) {
            is LifeResult.Success -> AttachmentRef(result.value.value, name, mime, bytes.size.toLong())
            is LifeResult.Failure -> null
        }
    }

    fun addFile(bytes: ByteArray, name: String, mime: String) {
        viewModelScope.launch {
            _busy.value = true
            vaultRepository.putBlob(bytes, VaultMeta(title = name, mimeType = mime))
            _busy.value = false
        }
    }

    suspend fun openAttachment(ref: String): ByteArray? =
        when (val result = vaultRepository.openBlob(VaultRef(ref))) {
            is LifeResult.Success -> result.value
            is LifeResult.Failure -> null
        }

    fun delete(item: VaultBlobEntity) {
        viewModelScope.launch { vaultRepository.deleteBlob(VaultRef(item.ref)) }
    }

    fun deleteByRef(ref: String) {
        viewModelScope.launch {
            vaultRepository.deleteBlob(VaultRef(ref))
            backToList()
        }
    }
}
