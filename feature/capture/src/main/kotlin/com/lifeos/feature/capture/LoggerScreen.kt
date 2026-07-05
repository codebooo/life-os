package com.lifeos.feature.capture

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.core.designsystem.component.SectionHeader
import com.lifeos.feature.capture.data.DefaultCaptureRepository
import java.text.DateFormat
import java.util.Date

@Composable
fun LoggerRoute(viewModel: LoggerViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LoggerScreen(uiState = uiState, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoggerScreen(uiState: LoggerUiState, onEvent: (LoggerUiEvent) -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(LoggerUiEvent.DismissError)
        }
    }

    when (uiState.mode) {
        LoggerMode.FORMS -> FormListScreen(uiState, onEvent, snackbarHostState)
        LoggerMode.EDITOR -> FormEditorScreen(uiState, onEvent, snackbarHostState)
        LoggerMode.ENTRIES -> EntriesScreen(uiState, onEvent, snackbarHostState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormListScreen(
    uiState: LoggerUiState,
    onEvent: (LoggerUiEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Logger") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(LoggerUiEvent.NewForm) }) {
                Icon(Icons.Filled.Add, contentDescription = "New form")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (uiState.forms.isEmpty()) {
            Column(modifier = Modifier.padding(innerPadding)) {
                EmptyState(
                    title = "Log anything, with a little structure",
                    description = "Mood, caffeine, sleep, symptoms — define a form once, log in one tap forever.",
                )
            }
        } else {
            LazyColumn(
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(uiState.forms, key = { it.id }) { form ->
                    Surface(onClick = { onEvent(LoggerUiEvent.OpenForm(form)) }) {
                        ListItem(
                            headlineContent = { Text(form.name) },
                            supportingContent = {
                                Text(
                                    DefaultCaptureRepository.parseFields(form.fieldsJson)
                                        .joinToString { "${it.name} (${it.type})" },
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onEvent(LoggerUiEvent.DeleteForm(form.id)) }) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormEditorScreen(
    uiState: LoggerUiState,
    onEvent: (LoggerUiEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New log form") },
                navigationIcon = {
                    IconButton(onClick = { onEvent(LoggerUiEvent.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(LoggerUiEvent.SaveForm) }) {
                        Icon(Icons.Filled.Save, contentDescription = "Save")
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
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = uiState.editorName,
                onValueChange = { onEvent(LoggerUiEvent.EditorNameChanged(it)) },
                label = { Text("Form name") },
                placeholder = { Text("Sleep") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.editorFieldLines,
                onValueChange = { onEvent(LoggerUiEvent.EditorFieldsChanged(it)) },
                label = { Text("Fields — one per line") },
                placeholder = { Text("hours: number\nquality: rating\nnotes") },
                supportingText = { Text("Types: number, text, boolean, rating, duration, date — inferred when omitted") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntriesScreen(
    uiState: LoggerUiState,
    onEvent: (LoggerUiEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.activeForm?.name ?: "Log") },
                navigationIcon = {
                    IconButton(onClick = { onEvent(LoggerUiEvent.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = 24.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("New entry", style = MaterialTheme.typography.titleMedium)
                        uiState.activeFields.forEach { field ->
                            OutlinedTextField(
                                value = uiState.entryDraft[field.name].orEmpty(),
                                onValueChange = { onEvent(LoggerUiEvent.DraftChanged(field.name, it)) },
                                label = { Text(field.name + (field.unit?.let { " ($it)" } ?: "")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            FloatingActionButton(onClick = { onEvent(LoggerUiEvent.AddEntry) }) {
                                Icon(Icons.Filled.Add, contentDescription = "Add entry")
                            }
                        }
                    }
                }
            }
            item { SectionHeader(title = "History (${uiState.entries.size})") }
            items(uiState.entries, key = { it.id }) { entry ->
                Card {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(entry.at)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            DefaultCaptureRepository.parseValues(entry.valuesJson)
                                .entries.joinToString { "${it.key}: ${it.value}" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
