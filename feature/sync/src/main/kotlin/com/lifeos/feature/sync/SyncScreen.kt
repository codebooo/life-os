package com.lifeos.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.database.backup.BackupDao
import com.lifeos.core.datastore.SettingsRepository
import com.lifeos.feature.sync.data.BackupService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SyncUiState(
    val passphrase: String = "",
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val webdavPassword: String = "",
    val keep: Int = 5,
    val busy: Boolean = false,
    val message: String? = null,
    val localBackups: List<String> = emptyList(),
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupService: BackupService,
    backupDao: BackupDao,
) : ViewModel() {

    val runs = backupDao.observeRuns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                passphrase = settingsRepository.backupPassphrase.first(),
                webdavUrl = settingsRepository.backupWebdavUrl.first(),
                webdavUser = settingsRepository.backupWebdavUser.first(),
                webdavPassword = settingsRepository.backupWebdavPassword.first(),
                keep = settingsRepository.backupKeepGenerations.first(),
                localBackups = backupService.localBackups().map { it.name },
            )
        }
    }

    fun onPassphrase(value: String) {
        _uiState.value = _uiState.value.copy(passphrase = value)
        viewModelScope.launch { settingsRepository.setBackupPassphrase(value) }
    }

    fun onUrl(value: String) {
        _uiState.value = _uiState.value.copy(webdavUrl = value)
        viewModelScope.launch { settingsRepository.setBackupWebdavUrl(value) }
    }

    fun onUser(value: String) {
        _uiState.value = _uiState.value.copy(webdavUser = value)
        viewModelScope.launch { settingsRepository.setBackupWebdavUser(value) }
    }

    fun onPassword(value: String) {
        _uiState.value = _uiState.value.copy(webdavPassword = value)
        viewModelScope.launch { settingsRepository.setBackupWebdavPassword(value) }
    }

    fun onKeep(value: String) {
        val keep = value.filter { it.isDigit() }.toIntOrNull() ?: return
        _uiState.value = _uiState.value.copy(keep = keep)
        viewModelScope.launch { settingsRepository.setBackupKeepGenerations(keep) }
    }

    fun backupNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, message = null)
            val result = backupService.backupNow()
            _uiState.value = _uiState.value.copy(
                busy = false,
                localBackups = backupService.localBackups().map { it.name },
                message = result.fold(
                    onSuccess = { outcome ->
                        if (outcome.verified) {
                            "Backed up and verified: ${outcome.fileName}"
                        } else {
                            "Backed up but not verified: ${outcome.detail}"
                        }
                    },
                    onFailure = { it.message ?: "Backup failed" },
                ),
            )
        }
    }

    fun restore(fileName: String) {
        viewModelScope.launch {
            val file = backupService.localBackups().firstOrNull { it.name == fileName } ?: return@launch
            _uiState.value = _uiState.value.copy(busy = true)
            val result = backupService.restoreFrom(file)
            _uiState.value = _uiState.value.copy(
                busy = false,
                message = result.fold(onSuccess = { it }, onFailure = { it.message ?: "Restore failed" }),
            )
        }
    }

    fun dismissMessage() { _uiState.value = _uiState.value.copy(message = null) }
}

/**
 * Sync (§Module Sync): encrypted snapshots of everything, kept locally and
 * pushed to the NAS, with a restore that has actually been tested.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncRoute(viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val runs by viewModel.runs.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sync") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Everything, encrypted", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "A snapshot is a consistent copy of the LifeOS database, encrypted on this phone " +
                                "with your passphrase (AES-256-GCM). It is written next to the app and, if you " +
                                "set a WebDAV URL, pushed to your NAS. Every run is read back and decrypted to " +
                                "prove it works - lose the passphrase and the backup is gone with it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.passphrase,
                    onValueChange = viewModel::onPassphrase,
                    label = { Text("Backup passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = state.webdavUrl,
                    onValueChange = viewModel::onUrl,
                    label = { Text("WebDAV folder URL (optional)") },
                    placeholder = { Text("https://nas.local/remote.php/dav/files/me/LifeOS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.webdavUser,
                        onValueChange = viewModel::onUser,
                        label = { Text("User") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.webdavPassword,
                        onValueChange = viewModel::onPassword,
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = state.keep.toString(),
                    onValueChange = viewModel::onKeep,
                    label = { Text("Local snapshots to keep") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::backupNow, enabled = !state.busy) {
                        Text(if (state.busy) "Working…" else "Back up now")
                    }
                }
            }
            if (state.busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

            if (state.localBackups.isNotEmpty()) {
                item { Text("Restore", style = MaterialTheme.typography.titleMedium) }
                items(state.localBackups) { name ->
                    ListItem(
                        headlineContent = { Text(name) },
                        trailingContent = {
                            OutlinedButton(onClick = { viewModel.restore(name) }, enabled = !state.busy) {
                                Text("Restore")
                            }
                        },
                    )
                }
                item {
                    Text(
                        "A restore is staged and applied the next time LifeOS starts cold - Room holds the " +
                            "database open, so that is the only safe moment to swap it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (runs.isNotEmpty()) {
                item { Text("History", style = MaterialTheme.typography.titleMedium) }
                items(runs, key = { it.id }) { run ->
                    ListItem(
                        headlineContent = { Text("${run.status} ${run.fileName}") },
                        supportingContent = {
                            Text(
                                listOf(
                                    AT.format(Date(run.at)),
                                    "${run.sizeBytes / 1024} KB",
                                    run.detail,
                                ).filter { it.isNotBlank() }.joinToString(" - "),
                            )
                        },
                    )
                }
            }
        }
    }
}

private val AT = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
