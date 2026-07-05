package com.lifeos.feature.memex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState
import java.text.DateFormat
import java.util.Date

/** Memex timeline (§Module 22): search, clip, annotate; retention is automatic. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemexRoute(viewModel: MemexViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(MemexUiEvent.DismissMessage)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Memex") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.onEvent(MemexUiEvent.QueryChanged(it)) },
                label = { Text("Search your archive") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.clipDraft,
                    onValueChange = { viewModel.onEvent(MemexUiEvent.ClipDraftChanged(it)) },
                    label = { Text("Clip text or a URL") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { viewModel.onEvent(MemexUiEvent.Clip) },
                    enabled = uiState.clipDraft.isNotBlank(),
                ) { Text("Clip") }
            }
            Text(
                "Tip: share any text or link from another app to \"Clip to LifeOS\". " +
                    "Un-annotated clips expire after 12 months; annotated ones are keepers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uiState.items.isEmpty()) {
                EmptyState(
                    title = "Archive is empty",
                    description = "Clip from the share sheet or paste above — everything stays on-device.",
                )
                return@Column
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.items, key = { it.id }) { item ->
                    Card {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                item.body,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.annotation.isNotBlank()) {
                                Text(
                                    "✎ ${item.annotation}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            "${item.source.lowercase()} · " +
                                                DateFormat.getDateInstance(DateFormat.MEDIUM)
                                                    .format(Date(item.capturedAt)),
                                        )
                                    },
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.onEvent(MemexUiEvent.StartAnnotate(item)) }) {
                                    Icon(Icons.Filled.EditNote, contentDescription = "Annotate")
                                }
                                IconButton(onClick = { viewModel.onEvent(MemexUiEvent.Delete(item.id)) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.annotating?.let {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(MemexUiEvent.CancelAnnotation) },
            title = { Text("Annotate") },
            text = {
                OutlinedTextField(
                    value = uiState.annotationDraft,
                    onValueChange = { viewModel.onEvent(MemexUiEvent.AnnotationChanged(it)) },
                    label = { Text("Your note (keeps the item forever)") },
                    minLines = 2,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(MemexUiEvent.SaveAnnotation) }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(MemexUiEvent.CancelAnnotation) }) { Text("Cancel") }
            },
        )
    }
}
