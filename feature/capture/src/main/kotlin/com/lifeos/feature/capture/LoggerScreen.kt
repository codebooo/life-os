package com.lifeos.feature.capture

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
                    description = "Counters, ratings, numbers, collections — define a form once, log in one tap forever. " +
                        "Try \"Pizza eaten: counter\" or a Books form with title + author fields.",
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = 96.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(uiState.forms, key = { it.id }) { form ->
                    val fields = DefaultCaptureRepository.parseFields(form.fieldsJson)
                    val count = uiState.counts[form.id] ?: 0
                    Card(onClick = { onEvent(LoggerUiEvent.OpenForm(form)) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(form.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "$count",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (LoggerViewModel.isCounterForm(fields)) {
                                // One-tap counting straight from the tile.
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { onEvent(LoggerUiEvent.Decrement(form.id)) },
                                        enabled = count > 0,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("−") }
                                    androidx.compose.material3.Button(
                                        onClick = { onEvent(LoggerUiEvent.Increment(form.id)) },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("+") }
                                }
                            } else {
                                Text(
                                    fields.joinToString { it.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                        }
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
                .verticalScroll(rememberScrollState())
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
                supportingText = {
                    Text(
                        "Types: counter, number, text, boolean, rating, duration, date — inferred when omitted. " +
                            "A single counter field (\"Pizza eaten: counter\") becomes a tap-to-count tile; " +
                            "multi-field forms (title, author) count their entries automatically.",
                    )
                },
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
                actions = {
                    IconButton(onClick = {
                        uiState.activeForm?.let { onEvent(LoggerUiEvent.DeleteForm(it.id)) }
                        onEvent(LoggerUiEvent.Back)
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete form")
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
                            TypedFieldInput(
                                field = field,
                                value = uiState.entryDraft[field.name].orEmpty(),
                                onValue = { onEvent(LoggerUiEvent.DraftChanged(field.name, it)) },
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
            // Aggregates: sums/averages for numeric fields across all entries.
            item {
                val numericFields = uiState.activeFields.filter { it.type == "number" }
                if (numericFields.isNotEmpty() && uiState.entries.isNotEmpty()) {
                    Card {
                        Column(modifier = Modifier.padding(12.dp)) {
                            numericFields.forEach { field ->
                                val values = uiState.entries.mapNotNull {
                                    DefaultCaptureRepository.parseValues(it.valuesJson)[field.name]
                                        ?.toDoubleOrNull()
                                }
                                if (values.isNotEmpty()) {
                                    Text(
                                        "${field.name}: total %.1f · avg %.1f".format(
                                            values.sum(),
                                            values.average(),
                                        ) + (field.unit?.let { " $it" } ?: ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
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

/** Renders a form field by its declared type — no more everything-is-text. */
@Composable
private fun TypedFieldInput(
    field: com.lifeos.feature.capture.data.LogFieldSpec,
    value: String,
    onValue: (String) -> Unit,
) {
    val label = field.name + (field.unit?.let { " ($it)" } ?: "")
    when (field.type) {
        "number", "counter" -> OutlinedTextField(
            value = value,
            onValueChange = { new -> if (new.all { it.isDigit() || it == '.' || it == '-' }) onValue(new) },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        "boolean" -> Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(field.name, style = MaterialTheme.typography.bodyLarge)
            androidx.compose.material3.Switch(
                checked = value == "yes",
                onCheckedChange = { onValue(if (it) "yes" else "no") },
            )
        }
        "rating" -> Column {
            Text(field.name, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { star ->
                    androidx.compose.material3.FilterChip(
                        selected = value == star.toString(),
                        onClick = { onValue(star.toString()) },
                        label = { Text(star.toString()) },
                    )
                }
            }
        }
        "date" -> OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text("$label (e.g. 2026-07-08)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        else -> OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
