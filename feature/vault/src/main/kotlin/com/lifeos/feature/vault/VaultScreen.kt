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
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.lifeos.core.database.vault.VaultBlobEntity
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.feature.vault.data.PasswordEntry
import com.lifeos.feature.vault.data.VaultMime
import com.mikepenz.markdown.m3.Markdown
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Vault (§Module Vault): the hidden, biometrically locked store. Texts and
 * passwords open dedicated screens; images open a zoomable gallery; passwords
 * mirror Proton Pass (username, generator, 2FA TOTP, website, note, custom
 * fields, attachments). Everything is Tink-encrypted at rest.
 */
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

    if (unlocked) {
        VaultContent(viewModel)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(if (authFailed) "Locked. Reopen the vault to retry." else "Unlocking…")
            }
        }
    }
}

@Composable
private fun VaultContent(viewModel: VaultViewModel) {
    val mode by viewModel.mode.collectAsState()
    when (val m = mode) {
        is VaultMode.List -> VaultListScreen(viewModel)
        is VaultMode.PasswordEditor -> PasswordEditorScreen(viewModel, m.ref, m.entry)
        is VaultMode.PasswordView -> PasswordViewScreen(viewModel, m.ref, m.entry)
        is VaultMode.TextEditor -> TextEditorScreen(viewModel, m.ref, m.title, m.body)
        is VaultMode.TextView -> TextViewScreen(viewModel, m.ref, m.title, m.body)
        is VaultMode.ImageView -> ImageGalleryScreen(viewModel, m.title, m.bytes)
        is VaultMode.FileInfo -> FileInfoScreen(viewModel, m.item, m.bytes)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultListScreen(viewModel: VaultViewModel) {
    val items by viewModel.items.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val context = LocalContext.current
    var sort by remember { mutableStateOf("Newest") }
    var filter by remember { mutableStateOf("All") }
    var addMenu by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = uri.lastPathSegment?.substringAfterLast('/')?.take(60) ?: "file"
        resolver.openInputStream(uri)?.use { input -> viewModel.addFile(input.readBytes(), name, mime) }
    }

    val shown = items
        .filter {
            when (filter) {
                "Media" -> it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")
                "Texts" -> it.mimeType == VaultMime.SECURE_TEXT || it.mimeType == "text/plain"
                "Passwords" -> it.mimeType == VaultMime.PASSWORD
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
                title = { Text("Vault") },
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
                        text = { Text("Login / password") },
                        leadingIcon = { Icon(Icons.Filled.Key, null) },
                        onClick = { viewModel.newPassword(); addMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Secure text") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                        onClick = { viewModel.newText(); addMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Import file") },
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
                                    Icon(iconFor(item.mimeType), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    IconButton(onClick = { viewModel.delete(item) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                    }
                                }
                                Text(item.title ?: "Untitled", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(
                                    "${labelFor(item.mimeType)} · ${DAY.format(Date(item.createdAt))}",
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
}

private fun iconFor(mime: String) = when {
    mime == VaultMime.PASSWORD -> Icons.Filled.Key
    mime.startsWith("image/") -> Icons.Filled.Image
    mime.startsWith("video/") -> Icons.Filled.Movie
    else -> Icons.AutoMirrored.Filled.Notes
}

private fun labelFor(mime: String) = when {
    mime == VaultMime.PASSWORD -> "login"
    mime == VaultMime.SECURE_TEXT || mime == "text/plain" -> "text"
    else -> mime.substringBefore('/')
}

private val DAY = SimpleDateFormat("d MMM", Locale.getDefault())
