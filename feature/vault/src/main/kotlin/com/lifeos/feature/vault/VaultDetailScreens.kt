package com.lifeos.feature.vault

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lifeos.core.database.vault.VaultBlobEntity
import com.lifeos.feature.vault.data.CustomField
import com.lifeos.feature.vault.data.PasswordEntry
import com.lifeos.feature.vault.data.PasswordGenerator
import com.lifeos.feature.vault.data.TotpGenerator
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun copyToClipboard(context: android.content.Context, label: String, text: String) {
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PasswordEditorScreen(viewModel: VaultViewModel, ref: String?, initial: PasswordEntry) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial.title) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    var totp by remember { mutableStateOf(initial.totpSecret) }
    var website by remember { mutableStateOf(initial.website) }
    var note by remember { mutableStateOf(initial.note) }
    val fields = remember { mutableStateListOf<CustomField>().apply { addAll(initial.customFields) } }
    val attachments = remember { mutableStateListOf(*initial.attachments.toTypedArray()) }
    var showGenerator by remember { mutableStateOf(false) }

    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = uri.lastPathSegment?.substringAfterLast('/')?.take(60) ?: "file"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        // Store the attachment as its own encrypted blob, then reference it.
        scope.launch {
            viewModel.putAttachment(bytes, name, mime)?.let { attachments.add(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ref == null) "New login" else "Edit login") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.backToList() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.savePassword(
                            ref,
                            PasswordEntry(title, username, password, totp, website, note, fields.toList(), attachments.toList()),
                        )
                    }) { Icon(Icons.Filled.Save, contentDescription = "Save") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it }, label = { Text("Username or email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                password,
                { password = it },
                label = { Text("Password") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showGenerator = true }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Generate")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(totp, { totp = it }, label = { Text("2FA secret key (TOTP)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(website, { website = it }, label = { Text("Website") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            fields.forEachIndexed { index, field ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        field.label,
                        { fields[index] = field.copy(label = it) },
                        label = { Text("Field") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        field.value,
                        { fields[index] = field.copy(value = it) },
                        label = { Text("Value") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { fields.removeAt(index) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove field")
                    }
                }
            }
            OutlinedButton(onClick = { fields.add(CustomField("", "")) }) {
                Icon(Icons.Filled.Add, contentDescription = null); Text("  Add field")
            }

            Text("Attachments", style = MaterialTheme.typography.titleSmall)
            attachments.forEach { attachment ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(attachment.name, modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { attachments.remove(attachment) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove")
                    }
                }
            }
            OutlinedButton(onClick = { attachLauncher.launch("*/*") }) {
                Icon(Icons.Filled.Add, contentDescription = null); Text("  Attach a file")
            }
        }
    }

    if (showGenerator) {
        PasswordGeneratorDialog(
            onDismiss = { showGenerator = false },
            onUse = { password = it; showGenerator = false },
        )
    }
}

@Composable
private fun PasswordGeneratorDialog(onDismiss: () -> Unit, onUse: (String) -> Unit) {
    var length by remember { mutableStateOf(20f) }
    var symbols by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var generated by remember { mutableStateOf(PasswordGenerator.generate(20)) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(generated, style = MaterialTheme.typography.titleMedium)
                Text("Length: ${length.toInt()}")
                androidx.compose.material3.Slider(value = length, onValueChange = { length = it }, valueRange = 8f..48f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(checked = digits, onCheckedChange = { digits = it }); Text("  Digits")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(checked = symbols, onCheckedChange = { symbols = it }); Text("  Symbols")
                }
                OutlinedButton(onClick = {
                    generated = PasswordGenerator.generate(length.toInt(), useDigits = digits, useSymbols = symbols)
                }) { Text("Regenerate") }
            }
        },
        confirmButton = { Button(onClick = { onUse(generated) }) { Text("Use") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PasswordViewScreen(viewModel: VaultViewModel, ref: String, entry: PasswordEntry) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revealPassword by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry.title.ifBlank { "Login" }) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.backToList() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.editPassword(ref, entry) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { viewModel.deleteByRef(ref) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (entry.username.isNotBlank()) {
                CopyRow("Username", entry.username, context)
            }
            if (entry.password.isNotBlank()) {
                Card {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Password", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (revealPassword) entry.password else "••••••••••",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { revealPassword = !revealPassword }) {
                                Icon(if (revealPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Reveal")
                            }
                            IconButton(onClick = { copyToClipboard(context, "password", entry.password) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                            }
                        }
                    }
                }
            }
            if (entry.totpSecret.isNotBlank()) {
                TotpCard(entry.totpSecret, context)
            }
            if (entry.website.isNotBlank()) CopyRow("Website", entry.website, context)
            if (entry.note.isNotBlank()) {
                Card { Column(modifier = Modifier.padding(14.dp)) {
                    Text("Note", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(entry.note)
                } }
            }
            entry.customFields.forEach { CopyRow(it.label.ifBlank { "Field" }, it.value, context) }
            if (entry.attachments.isNotEmpty()) {
                Text("Attachments", style = MaterialTheme.typography.titleSmall)
                entry.attachments.forEach { attachment ->
                    Card(onClick = {
                        scope.launch {
                            viewModel.openAttachment(attachment.ref)?.let { bytes ->
                                exportToDownloads(context, attachment.name, attachment.mimeType, bytes)
                            }
                        }
                    }) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Text("  ${attachment.name} (${attachment.sizeBytes / 1024} KB)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotpCard(secret: String, context: android.content.Context) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1_000) }
    }
    val code = TotpGenerator.code(secret, now)
    val remaining = TotpGenerator.secondsRemaining(now)
    Card {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("2FA code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    code?.chunked(3)?.joinToString(" ") ?: "invalid secret",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Text("${remaining}s", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (code != null) {
                    IconButton(onClick = { copyToClipboard(context, "2fa", code) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyRow(label: String, value: String, context: android.content.Context) {
    Card(onClick = { copyToClipboard(context, label, value) }) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextEditorScreen(viewModel: VaultViewModel, ref: String?, initialTitle: String, initialBody: String) {
    var title by remember { mutableStateOf(initialTitle) }
    var body by remember { mutableStateOf(initialBody) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ref == null) "New text" else "Edit text") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.backToList() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveText(ref, title, body) }) {
                        Icon(Icons.Filled.Save, contentDescription = "Save")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                body,
                { body = it },
                label = { Text("Markdown supported") },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextViewScreen(viewModel: VaultViewModel, ref: String, title: String, body: String) {
    var rendered by remember { mutableStateOf(true) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.backToList() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    androidx.compose.material3.TextButton(onClick = { rendered = !rendered }) {
                        Text(if (rendered) "Raw" else "Rendered")
                    }
                    IconButton(onClick = { viewModel.editText(ref, title, body) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { viewModel.deleteByRef(ref) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            if (rendered) Markdown(content = body.ifBlank { "_Empty_" }) else Text(body)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageGalleryScreen(viewModel: VaultViewModel, title: String, bytes: ByteArray) {
    val context = LocalContext.current
    // Downsample to screen size so large photos don't OOM (the old crash).
    val bitmap = remember(bytes) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (opts.outWidth / sample > 2048 || opts.outHeight / sample > 2048) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.backToList() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        exportToDownloads(context, title.ifBlank { "image" }, "image/jpeg", bytes)
                    }) { Icon(Icons.Filled.Download, contentDescription = "Export") }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding).fillMaxSize().pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize().graphicsLayer(
                        scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY,
                    ),
                )
            } else {
                Text("Couldn't decode this image.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileInfoScreen(viewModel: VaultViewModel, item: VaultBlobEntity, bytes: ByteArray) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.title ?: "File") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.backToList() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.deleteByRef(item.ref) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${item.mimeType} · ${bytes.size / 1024} KB")
            Button(onClick = {
                exportToDownloads(context, item.title ?: "vault-export", item.mimeType, bytes)
            }) { Text("Export to Downloads") }
        }
    }
}

private fun exportToDownloads(context: android.content.Context, name: String, mime: String, bytes: ByteArray) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, name)
        put(MediaStore.Downloads.MIME_TYPE, mime)
    }
    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.let { uri ->
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
    }
}
