package com.lifeos.feature.agentic

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState

/** NL macro authoring + dry-run preview + run (§Module 12, [src 41]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacrosRoute(viewModel: MacrosViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(MacrosUiEvent.DismissMessage)
        }
    }
    LaunchedEffect(Unit) { viewModel.onEvent(MacrosUiEvent.RefreshServiceState) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Macros") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!uiState.serviceEnabled) {
                Card {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Macros need the \"LifeOS Macros\" accessibility service — enable it once, " +
                                "then every run still requires your tap here.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) { Text("Open accessibility settings") }
                    }
                }
            }
            OutlinedTextField(
                value = uiState.nlPrompt,
                onValueChange = { viewModel.onEvent(MacrosUiEvent.PromptChanged(it)) },
                label = { Text("Describe an automation, e.g. \"open Spotify and tap Search\"") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.onEvent(MacrosUiEvent.Compile) },
                enabled = uiState.nlPrompt.isNotBlank() && !uiState.compiling,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.compiling) "Compiling…" else "Compile with AI")
            }
            if (uiState.running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (uiState.macros.isEmpty()) {
                EmptyState(
                    title = "No macros yet",
                    description = "Describe an automation above — you review every step before it can run.",
                )
                return@Column
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.macros, key = { it.id }) { macro ->
                    Card {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(macro.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${stepCount(macro.stepsJson)} steps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = macro.enabled,
                                onCheckedChange = { viewModel.onEvent(MacrosUiEvent.ToggleEnabled(macro)) },
                            )
                            IconButton(
                                onClick = { viewModel.onEvent(MacrosUiEvent.Run(macro)) },
                                enabled = macro.enabled && !uiState.running,
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Run")
                            }
                            IconButton(onClick = { viewModel.onEvent(MacrosUiEvent.Delete(macro.id)) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.preview?.let { steps ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(MacrosUiEvent.DiscardPreview) },
            title = { Text("Dry run — confirm the steps") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    steps.forEachIndexed { index, step ->
                        Text(
                            "${index + 1}. ${step.action} " +
                                listOfNotNull(step.target, step.text, step.delayMs?.let { "${it}ms" })
                                    .joinToString(" "),
                        )
                    }
                    Spacer(modifier = Modifier.padding(2.dp))
                    Text(
                        "Nothing runs until you press Run on the saved macro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(MacrosUiEvent.SavePreview) }) { Text("Save macro") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(MacrosUiEvent.DiscardPreview) }) { Text("Discard") }
            },
        )
    }
}

private fun stepCount(stepsJson: String): Int = stepsJson.count { it == '{' }
