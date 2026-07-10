package com.lifeos.feature.vault

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.vault.VaultBlobDao
import com.lifeos.core.database.vault.VaultBlobEntity
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.core.model.vault.VaultMeta
import com.lifeos.core.model.vault.VaultRef
import com.lifeos.core.vault.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultBlobDao: VaultBlobDao,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    val items = vaultBlobDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _opened = MutableStateFlow<Pair<VaultBlobEntity, ByteArray>?>(null)
    val opened = _opened.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun addText(title: String, body: String, secret: Boolean) {
        viewModelScope.launch {
            vaultRepository.putBlob(
                body.encodeToByteArray(),
                VaultMeta(title = title, mimeType = if (secret) MIME_SECRET else "text/plain"),
            )
        }
    }

    fun addFile(bytes: ByteArray, name: String, mime: String) {
        viewModelScope.launch {
            _busy.value = true
            vaultRepository.putBlob(bytes, VaultMeta(title = name, mimeType = mime))
            _busy.value = false
        }
    }

    fun open(item: VaultBlobEntity) {
        viewModelScope.launch {
            _busy.value = true
            when (val result = vaultRepository.openBlob(VaultRef(item.ref))) {
                is LifeResult.Success -> _opened.value = item to result.value
                is LifeResult.Failure -> Unit
            }
            _busy.value = false
        }
    }

    fun closeOpened() { _opened.value = null }

    fun delete(item: VaultBlobEntity) {
        viewModelScope.launch { vaultRepository.deleteBlob(VaultRef(item.ref)) }
    }

    companion object {
        const val MIME_SECRET = "application/x-lifeos-secret"
    }
}

/**
 * Vault (§Module Vault): the hidden, biometrically locked store. Reached only
 * via the 5-second reveal on the Home "LifeOS" title — no tile, no nav entry.
 * Texts, passwords and any media live as Tink-encrypted blobs; nothing is
 * readable without passing BiometricPrompt (fingerprint/face or device PIN).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultRoute(viewModel: VaultViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }
    var authFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val activity = context as? FragmentActivity ?: return@LaunchedEffect
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authFailed = true
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Vault")
                .setSubtitle("Biometrics or device PIN")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build(),
        )
    }

    when {
        unlocked -> VaultContent(viewModel)
        else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(if (authFailed) "Locked. Reopen the vault to retry." else "Unlocking…")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultContent(viewModel: VaultViewModel) {
    val items by viewModel.items.collectAsState()
    val opened by viewModel.opened.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val context = LocalContext.current
    var sort by remember { mutableStateOf("Newest") }
    var filter by remember { mutableStateOf("All") }
    var addMenu by remember { mutableStateOf(false) }
    var addText by remember { mutableStateOf<Boolean?>(null) } // true = password, false = note

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = uri.lastPathSegment?.substringAfterLast('/')?.take(60) ?: "file"
        resolver.openInputStream(uri)?.use { input ->
            viewModel.addFile(input.readBytes(), name, mime)
        }
    }

    val shown = items
        .filter {
            when (filter) {
                "Media" -> it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")
                "Texts" -> it.mimeType == "text/plain"
                "Passwords" -> it.mimeType == VaultViewModel.MIME_SECRET
                else -> true
            }
        }
        .let { list ->
            when (sort) {
                "Name" -> list.sortedBy { it.title ?: it.ref }
                "Size" -> list.sortedByDescending { it.sizeBytes }
                "Type" -> list.sortedBy { it.mimeType }
                else -> list.sortedByDescending { it.createdAt }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault 🔒") },
                actions = {
                    Box {
                        var sortMenu by remember { mutableStateOf(false) }
                        TextButton(onClick = { sortMenu = true }) { Text(sort) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("Newest", "Name", "Size", "Type").forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = { sort = option; sortMenu = false })
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { addMenu = true }) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.padding(12.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Add, contentDescription = "Add")
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Password / secret") },
                        leadingIcon = { Icon(Icons.Filled.Key, null) },
                        onClick = { addText = true; addMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Secure text") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                        onClick = { addText = false; addMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Import file (photo, video, …)") },
                        leadingIcon = { Icon(Icons.Filled.Upload, null) },
                        onClick = { importLauncher.launch("*/*"); addMenu = false },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("All", "Media", "Texts", "Passwords").forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
                }
            }
            if (shown.isEmpty()) {
                EmptyState(
                    title = "Vault is empty",
                    description = "Everything here is Tink-encrypted at rest and only opens after biometrics.",
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    items(shown, key = { it.ref }) { item ->
                        Card(onClick = { viewModel.open(item) }) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        when {
                                            item.mimeType.startsWith("image/") -> Icons.Filled.Image
                                            item.mimeType.startsWith("video/") -> Icons.Filled.Movie
                                            item.mimeType == VaultViewModel.MIME_SECRET -> Icons.Filled.Key
                                            else -> Icons.AutoMirrored.Filled.Notes
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    IconButton(onClick = { viewModel.delete(item) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                    }
                                }
                                Text(item.title ?: "Untitled", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(
                                    "${item.mimeType.substringBefore('/')} · ${item.sizeBytes / 1024} KB · ${DAY.format(Date(item.createdAt))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    addText?.let { isSecret ->
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addText = null },
            title = { Text(if (isSecret) "New password" else "New secure text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Label") }, singleLine = true)
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text(if (isSecret) "Username / password / notes" else "Text") },
                        minLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank() || body.isNotBlank()) {
                        viewModel.addText(title.ifBlank { "Untitled" }, body, isSecret)
                    }
                    addText = null
                }) { Text("Encrypt & save") }
            },
            dismissButton = { TextButton(onClick = { addText = null }) { Text("Cancel") } },
        )
    }

    opened?.let { (item, bytes) ->
        AlertDialog(
            onDismissRequest = viewModel::closeOpened,
            title = { Text(item.title ?: "Untitled") },
            text = {
                when {
                    item.mimeType.startsWith("image/") -> {
                        val bitmap = remember(item.ref) {
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                        if (bitmap != null) {
                            Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
                        } else {
                            Text("Couldn't decode the image.")
                        }
                    }
                    item.mimeType == "text/plain" || item.mimeType == VaultViewModel.MIME_SECRET ->
                        Text(bytes.decodeToString())
                    else -> Text(
                        "${item.mimeType} · ${bytes.size / 1024} KB.\nUse Export to view it in a player.",
                    )
                }
            },
            confirmButton = {
                if (!item.mimeType.startsWith("image/") && item.mimeType != "text/plain" &&
                    item.mimeType != VaultViewModel.MIME_SECRET
                ) {
                    Button(onClick = {
                        val resolver = context.contentResolver
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, item.title ?: "vault-export")
                            put(MediaStore.Downloads.MIME_TYPE, item.mimeType)
                        }
                        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                        }
                        viewModel.closeOpened()
                    }) { Text("Export") }
                } else {
                    TextButton(onClick = viewModel::closeOpened) { Text("Close") }
                }
            },
            dismissButton = { TextButton(onClick = viewModel::closeOpened) { Text("Close") } },
        )
    }
}

private val DAY = SimpleDateFormat("d MMM", Locale.getDefault())
