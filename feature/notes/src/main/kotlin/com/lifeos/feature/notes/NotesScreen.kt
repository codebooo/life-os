package com.lifeos.feature.notes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.core.designsystem.component.SectionHeader
import com.mikepenz.markdown.m3.Markdown
import java.text.DateFormat
import java.util.Date

@Composable
fun NotesRoute(viewModel: NotesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NotesScreen(uiState = uiState, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotesScreen(uiState: NotesUiState, onEvent: (NotesUiEvent) -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(NotesUiEvent.DismissError)
        }
    }

    when (uiState.mode) {
        NotesScreenMode.LIST -> NotesListScreen(uiState, onEvent, snackbarHostState)
        NotesScreenMode.EDITOR -> NoteEditorScreen(uiState.editor, onEvent, snackbarHostState)
        NotesScreenMode.ASK -> AskMyNotesScreen(uiState.ask, onEvent, snackbarHostState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesListScreen(
    uiState: NotesUiState,
    onEvent: (NotesUiEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notes") },
                actions = {
                    IconButton(onClick = { onEvent(NotesUiEvent.OpenAsk) }) {
                        Icon(Icons.Filled.Psychology, contentDescription = "Ask my notes")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(NotesUiEvent.NewNote) }) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { onEvent(NotesUiEvent.QueryChanged(it)) },
                placeholder = { Text("Search notes") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (uiState.notes.isEmpty()) {
                EmptyState(
                    title = "No notes yet",
                    description = "Plain Markdown files you own forever — readable in any editor, on any OS.",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(uiState.notes, key = { it.id }) { note ->
                        Surface(onClick = { onEvent(NotesUiEvent.OpenNote(note.id)) }) {
                            ListItem(
                                headlineContent = {
                                    Text(note.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(
                                        DateFormat.getDateTimeInstance(
                                            DateFormat.MEDIUM,
                                            DateFormat.SHORT,
                                        ).format(Date(note.updatedAt)),
                                    )
                                },
                                leadingContent = if (note.bodyVaultRef != null) {
                                    { Icon(Icons.Filled.Lock, contentDescription = "In vault") }
                                } else {
                                    null
                                },
                                trailingContent = {
                                    IconButton(onClick = { onEvent(NotesUiEvent.DeleteNote(note.id)) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorScreen(
    editor: EditorState,
    onEvent: (NotesUiEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    // Local selection-aware value so the toolbar can wrap the selected text.
    var body by remember(editor.noteId, editor.editing) {
        mutableStateOf(TextFieldValue(editor.body))
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editor.noteId == null) "New note" else editor.title.ifBlank { "Note" }) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(NotesUiEvent.BackToList) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(NotesUiEvent.ToggleSensitive) }) {
                        Icon(
                            if (editor.sensitive) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = if (editor.sensitive) "Stored in vault" else "Store in vault",
                            tint = if (editor.sensitive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (editor.editing) {
                        IconButton(onClick = { onEvent(NotesUiEvent.SaveNote) }) {
                            Icon(Icons.Filled.Save, contentDescription = "Save")
                        }
                    } else {
                        // View mode: toggle rendered <-> raw, and a pen to edit.
                        IconButton(onClick = { onEvent(NotesUiEvent.TogglePreview) }) {
                            Icon(
                                if (editor.preview) Icons.Filled.Code else Icons.Filled.Preview,
                                contentDescription = if (editor.preview) "Show raw" else "Show rendered",
                            )
                        }
                        IconButton(onClick = { onEvent(NotesUiEvent.EnterEdit) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (editor.editing) {
                OutlinedTextField(
                    value = editor.title,
                    onValueChange = { onEvent(NotesUiEvent.TitleChanged(it)) },
                    placeholder = { Text("Title") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                MarkdownToolbar(
                    onApply = { transform ->
                        val next = transform(body)
                        body = next
                        onEvent(NotesUiEvent.BodyChanged(next.text))
                    },
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = {
                        body = it
                        onEvent(NotesUiEvent.BodyChanged(it.text))
                    },
                    placeholder = { Text("Write in Markdown — [[wiki links]] connect notes") },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else if (editor.preview) {
                Markdown(
                    content = editor.body.ifBlank { "_Empty note_" },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                )
            } else {
                Text(
                    editor.body.ifBlank { "Empty note" },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                )
            }
            if (editor.backlinks.isNotEmpty()) {
                SectionHeader(title = "Linked from")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    editor.backlinks.take(4).forEach { backlink ->
                        AssistChip(
                            onClick = { onEvent(NotesUiEvent.OpenNote(backlink.id)) },
                            label = { Text(backlink.title, maxLines = 1) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Word-processor-style formatting bar (§Module 21): each button wraps the
 * current selection (or inserts a marker at the caret) with the Markdown for
 * that style — bold, italic, strikethrough, headings, list, quote, code, link.
 */
@Composable
private fun MarkdownToolbar(onApply: ((TextFieldValue) -> TextFieldValue) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ToolbarButton("B") { onApply { wrapSelection(it, "**") } }
        ToolbarButton("I") { onApply { wrapSelection(it, "*") } }
        ToolbarButton("S") { onApply { wrapSelection(it, "~~") } }
        ToolbarButton("H1") { onApply { linePrefix(it, "# ") } }
        ToolbarButton("H2") { onApply { linePrefix(it, "## ") } }
        ToolbarButton("List") { onApply { linePrefix(it, "- ") } }
        ToolbarButton("Quote") { onApply { linePrefix(it, "> ") } }
        ToolbarButton("Code") { onApply { wrapSelection(it, "`") } }
        ToolbarButton("Link") { onApply { linkSelection(it) } }
    }
}

@Composable
private fun ToolbarButton(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp)) {
        Text(label)
    }
}

/** Wraps the selected range with [marker] on both sides (or inserts a pair). */
private fun wrapSelection(value: TextFieldValue, marker: String): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val selected = value.text.substring(start, end)
    val newText = value.text.substring(0, start) + marker + selected + marker + value.text.substring(end)
    val caret = if (selected.isEmpty()) start + marker.length else end + marker.length * 2
    return value.copy(text = newText, selection = TextRange(caret))
}

/** Prepends [prefix] to the start of the line the caret sits on. */
private fun linePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
    val caret = value.selection.min
    val lineStart = value.text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val newText = value.text.substring(0, lineStart) + prefix + value.text.substring(lineStart)
    return value.copy(text = newText, selection = TextRange(caret + prefix.length))
}

/** Wraps the selection as a Markdown link `[text](url)` with the caret in url. */
private fun linkSelection(value: TextFieldValue): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val selected = value.text.substring(start, end).ifEmpty { "text" }
    val inserted = "[$selected](url)"
    val newText = value.text.substring(0, start) + inserted + value.text.substring(end)
    val urlStart = start + inserted.length - 4
    return value.copy(text = newText, selection = TextRange(urlStart, urlStart + 3))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskMyNotesScreen(
    ask: AskState,
    onEvent: (NotesUiEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask my notes") },
                navigationIcon = {
                    IconButton(onClick = { onEvent(NotesUiEvent.BackToList) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Answers are grounded in your notes and never leave this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = ask.question,
                onValueChange = { onEvent(NotesUiEvent.AskQuestionChanged(it)) },
                placeholder = { Text("What did I write about…?") },
                trailingIcon = {
                    if (ask.thinking) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { onEvent(NotesUiEvent.Ask) }) {
                            Icon(Icons.Filled.Psychology, contentDescription = "Ask")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ask.answer?.let { answer ->
                    Card { Markdown(content = answer, modifier = Modifier.padding(16.dp)) }
                }
                if (ask.citations.isNotEmpty()) {
                    SectionHeader(title = "Sources")
                    ask.citations.forEachIndexed { index, citation ->
                        Card(onClick = { onEvent(NotesUiEvent.OpenNote(citation.sourceId)) }) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "[${index + 1}] ${citation.sourceTitle}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    citation.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
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
