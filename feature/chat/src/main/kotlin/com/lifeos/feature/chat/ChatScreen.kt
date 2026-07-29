package com.lifeos.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.database.chat.AiMessageEntity
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.core.ui.component.AiInputBar
import com.lifeos.feature.chat.settings.AiSettingsSheet

/** Entry point for the Assistant tab (§Module 18). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoute(viewModel: ChatViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(uiState = uiState, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreen(
    uiState: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(ChatUiEvent.DismissError)
        }
    }

    // Gallery images are copied into app storage so the model can read them
    // after the picker's temporary permission is gone.
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            copyToChatImages(context, uri)?.let { path -> onEvent(ChatUiEvent.ImageAttached(path)) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jarvis") },
                actions = {
                    uiState.activeEngine?.let { engine ->
                        AssistChip(onClick = {}, label = { Text(engine.label) })
                    }
                    IconButton(onClick = { onEvent(ChatUiEvent.ToggleContext) }) {
                        Icon(
                            Icons.Filled.AttachFile,
                            contentDescription = "AI context",
                            tint = if (uiState.contextText.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = { onEvent(ChatUiEvent.NewConversation) }) {
                        Icon(Icons.Filled.Add, contentDescription = "New conversation")
                    }
                    IconButton(onClick = { onEvent(ChatUiEvent.ToggleConversations) }) {
                        Icon(Icons.Filled.History, contentDescription = "Conversations")
                    }
                    IconButton(onClick = { onEvent(ChatUiEvent.ToggleSettings) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "AI settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.messages.isEmpty()) {
                    EmptyState(
                        title = "Ask anything",
                        description = "Answers come from your NAS or stay fully on-device — never a third-party cloud.",
                    )
                } else {
                    MessageList(messages = uiState.messages, streaming = uiState.streaming)
                }
            }
            if (uiState.debugEnabled) {
                JarvisDebugPanel(uiState.debugLog)
            }
            // Attached images sit above the bar so it stays obvious what will be sent.
            if (uiState.pendingImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.pendingImages.forEach { path ->
                        Box {
                            ThumbImage(path = path, size = 72.dp, description = "Attached image")
                            IconButton(
                                onClick = { onEvent(ChatUiEvent.ImageRemoved(path)) },
                                modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove image",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
            AiInputBar(
                value = uiState.input,
                onValueChange = { onEvent(ChatUiEvent.InputChanged(it)) },
                onSend = { onEvent(ChatUiEvent.Send) },
                busy = uiState.streaming,
                onAttachImage = { imagePicker.launch("image/*") },
                hasAttachments = uiState.pendingImages.isNotEmpty(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }

    if (uiState.showConversations) {
        ModalBottomSheet(onDismissRequest = { onEvent(ChatUiEvent.ToggleConversations) }) {
            if (uiState.conversations.isEmpty()) {
                Text(
                    "No conversations yet",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            LazyColumn {
                items(uiState.conversations, key = { it.id }) { conversation ->
                    Surface(
                        onClick = { onEvent(ChatUiEvent.SelectConversation(conversation.id)) },
                        tonalElevation = if (conversation.id == uiState.activeConversationId) 4.dp else 0.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                IconButton(onClick = { onEvent(ChatUiEvent.DeleteConversation(conversation.id)) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (uiState.showSettings) {
        AiSettingsSheet(onDismiss = { onEvent(ChatUiEvent.ToggleSettings) })
    }

    if (uiState.showContext) {
        ContextSheet(uiState, onEvent)
    }
}

/**
 * Developer Options "Jarvis Debugging" panel: the last turn's snapshot, raw
 * model output, tool calls and errors, with a one-tap copy for bug reports.
 */
@Composable
private fun JarvisDebugPanel(log: List<String>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                    Text("Jarvis debug (${log.size})", style = MaterialTheme.typography.labelLarge)
                }
                androidx.compose.material3.TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("jarvis-debug", log.joinToString("\n")),
                        )
                    },
                ) { Text("Copy debug data") }
            }
            if (expanded) {
                Text(
                    log.joinToString("\n").ifBlank { "No debug data yet — send a message." },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

/** Manual "AI Context" (§Module 9): pasted notes or attached text files ride along with every prompt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextSheet(uiState: ChatUiState, onEvent: (ChatUiEvent) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }.orEmpty()
            onEvent(ChatUiEvent.ContextFileAttached(name, content))
        }
    }

    ModalBottomSheet(onDismissRequest = { onEvent(ChatUiEvent.ToggleContext) }) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("AI context", style = MaterialTheme.typography.titleLarge)
            Text(
                "Anything here is attached to every prompt you send (visibly, as a [Context] block). " +
                    "Paste text or attach files — it never leaves your devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.OutlinedTextField(
                value = uiState.contextText,
                onValueChange = { onEvent(ChatUiEvent.ContextChanged(it)) },
                label = { Text("Context") },
                minLines = 5,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        filePicker.launch(
                            arrayOf("text/*", "application/json", "application/xml", "text/markdown"),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Attach text file") }
                androidx.compose.material3.Button(
                    onClick = { onEvent(ChatUiEvent.ContextChanged("")) },
                    enabled = uiState.contextText.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun MessageList(messages: List<AiMessageEntity>, streaming: Boolean) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(message)
        }
    }
}

@Composable
private fun MessageBubble(message: AiMessageEntity) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    color = if (isUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 20.dp,
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                val images = remember(message.imagePaths) {
                    message.imagePaths.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                }
                if (images.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        images.forEach { path ->
                            ThumbImage(path = path, size = 140.dp)
                        }
                    }
                }
                val (thoughts, answer) = remember(message.content) { splitThinking(message.content) }
                if (thoughts != null) {
                    ThoughtChain(thoughts)
                }
                Text(
                    text = answer.ifBlank { if (thoughts != null) "…" else "…" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

/** Collapsible reasoning block parsed from a &lt;think&gt;…&lt;/think&gt; span. */
@Composable
private fun ThoughtChain(thoughts: String) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
        ) {
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (expanded) "Hide thinking" else "Show thinking",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Text(
                thoughts.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp, start = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(8.dp),
            )
        }
    }
}

/** Splits "&lt;think&gt;…&lt;/think&gt; answer" into (thoughts?, answer); handles a still-streaming, unclosed tag. */
private fun splitThinking(content: String): Pair<String?, String> {
    val start = content.indexOf("<think>")
    if (start == -1) return null to content
    val afterOpen = content.substring(start + 7)
    val close = afterOpen.indexOf("</think>")
    return if (close == -1) {
        // Still streaming the thought.
        afterOpen to ""
    } else {
        afterOpen.substring(0, close) to afterOpen.substring(close + 8).trim()
    }
}

/**
 * Copies a picked image into the app's chat-images folder and returns its path.
 * The picker only grants a short-lived permission on the Uri, and inference runs
 * later on another thread, so the bytes have to be ours first.
 */
private fun copyToChatImages(context: android.content.Context, uri: android.net.Uri): String? = runCatching {
    val dir = java.io.File(context.filesDir, "chat-images").apply { mkdirs() }
    val target = java.io.File(dir, "img-${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    target.absolutePath
}.getOrNull()

/** Bounded bitmap thumbnail, decoded once per path (no image library needed). */
@Composable
private fun ThumbImage(path: String, size: androidx.compose.ui.unit.Dp, description: String? = null) {
    val bitmap = remember(path) {
        runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            val options = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = 1
                while (longest / inSampleSize > 512) inSampleSize *= 2
            }
            android.graphics.BitmapFactory.decodeFile(path, options)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap == null) return
    Image(
        bitmap = bitmap,
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)),
    )
}
