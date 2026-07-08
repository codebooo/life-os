package com.lifeos.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assistant") },
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
            AiInputBar(
                value = uiState.input,
                onValueChange = { onEvent(ChatUiEvent.InputChanged(it)) },
                onSend = { onEvent(ChatUiEvent.Send) },
                busy = uiState.streaming,
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
