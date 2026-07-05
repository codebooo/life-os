package com.lifeos.feature.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState
import java.text.DateFormat
import java.util.Date

@Composable
fun RemindersRoute(viewModel: RemindersViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RemindersScreen(uiState = uiState, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemindersScreen(uiState: RemindersUiState, onEvent: (RemindersUiEvent) -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(RemindersUiEvent.DismissError)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(RemindersUiEvent.ToggleEditor) }) {
                Icon(Icons.Filled.Add, contentDescription = "New reminder")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (uiState.reminders.isEmpty()) {
            Column(modifier = Modifier.padding(innerPadding)) {
                EmptyState(
                    title = "No reminders",
                    description = "Exact alarms that fire over the lockscreen and survive reboots.",
                )
            }
        } else {
            LazyColumn(contentPadding = innerPadding, modifier = Modifier.fillMaxSize()) {
                items(uiState.reminders, key = { it.id }) { reminder ->
                    ListItem(
                        headlineContent = { Text(reminder.title) },
                        supportingContent = {
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                    .format(Date(reminder.at)) +
                                    if (reminder.recurrence != "NONE") " · ${reminder.recurrence.lowercase()}" else "" +
                                        if (reminder.firedAt != null) " · fired" else "",
                            )
                        },
                        leadingContent = {
                            Switch(
                                checked = reminder.enabled,
                                onCheckedChange = {
                                    onEvent(RemindersUiEvent.SetEnabled(reminder.id, it))
                                },
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onEvent(RemindersUiEvent.Delete(reminder.id)) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        },
                    )
                }
            }
        }
    }

    if (uiState.showEditor) {
        ReminderEditorSheet(uiState = uiState, onEvent = onEvent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditorSheet(uiState: RemindersUiState, onEvent: (RemindersUiEvent) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onEvent(RemindersUiEvent.ToggleEditor) }) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("New reminder", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = uiState.editorTitle,
                onValueChange = { onEvent(RemindersUiEvent.EditorTitleChanged(it)) },
                label = { Text("What?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.editorWhen,
                onValueChange = { onEvent(RemindersUiEvent.EditorWhenChanged(it)) },
                label = { Text("When?") },
                placeholder = { Text("in 30 minutes · tomorrow at 9 · friday 18:30") },
                supportingText = {
                    Text(
                        uiState.editorParsedAt?.let {
                            "Will fire " + DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM,
                                DateFormat.SHORT,
                            ).format(Date(it))
                        } ?: "Type a time in plain language",
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("NONE" to "Once", "DAILY" to "Daily", "WEEKLY" to "Weekly").forEach { (value, label) ->
                    FilterChip(
                        selected = uiState.editorRecurrence == value,
                        onClick = { onEvent(RemindersUiEvent.EditorRecurrenceChanged(value)) },
                        label = { Text(label) },
                    )
                }
            }
            Button(
                onClick = { onEvent(RemindersUiEvent.Save) },
                enabled = uiState.editorTitle.isNotBlank() && uiState.editorParsedAt != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Set reminder")
            }
        }
    }
}
