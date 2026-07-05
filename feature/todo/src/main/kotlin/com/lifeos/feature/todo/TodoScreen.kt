package com.lifeos.feature.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.database.capture.TaskEntity
import com.lifeos.core.designsystem.component.EmptyState

@Composable
fun TodoRoute(viewModel: TodoViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TodoScreen(uiState = uiState, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodoScreen(uiState: TodoUiState, onEvent: (TodoUiEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) {
            item {
                FilterChip(
                    selected = uiState.activeListId == null,
                    onClick = { onEvent(TodoUiEvent.SelectList(null)) },
                    label = { Text("Inbox") },
                )
            }
            items(uiState.lists, key = { it.id }) { list ->
                FilterChip(
                    selected = uiState.activeListId == list.id,
                    onClick = { onEvent(TodoUiEvent.SelectList(list.id)) },
                    label = { Text(list.name) },
                    trailingIcon = if (uiState.activeListId == list.id) {
                        {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete list",
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .clickable { onEvent(TodoUiEvent.DeleteList(list.id)) },
                            )
                        }
                    } else {
                        null
                    },
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = { onEvent(TodoUiEvent.ToggleNewList) },
                    label = { Text("+ List") },
                )
            }
        }

        if (uiState.showNewList) {
            OutlinedTextField(
                value = uiState.newListName,
                onValueChange = { onEvent(TodoUiEvent.NewListNameChanged(it)) },
                label = { Text("List name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onEvent(TodoUiEvent.AddList) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        OutlinedTextField(
            value = uiState.newTaskTitle,
            onValueChange = { onEvent(TodoUiEvent.NewTaskTitleChanged(it)) },
            placeholder = {
                Text(
                    if (uiState.addingSubtaskOf != null) "Add subtask…" else "Add a task…",
                )
            },
            trailingIcon = {
                IconButton(onClick = { onEvent(TodoUiEvent.AddTask) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEvent(TodoUiEvent.AddTask) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (uiState.nodes.isEmpty()) {
            EmptyState(
                title = "All clear",
                description = "Tasks from quick capture land in the Inbox.",
            )
        } else {
            LazyColumn {
                uiState.nodes.forEach { node ->
                    item(key = node.task.id) {
                        TaskRow(
                            task = node.task,
                            indent = 0.dp,
                            onEvent = onEvent,
                            subtaskTarget = uiState.addingSubtaskOf == node.task.id,
                        )
                    }
                    items(node.children, key = { it.id }) { child ->
                        TaskRow(task = child, indent = 32.dp, onEvent = onEvent, subtaskTarget = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    indent: androidx.compose.ui.unit.Dp,
    onEvent: (TodoUiEvent) -> Unit,
    subtaskTarget: Boolean,
) {
    ListItem(
        headlineContent = {
            Text(
                task.title,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = if (subtaskTarget) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified,
            )
        },
        leadingContent = {
            Checkbox(
                checked = task.done,
                onCheckedChange = { onEvent(TodoUiEvent.SetDone(task.id, it)) },
            )
        },
        trailingContent = {
            Row {
                if (indent == 0.dp) {
                    IconButton(onClick = {
                        onEvent(
                            if (subtaskTarget) TodoUiEvent.StartSubtask(null)
                            else TodoUiEvent.StartSubtask(task.id),
                        )
                    }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add subtask")
                    }
                }
                IconButton(onClick = { onEvent(TodoUiEvent.DeleteTask(task.id)) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        },
        modifier = Modifier.padding(start = indent),
    )
}

